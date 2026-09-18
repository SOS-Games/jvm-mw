/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Load placeable NAME+MODL records and one interior CELL's refs from Morrowind.esm. */
public final class EsmFile {
    public static final int CELL_INTERIOR = 0x01;

    public final Map<String, EsmObject> objects = new LinkedHashMap<>();
    public final Set<String> actorIds = new HashSet<>();
    public final List<String> interiorNames = new ArrayList<>();

    public static boolean isHiddenMarker(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        return s.equals("prisonmarker") || s.equals("divinemarker") || s.equals("templemarker")
            || s.equals("northmarker");
    }

    public static boolean isPlaceable(String rec) {
        return switch (rec) {
            case "STAT", "DOOR", "CONT", "MISC", "BOOK", "LIGH", "ACTI", "ALCH", "INGR", "WEAP", "APPA", "ARMO", "CLOT",
                 "LOCK", "PROB", "REPA" -> true;
            default -> false;
        };
    }

    public static EsmFile load(EsmReader esm) {
        EsmFile file = new EsmFile();
        while (esm.hasMoreRecs()) {
            String rec = esm.getRecName();
            esm.getRecHeader();
            if (isPlaceable(rec)) {
                file.readObject(esm, rec);
            } else {
                esm.skipRecord();
            }
        }
        return file;
    }

    public static LoadedCell loadInterior(EsmReader esm, String... wanted) {
        EsmFile file = new EsmFile();
        LoadedCell[] hits = new LoadedCell[wanted.length];
        while (esm.hasMoreRecs()) {
            String rec = esm.getRecName();
            esm.getRecHeader();
            if (isPlaceable(rec)) {
                file.readObject(esm, rec);
            } else if ("NPC_".equals(rec) || "CREA".equals(rec)) {
                file.readActor(esm);
            } else if ("CELL".equals(rec)) {
                String[] stillWanted = hits[0] != null ? new String[0] : wanted;
                LoadedCell cell = file.readCell(esm, stillWanted);
                if (cell != null) {
                    for (int i = 0; i < wanted.length; i++) {
                        if (wanted[i].equalsIgnoreCase(cell.name)) {
                            hits[i] = cell;
                            break;
                        }
                    }
                }
            } else {
                esm.skipRecord();
            }
        }
        LoadedCell found = null;
        for (LoadedCell hit : hits) {
            if (hit != null) {
                found = hit;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("No interior CELL matching " + java.util.Arrays.toString(wanted)
                + ". Interiors matching Census/Prison: " + file.hintNames());
        }
        found.objects = file.objects;
        found.actorIds = file.actorIds;
        return found;
    }

    private static boolean matchWanted(String[] wanted, String name) {
        for (String w : wanted) {
            if (w.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String hintNames() {
        List<String> hints = new ArrayList<>();
        for (String n : interiorNames) {
            String lower = n.toLowerCase(Locale.ROOT);
            if (lower.contains("census") || lower.contains("prison ship")) {
                hints.add(n);
            }
        }
        return hints.toString();
    }

    private void readObject(EsmReader esm, String rec) {
        EsmObject obj = new EsmObject();
        obj.rec = rec;
        while (esm.hasMoreSubs()) {
            String sub = esm.getSubName();
            switch (sub) {
                case "NAME" -> obj.id = esm.getHString();
                case "MODL" -> obj.model = esm.getHString();
                default -> esm.skipHSub();
            }
        }
        if (!obj.id.isEmpty()) {
            objects.put(obj.id.toLowerCase(Locale.ROOT), obj);
        }
    }

    private void readActor(EsmReader esm) {
        String id = "";
        while (esm.hasMoreSubs()) {
            if (esm.isNextSub("NAME")) {
                id = esm.getHString();
            } else {
                esm.getSubName();
                esm.skipHSub();
            }
        }
        if (!id.isEmpty()) {
            actorIds.add(id.toLowerCase(Locale.ROOT));
        }
    }

    private LoadedCell readCell(EsmReader esm, String[] wanted) {
        String name = "";
        int flags = 0;
        boolean headerDone = false;
        while (!headerDone && esm.hasMoreSubs()) {
            if (esm.isNextSub("NAME")) {
                name = esm.getHString();
            } else if (esm.isNextSub("DATA")) {
                esm.getSubHeader();
                flags = esm.getI32();
                esm.getI32();
                esm.getI32();
            } else if (esm.isNextSub("DELE")) {
                esm.skipHSub();
            } else {
                headerDone = true;
            }
        }
        boolean cellHeaderDone = false;
        while (!cellHeaderDone && esm.hasMoreSubs()) {
            if (esm.isNextSub("INTV") || esm.isNextSub("WHGT") || esm.isNextSub("AMBI") || esm.isNextSub("RGNN")
                || esm.isNextSub("NAM5") || esm.isNextSub("NAM0")) {
                esm.skipHSub();
            } else {
                cellHeaderDone = true;
            }
        }
        boolean interior = (flags & CELL_INTERIOR) != 0;
        if (interior && !name.isEmpty()) {
            interiorNames.add(name);
        }
        if (!interior || wanted == null || !matchWanted(wanted, name)) {
            esm.skipRecord();
            return null;
        }
        LoadedCell cell = new LoadedCell();
        cell.name = name;
        cell.interior = true;
        while (esm.hasMoreSubs()) {
            while (esm.isNextSub("MVRF")) {
                esm.skipHSub();
                if (esm.isNextSub("CNDT")) {
                    esm.skipHSub();
                }
                if (esm.peekNextSub("FRMR")) {
                    readRef(esm);
                }
            }
            if (!esm.peekNextSub("FRMR")) {
                if (esm.hasMoreSubs()) {
                    esm.getSubName();
                    esm.skipHSub();
                    continue;
                }
                break;
            }
            CellRef ref = readRef(esm);
            cell.refs.add(ref);
        }
        return cell;
    }

    private static CellRef readRef(EsmReader esm) {
        if (esm.isNextSub("NAM0")) {
            esm.skipHSub();
        }
        CellRef ref = new CellRef();
        ref.frmr = esm.getHNTInt("FRMR");
        if (esm.isNextSub("NAME")) {
            ref.refId = esm.getHString();
        }
        boolean done = false;
        while (!done && esm.hasMoreSubs()) {
            String sub = esm.getSubName();
            switch (sub) {
                case "XSCL" -> {
                    esm.getSubHeader();
                    ref.scale = Math.max(0.5f, Math.min(2f, esm.getF32()));
                }
                case "DATA" -> {
                    esm.getSubHeader();
                    ref.pos[0] = esm.getF32();
                    ref.pos[1] = esm.getF32();
                    ref.pos[2] = esm.getF32();
                    ref.rot[0] = esm.getF32();
                    ref.rot[1] = esm.getF32();
                    ref.rot[2] = esm.getF32();
                }
                case "DELE" -> {
                    esm.skipHSub();
                    ref.deleted = true;
                }
                case "NAM0" -> esm.skipHSub();
                case "UNAM", "ANAM", "BNAM", "XSOL", "CNAM", "INDX", "XCHG", "INTV", "NAM9", "DODT", "DNAM", "FLTV",
                     "KNAM", "TNAM" -> esm.skipHSub();
                default -> {
                    esm.cacheSubName();
                    done = true;
                }
            }
        }
        return ref;
    }

    public static final class LoadedCell {
        public String name = "";
        public boolean interior;
        public final List<CellRef> refs = new ArrayList<>();
        public Map<String, EsmObject> objects = Map.of();
        public Set<String> actorIds = Set.of();
    }
}

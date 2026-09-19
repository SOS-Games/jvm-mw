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
    public final Map<String, EsmNpc> npcs = new LinkedHashMap<>();
    public final Map<String, EsmCreature> creatures = new LinkedHashMap<>();
    public final Map<String, EsmRace> races = new LinkedHashMap<>();
    public final Map<String, EsmBodyPart> bodies = new LinkedHashMap<>();
    public final Set<String> actorIds = new HashSet<>();
    public final List<String> interiorNames = new ArrayList<>();
    public final Map<String, CellRef> inboundSpawns = new LinkedHashMap<>();

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
            } else if ("NPC_".equals(rec)) {
                file.readNpc(esm);
            } else if ("CREA".equals(rec)) {
                file.readCreature(esm);
            } else if ("RACE".equals(rec)) {
                file.readRace(esm);
            } else if ("BODY".equals(rec)) {
                file.readBody(esm);
            } else if ("CELL".equals(rec)) {
                LoadedCell cell = file.readCell(esm, wanted);
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
        found.npcs = file.npcs;
        found.creatures = file.creatures;
        found.races = file.races;
        found.bodies = file.bodies;
        found.actorIds = file.actorIds;
        file.applySpawn(found);
        return found;
    }

    /**
     * CELL-only pass: interior size, fog, inbound door spawn. Skips STAT/DOOR object records.
     * Use from the debug CLI; {@link #loadInterior} is still required to place a cell.
     */
    public static List<InteriorSummary> listInteriors(EsmReader esm) {
        EsmFile file = new EsmFile();
        List<InteriorSummary> list = new ArrayList<>();
        while (esm.hasMoreRecs()) {
            String rec = esm.getRecName();
            esm.getRecHeader();
            if (!"CELL".equals(rec)) {
                esm.skipRecord();
                continue;
            }
            InteriorSummary summary = file.readInteriorSummary(esm);
            if (summary != null) {
                list.add(summary);
            }
        }
        for (InteriorSummary summary : list) {
            file.applySpawn(summary);
        }
        return list;
    }

    private void applySpawn(LoadedCell cell) {
        CellRef inbound = inboundSpawns.get(cell.name.toLowerCase(Locale.ROOT));
        if (inbound == null) {
            return;
        }
        System.arraycopy(inbound.destPos, 0, cell.spawnPos, 0, 3);
        System.arraycopy(inbound.destRot, 0, cell.spawnRot, 0, 3);
        cell.hasSpawn = true;
    }

    private void applySpawn(InteriorSummary cell) {
        CellRef inbound = inboundSpawns.get(cell.name.toLowerCase(Locale.ROOT));
        if (inbound == null) {
            return;
        }
        System.arraycopy(inbound.destPos, 0, cell.spawnPos, 0, 3);
        System.arraycopy(inbound.destRot, 0, cell.spawnRot, 0, 3);
        cell.hasSpawn = true;
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
                case "LHDT" -> {
                    esm.getSubHeader();
                    if ("LIGH".equals(rec)) {
                        esm.getF32();
                        esm.getI32();
                        esm.getI32();
                        obj.lightRadius = esm.getI32();
                        obj.lightColor = esm.getI32();
                        obj.lightFlags = esm.getI32();
                        obj.hasLight = true;
                    }
                    esm.skipRestOfSub();
                }
                case "CTDT" -> {
                    esm.getSubHeader();
                    if ("CLOT".equals(rec)) {
                        obj.clothType = esm.getI32();
                        esm.getF32();
                        obj.value = esm.getU16();
                    }
                    esm.skipRestOfSub();
                }
                case "AODT" -> {
                    esm.getSubHeader();
                    if ("ARMO".equals(rec)) {
                        obj.armorType = esm.getI32();
                        esm.getF32();
                        obj.value = esm.getI32();
                    }
                    esm.skipRestOfSub();
                }
                case "INDX" -> {
                    EsmPartRef part = new EsmPartRef();
                    esm.getSubHeader();
                    part.part = esm.getU8();
                    esm.skipRestOfSub();
                    if (esm.isNextSub("BNAM")) {
                        part.male = esm.getHString();
                    }
                    if (esm.isNextSub("CNAM")) {
                        part.female = esm.getHString();
                    }
                    obj.parts.add(part);
                }
                default -> esm.skipHSub();
            }
        }
        if (!obj.id.isEmpty()) {
            objects.put(obj.id.toLowerCase(Locale.ROOT), obj);
        }
    }

    public static void colourFromRgb(int clr, float[] rgb) {
        rgb[0] = (clr & 0xFF) / 255f;
        rgb[1] = ((clr >> 8) & 0xFF) / 255f;
        rgb[2] = ((clr >> 16) & 0xFF) / 255f;
    }

    private void readCreature(EsmReader esm) {
        EsmCreature crea = new EsmCreature();
        while (esm.hasMoreSubs()) {
            String sub = esm.getSubName();
            switch (sub) {
                case "NAME" -> crea.id = esm.getHString();
                case "FNAM" -> crea.name = esm.getHString();
                case "MODL" -> crea.model = esm.getHString();
                case "FLAG" -> {
                    esm.getSubHeader();
                    crea.flags = esm.getI32() & 0xFF;
                    esm.skipRestOfSub();
                }
                case "XSCL" -> {
                    esm.getSubHeader();
                    crea.scale = esm.getF32();
                    esm.skipRestOfSub();
                }
                default -> esm.skipHSub();
            }
        }
        if (!crea.id.isEmpty()) {
            String key = crea.id.toLowerCase(Locale.ROOT);
            creatures.put(key, crea);
            actorIds.add(key);
        }
    }

    private void readNpc(EsmReader esm) {
        EsmNpc npc = new EsmNpc();
        while (esm.hasMoreSubs()) {
            String sub = esm.getSubName();
            switch (sub) {
                case "NAME" -> npc.id = esm.getHString();
                case "FNAM" -> npc.name = esm.getHString();
                case "MODL" -> npc.model = esm.getHString();
                case "RNAM" -> npc.race = esm.getHString();
                case "BNAM" -> npc.head = esm.getHString();
                case "KNAM" -> npc.hair = esm.getHString();
                case "FLAG" -> {
                    esm.getSubHeader();
                    npc.flags = esm.getI32() & 0xFF;
                    esm.skipRestOfSub();
                }
                case "NPCO" -> {
                    esm.getSubHeader();
                    esm.getI32();
                    int n = Math.min(esm.leftSub(), 32);
                    String item = n > 0 ? esm.takeString(n) : "";
                    esm.skipRestOfSub();
                    if (!item.isEmpty()) {
                        npc.inventory.add(item);
                    }
                }
                default -> esm.skipHSub();
            }
        }
        if (!npc.id.isEmpty()) {
            String key = npc.id.toLowerCase(Locale.ROOT);
            npcs.put(key, npc);
            actorIds.add(key);
        }
    }

    private void readRace(EsmReader esm) {
        EsmRace race = new EsmRace();
        while (esm.hasMoreSubs()) {
            String sub = esm.getSubName();
            switch (sub) {
                case "NAME" -> race.id = esm.getHString();
                case "RADT" -> {
                    esm.getSubHeader();
                    esm.skip(7 * 8 + 16 * 4);
                    race.maleHeight = esm.getF32();
                    race.femaleHeight = esm.getF32();
                    race.maleWeight = esm.getF32();
                    race.femaleWeight = esm.getF32();
                    race.flags = esm.getI32();
                    esm.skipRestOfSub();
                }
                default -> esm.skipHSub();
            }
        }
        if (!race.id.isEmpty()) {
            races.put(race.id.toLowerCase(Locale.ROOT), race);
        }
    }

    private void readBody(EsmReader esm) {
        EsmBodyPart body = new EsmBodyPart();
        while (esm.hasMoreSubs()) {
            String sub = esm.getSubName();
            switch (sub) {
                case "NAME" -> body.id = esm.getHString();
                case "MODL" -> body.model = esm.getHString();
                case "FNAM" -> body.race = esm.getHString();
                case "BYDT" -> {
                    esm.getSubHeader();
                    body.part = esm.getU8();
                    body.vampire = esm.getU8();
                    body.flags = esm.getU8();
                    body.type = esm.getU8();
                    esm.skipRestOfSub();
                }
                default -> esm.skipHSub();
            }
        }
        if (!body.id.isEmpty()) {
            bodies.put(body.id.toLowerCase(Locale.ROOT), body);
        }
    }

    private LoadedCell readCell(EsmReader esm, String[] wanted) {
        CellHead head = readCellHead(esm);
        boolean interior = (head.flags & CELL_INTERIOR) != 0;
        if (interior && !head.name.isEmpty()) {
            interiorNames.add(head.name);
        }
        if (!interior || wanted == null || !matchWanted(wanted, head.name)) {
            harvestRefs(esm);
            return null;
        }
        LoadedCell cell = new LoadedCell();
        cell.name = head.name;
        cell.interior = true;
        colourFromRgb(head.ambiAmbient, cell.ambient);
        colourFromRgb(head.ambiSun, cell.sunlight);
        colourFromRgb(head.ambiFog, cell.fogColor);
        cell.fogDensity = head.fogDensity;
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
            noteInbound(ref);
        }
        return cell;
    }

    private InteriorSummary readInteriorSummary(EsmReader esm) {
        CellHead head = readCellHead(esm);
        boolean interior = (head.flags & CELL_INTERIOR) != 0;
        if (!interior || head.name.isEmpty()) {
            harvestRefs(esm);
            return null;
        }
        InteriorSummary summary = new InteriorSummary();
        summary.name = head.name;
        summary.fogDensity = head.fogDensity;
        colourFromRgb(head.ambiFog, summary.fogColor);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        while (esm.hasMoreSubs()) {
            while (esm.isNextSub("MVRF")) {
                esm.skipHSub();
                if (esm.isNextSub("CNDT")) {
                    esm.skipHSub();
                }
                if (esm.peekNextSub("FRMR")) {
                    noteInbound(readRef(esm));
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
            noteInbound(ref);
            summary.refs++;
            minX = Math.min(minX, ref.pos[0]);
            minY = Math.min(minY, ref.pos[1]);
            minZ = Math.min(minZ, ref.pos[2]);
            maxX = Math.max(maxX, ref.pos[0]);
            maxY = Math.max(maxY, ref.pos[1]);
            maxZ = Math.max(maxZ, ref.pos[2]);
        }
        if (summary.refs > 0 && minX <= maxX) {
            summary.dx = maxX - minX;
            summary.dy = maxY - minY;
            summary.dz = maxZ - minZ;
            summary.span = Math.max(summary.dx, Math.max(summary.dy, summary.dz));
        }
        return summary;
    }

    private CellHead readCellHead(EsmReader esm) {
        CellHead head = new CellHead();
        boolean headerDone = false;
        while (!headerDone && esm.hasMoreSubs()) {
            if (esm.isNextSub("NAME")) {
                head.name = esm.getHString();
            } else if (esm.isNextSub("DATA")) {
                esm.getSubHeader();
                head.flags = esm.getI32();
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
            if (esm.isNextSub("AMBI")) {
                esm.getSubHeader();
                head.ambiAmbient = esm.getI32();
                head.ambiSun = esm.getI32();
                head.ambiFog = esm.getI32();
                head.fogDensity = esm.getF32();
                esm.skipRestOfSub();
            } else if (esm.isNextSub("INTV") || esm.isNextSub("WHGT") || esm.isNextSub("RGNN")
                || esm.isNextSub("NAM5") || esm.isNextSub("NAM0")) {
                esm.skipHSub();
            } else {
                cellHeaderDone = true;
            }
        }
        return head;
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
                case "DODT" -> {
                    esm.getSubHeader();
                    ref.destPos[0] = esm.getF32();
                    ref.destPos[1] = esm.getF32();
                    ref.destPos[2] = esm.getF32();
                    ref.destRot[0] = esm.getF32();
                    ref.destRot[1] = esm.getF32();
                    ref.destRot[2] = esm.getF32();
                    ref.teleport = true;
                    esm.skipRestOfSub();
                }
                case "DNAM" -> ref.destCell = esm.getHString();
                case "DELE" -> {
                    esm.skipHSub();
                    ref.deleted = true;
                }
                case "NAM0" -> esm.skipHSub();
                case "UNAM", "ANAM", "BNAM", "XSOL", "CNAM", "INDX", "XCHG", "INTV", "NAM9", "FLTV",
                     "KNAM", "TNAM" -> esm.skipHSub();
                default -> {
                    esm.cacheSubName();
                    done = true;
                }
            }
        }
        return ref;
    }

    private void harvestRefs(EsmReader esm) {
        while (esm.hasMoreSubs()) {
            while (esm.isNextSub("MVRF")) {
                esm.skipHSub();
                if (esm.isNextSub("CNDT")) {
                    esm.skipHSub();
                }
                if (esm.peekNextSub("FRMR")) {
                    noteInbound(readRef(esm));
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
            noteInbound(readRef(esm));
        }
    }

    private void noteInbound(CellRef ref) {
        if (!ref.teleport || ref.destCell.isEmpty()) {
            return;
        }
        inboundSpawns.putIfAbsent(ref.destCell.toLowerCase(Locale.ROOT), ref);
    }

    public static final class LoadedCell {
        public String name = "";
        public boolean interior;
        public boolean hasSpawn;
        public final List<CellRef> refs = new ArrayList<>();
        public Map<String, EsmObject> objects = Map.of();
        public Map<String, EsmNpc> npcs = Map.of();
        public Map<String, EsmCreature> creatures = Map.of();
        public Map<String, EsmRace> races = Map.of();
        public Map<String, EsmBodyPart> bodies = Map.of();
        public Set<String> actorIds = Set.of();
        public final float[] ambient = {0.35f, 0.35f, 0.35f};
        public final float[] sunlight = {1f, 1f, 1f};
        public final float[] fogColor = {0.08f, 0.09f, 0.12f};
        public float fogDensity;
        public final float[] spawnPos = new float[3];
        public final float[] spawnRot = new float[3];
    }

    /** CELL scan row for the debug CLI. Not a placed scene. */
    public static final class InteriorSummary {
        public String name = "";
        public int refs;
        public float fogDensity;
        public final float[] fogColor = new float[3];
        public float dx;
        public float dy;
        public float dz;
        public float span;
        public boolean hasSpawn;
        public final float[] spawnPos = new float[3];
        public final float[] spawnRot = new float[3];
    }

    private static final class CellHead {
        String name = "";
        int flags;
        int ambiAmbient;
        int ambiSun;
        int ambiFog;
        float fogDensity;
    }
}

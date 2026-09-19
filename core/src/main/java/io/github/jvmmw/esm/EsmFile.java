/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Load placeable NAME+MODL records and CELL refs from Morrowind.esm. */
public final class EsmFile {
    public static final int CELL_INTERIOR = 0x01;
    /** TES3 {@code Constants::CellGridRadius}. Active grid side is {@code 2 * r + 1}. */
    public static final int CELL_GRID_RADIUS = 1;
    public static final String CENSUS_CELL = "Seyda Neen, Census and Excise Office";
    public static final String CENSUS_EXIT = "chargen door exit";

    public final Map<String, EsmObject> objects = new LinkedHashMap<>();
    public final Map<String, EsmNpc> npcs = new LinkedHashMap<>();
    public final Map<String, EsmCreature> creatures = new LinkedHashMap<>();
    public final Map<String, EsmRace> races = new LinkedHashMap<>();
    public final Map<String, EsmBodyPart> bodies = new LinkedHashMap<>();
    public final Set<String> actorIds = new HashSet<>();
    public final List<String> interiorNames = new ArrayList<>();
    public final Map<String, CellRef> inboundSpawns = new LinkedHashMap<>();
    /** {@code LTEX} INTV index → DATA path. First index wins (OpenMW {@code emplace}). */
    public final Map<Integer, String> landTextures = new HashMap<>();
    private CellRef censusExit;

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
                LoadedCell cell = file.readCell(esm, wanted, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
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

    public static LoadedCell loadExterior(EsmReader esm, int gridX, int gridY) {
        return loadExterior(esm, gridX, gridY, CELL_GRID_RADIUS);
    }

    public static LoadedCell loadExterior(EsmReader esm, int gridX, int gridY, int radius) {
        EsmFile file = new EsmFile();
        Map<Long, LoadedCell> cells = new HashMap<>();
        Map<Long, LandRecord> lands = new HashMap<>();
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
            } else if ("LTEX".equals(rec)) {
                file.readLandTexture(esm);
            } else if ("CELL".equals(rec)) {
                LoadedCell cell = file.readCell(esm, null, gridX, gridY, radius);
                if (cell != null) {
                    cells.put(gridKey(cell.gridX, cell.gridY), cell);
                }
            } else if ("LAND".equals(rec)) {
                LandRecord parsed = file.readLand(esm, gridX, gridY, radius);
                if (parsed != null) {
                    lands.put(gridKey(parsed.gridX, parsed.gridY), parsed);
                }
            } else {
                esm.skipRecord();
            }
        }
        LoadedCell center = cells.get(gridKey(gridX, gridY));
        if (center == null) {
            throw new IllegalStateException("No exterior CELL at (" + gridX + ", " + gridY + ")");
        }
        center.objects = file.objects;
        center.npcs = file.npcs;
        center.creatures = file.creatures;
        center.races = file.races;
        center.bodies = file.bodies;
        center.actorIds = file.actorIds;
        center.landTextures = file.landTextures;
        for (int x = gridX - radius; x <= gridX + radius; x++) {
            for (int y = gridY - radius; y <= gridY + radius; y++) {
                long key = gridKey(x, y);
                LandRecord land = lands.get(key);
                if (land == null) {
                    land = LandRecord.flat(x, y);
                }
                LoadedCell part = cells.get(key);
                GridTile tile = new GridTile();
                tile.gridX = x;
                tile.gridY = y;
                tile.name = part != null ? part.name : "";
                tile.refs = part != null ? part.refs.size() : 0;
                tile.land = land;
                center.tiles.add(tile);
                center.lands.add(land);
                if (x == gridX && y == gridY) {
                    center.land = land;
                } else if (part != null) {
                    center.refs.addAll(part.refs);
                }
            }
        }
        file.applyCensusExitSpawn(center);
        return center;
    }

    public static boolean inCellGrid(int x, int y, int cx, int cy, int radius) {
        return Math.abs(x - cx) <= radius && Math.abs(y - cy) <= radius;
    }

    private static long gridKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
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

    private LoadedCell readCell(EsmReader esm, String[] wanted, int gridX, int gridY, int radius) {
        CellHead head = readCellHead(esm);
        boolean interior = (head.flags & CELL_INTERIOR) != 0;
        if (interior && !head.name.isEmpty()) {
            interiorNames.add(head.name);
        }
        boolean takeInterior = interior && wanted != null && matchWanted(wanted, head.name);
        boolean takeExterior = !interior && wanted == null && inCellGrid(head.gridX, head.gridY, gridX, gridY, radius);
        if (!takeInterior && !takeExterior) {
            harvestRefs(esm, head.name, interior);
            return null;
        }
        LoadedCell cell = new LoadedCell();
        cell.name = head.name;
        cell.interior = interior;
        cell.gridX = head.gridX;
        cell.gridY = head.gridY;
        if (interior) {
            colourFromRgb(head.ambiAmbient, cell.ambient);
            colourFromRgb(head.ambiSun, cell.sunlight);
            colourFromRgb(head.ambiFog, cell.fogColor);
            cell.fogDensity = head.fogDensity;
        }
        while (esm.hasMoreSubs()) {
            while (esm.isNextSub("MVRF")) {
                esm.skipHSub();
                if (esm.isNextSub("CNDT")) {
                    esm.skipHSub();
                }
                if (esm.peekNextSub("FRMR")) {
                    noteRef(head.name, interior, readRef(esm));
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
            noteRef(head.name, interior, ref);
        }
        return cell;
    }

    private InteriorSummary readInteriorSummary(EsmReader esm) {
        CellHead head = readCellHead(esm);
        boolean interior = (head.flags & CELL_INTERIOR) != 0;
        if (!interior || head.name.isEmpty()) {
            harvestRefs(esm, head.name, interior);
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
                    noteRef(head.name, true, readRef(esm));
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
            noteRef(head.name, true, ref);
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
                head.gridX = esm.getI32();
                head.gridY = esm.getI32();
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

    private void harvestRefs(EsmReader esm, String cellName, boolean interior) {
        while (esm.hasMoreSubs()) {
            while (esm.isNextSub("MVRF")) {
                esm.skipHSub();
                if (esm.isNextSub("CNDT")) {
                    esm.skipHSub();
                }
                if (esm.peekNextSub("FRMR")) {
                    noteRef(cellName, interior, readRef(esm));
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
            noteRef(cellName, interior, readRef(esm));
        }
    }

    private void noteRef(String cellName, boolean interior, CellRef ref) {
        noteInbound(ref);
        if (interior && CENSUS_CELL.equalsIgnoreCase(cellName) && ref.teleport && ref.destCell.isEmpty()) {
            if (censusExit == null || CENSUS_EXIT.equalsIgnoreCase(ref.refId)) {
                censusExit = ref;
            }
        }
    }

    private void noteInbound(CellRef ref) {
        if (!ref.teleport || ref.destCell.isEmpty()) {
            return;
        }
        inboundSpawns.putIfAbsent(ref.destCell.toLowerCase(Locale.ROOT), ref);
    }

    private void applyCensusExitSpawn(LoadedCell cell) {
        if (censusExit == null) {
            return;
        }
        if (LandRecord.cellGrid(censusExit.destPos[0]) != cell.gridX
            || LandRecord.cellGrid(censusExit.destPos[1]) != cell.gridY) {
            return;
        }
        System.arraycopy(censusExit.destPos, 0, cell.spawnPos, 0, 3);
        System.arraycopy(censusExit.destRot, 0, cell.spawnRot, 0, 3);
        cell.hasSpawn = true;
    }

    private LandRecord readLand(EsmReader esm, int gridX, int gridY, int radius) {
        int x = 0;
        int y = 0;
        boolean hasLocation = false;
        while (esm.hasMoreSubs()) {
            if (esm.isNextSub("INTV")) {
                esm.getSubHeader();
                x = esm.getI32();
                y = esm.getI32();
                hasLocation = true;
            } else if (esm.isNextSub("DATA") || esm.isNextSub("DELE")) {
                esm.skipHSub();
            } else {
                break;
            }
        }
        if (!hasLocation || !inCellGrid(x, y, gridX, gridY, radius)) {
            while (esm.hasMoreSubs()) {
                esm.getSubName();
                esm.skipHSub();
            }
            return null;
        }
        LandRecord land = LandRecord.flat(x, y);
        while (esm.hasMoreSubs()) {
            if (esm.isNextSub("VHGT")) {
                decodeVhgt(esm, land);
            } else if (esm.isNextSub("VTEX")) {
                decodeVtex(esm, land);
            } else {
                esm.getSubName();
                esm.skipHSub();
            }
        }
        return land;
    }

    private static void decodeVhgt(EsmReader esm, LandRecord land) {
        esm.getSubHeader();
        float rowOffset = esm.getF32();
        land.minHeight = Float.POSITIVE_INFINITY;
        land.maxHeight = Float.NEGATIVE_INFINITY;
        for (int row = 0; row < LandRecord.SIZE; row++) {
            rowOffset += esm.getI8();
            float h = rowOffset * LandRecord.HEIGHT_SCALE;
            land.heights[row * LandRecord.SIZE] = h;
            land.minHeight = Math.min(land.minHeight, h);
            land.maxHeight = Math.max(land.maxHeight, h);
            float colOffset = rowOffset;
            for (int col = 1; col < LandRecord.SIZE; col++) {
                colOffset += esm.getI8();
                h = colOffset * LandRecord.HEIGHT_SCALE;
                land.heights[col + row * LandRecord.SIZE] = h;
                land.minHeight = Math.min(land.minHeight, h);
                land.maxHeight = Math.max(land.maxHeight, h);
            }
        }
        esm.skipRestOfSub();
    }

    private static void decodeVtex(EsmReader esm, LandRecord land) {
        esm.getSubHeader();
        int[] raw = new int[LandRecord.NUM_TEXTURES];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = esm.getU16();
        }
        esm.skipRestOfSub();
        int readPos = 0;
        for (int y1 = 0; y1 < 4; y1++) {
            for (int x1 = 0; x1 < 4; x1++) {
                for (int y2 = 0; y2 < 4; y2++) {
                    for (int x2 = 0; x2 < 4; x2++) {
                        land.textures[(y1 * 4 + y2) * 16 + (x1 * 4 + x2)] = raw[readPos++];
                    }
                }
            }
        }
    }

    private void readLandTexture(EsmReader esm) {
        LandTexture lt = new LandTexture();
        while (esm.hasMoreSubs()) {
            if (esm.isNextSub("NAME")) {
                lt.id = esm.getHString();
            } else if (esm.isNextSub("INTV")) {
                esm.getSubHeader();
                lt.index = esm.getI32();
                esm.skipRestOfSub();
            } else if (esm.isNextSub("DATA")) {
                lt.texture = esm.getHString();
            } else {
                esm.getSubName();
                esm.skipHSub();
            }
        }
        landTextures.putIfAbsent(lt.index, lt.texture);
    }

    public static final class LoadedCell {
        public String name = "";
        public boolean interior;
        public int gridX;
        public int gridY;
        public boolean hasSpawn;
        public LandRecord land;
        public final List<LandRecord> lands = new ArrayList<>();
        public final List<GridTile> tiles = new ArrayList<>();
        public final List<CellRef> refs = new ArrayList<>();
        public Map<String, EsmObject> objects = Map.of();
        public Map<String, EsmNpc> npcs = Map.of();
        public Map<String, EsmCreature> creatures = Map.of();
        public Map<String, EsmRace> races = Map.of();
        public Map<String, EsmBodyPart> bodies = Map.of();
        public Set<String> actorIds = Set.of();
        public Map<Integer, String> landTextures = Map.of();
        public final float[] ambient = {0.35f, 0.35f, 0.35f};
        public final float[] sunlight = {1f, 1f, 1f};
        public final float[] fogColor = {0.08f, 0.09f, 0.12f};
        public float fogDensity;
        public final float[] spawnPos = new float[3];
        public final float[] spawnRot = new float[3];
    }

    public static final class GridTile {
        public int gridX;
        public int gridY;
        public String name = "";
        public int refs;
        public LandRecord land;
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
        int gridX;
        int gridY;
        int ambiAmbient;
        int ambiSun;
        int ambiFog;
        float fogDensity;
    }
}

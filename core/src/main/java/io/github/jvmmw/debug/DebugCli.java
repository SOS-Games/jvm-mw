package io.github.jvmmw.debug;

import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmCreature;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmLevc;
import io.github.jvmmw.esm.EsmNpc;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.esm.EsmPathgrid;
import io.github.jvmmw.esm.EsmReader;
import io.github.jvmmw.esm.LevelledCreatures;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.render.CellLighting;
import io.github.jvmmw.render.LandMesh;
import io.github.jvmmw.render.NpcMannequin;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Text dumps of the same ESM/NIF parsers the viewer uses, with no window.
 * From the repo root: gradlew.bat :core:debugCli --args="help"
 *
 * nif — a door or wall that looks offset. cell / spawn — fog and where you
 * arrive. exterior — the 21-cell walk grid. npc / crea / levc / kf — how a
 * person, creature, or wilderness spawn list is put together. pgrd — that
 * cell’s walk-graph nodes. navdb — OpenMW navmesh.db tiles vs cell edges.
 */
public final class DebugCli {
    private DebugCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "-h".equals(args[0])) {
            System.out.print(help());
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "nif" -> nif(require(args, 1, "nif <vfs-or-path>"));
            case "cell" -> cell(require(args, 1, "cell <interior name>"));
            case "interiors" -> interiors(args.length > 1 ? args[1] : "");
            case "spawn" -> spawn(require(args, 1, "spawn <interior name>"));
            case "npc" -> npc(require(args, 1, "npc <id>"));
            case "crea" -> crea(require(args, 1, "crea <id>"));
            case "levc" -> levc(require(args, 1, "levc <id>"));
            case "kf" -> kf(require(args, 1, "kf <vfs-or-path>"));
            case "exterior" -> exterior(require(args, 1, "exterior <gridX> <gridY>"));
            case "pgrd" -> pgrd(require(args, 1, "pgrd <interior name> | <gridX> <gridY>"));
            case "navdb" -> NavmeshDbDump.run(args.length > 1 ? require(args, 1, "navdb") : "");
            default -> {
                System.err.println("Unknown command: " + args[0]);
                System.out.print(help());
                System.exit(2);
            }
        }
    }

    public static String help() {
        return """
            JVM-MW debug CLI (no window). Cwd must be the repo root so local.properties resolves.

            gradlew.bat :core:debugCli --args="help"
            gradlew.bat :core:debugCli --args="nif meshes/d/door_cavern_doors00.nif"
            gradlew.bat :core:debugCli --args="cell Addamasartus"
            gradlew.bat :core:debugCli --args="interiors cave"
            gradlew.bat :core:debugCli --args="spawn Addamasartus"
            gradlew.bat :core:debugCli --args="npc sellus gravius"
            gradlew.bat :core:debugCli --args="crea nix-hound"
            gradlew.bat :core:debugCli --args="levc ex_bittercoast_lev+0"
            gradlew.bat :core:debugCli --args="kf meshes/xbase_anim.kf"
            gradlew.bat :core:debugCli --args="exterior -2 -9"
            gradlew.bat :core:debugCli --args="pgrd -2 -9"
            gradlew.bat :core:debugCli --args="navdb -2 -9"

            nif        Node tree + local transforms. VFS path extracts from BSA into testdata/.
            cell       One interior: fog, spawn, doors, NPCs. Kit STAT lines include world AABB.
                       Then seam meet/gap/islands: whether cave hull triangles actually touch.
                       Header includes pgrd=N e=M.
            interiors  All interiors: span / fog / spawn. Optional substring filter. CELL-only pass.
            spawn      Inbound DODT for an interior (the OpenMW arrival point).
            npc        One NPC_: race, head, hair, skeleton, equipped CLOT/ARMO parts, wander= and allowed= pathgrid dests.
            crea       One CREA: model, corrected x-path, flags, scale, wander= and allowed=.
            levc       One creature leveled list: flags, chance-none, level/id rows.
            kf         Text-key groups and bone tracks from a Morrowind .kf (BSA or extra data dirs).
            exterior   5x5 minus corners around a grid: 21 grid= lines, then center spawn/doors.
                       crea= hardcoded, levc=/levcNone= a dry roll at player level 1. Each grid= has pgrd=.
            pgrd       One cell's pathgrid nodes and edges. Interior name or exterior grid.
            navdb      OpenMW navmesh.db Recast tiles vs TES cell edges. Default Town (-2,-9) 5x5.
                       Writes build/navdb-*.png (green tris, yellow cell grid, red missing tiles,
                       magenta uncovered edge samples). verdict= says if the db already has the cracks.

            Viewer: HUD Dump or F3 copies camera/fog/perf to the clipboard and writes build/debug-snapshot.txt.
            F4 toggles the fps overlay. Wait for overlay n=60 before treating fps as settled. Headless CLI has no fps.
            E activates the closest door, container, or takeable item (192 units). Named interior dest loads that cell.
            Empty-DNAM dest loads a 5x5-minus-corners around that exterior grid. HUD Town loads exterior (-2, -9).
            """;
    }

    private static void nif(String path) throws Exception {
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            file = TestData.ensureNif(path.replace('\\', '/'));
        }
        NifFile nif = NifFile.parse(Files.readAllBytes(file), file.toString());
        System.out.println(file);
        System.out.print(NifDump.dump(nif));
    }

    private static void kf(String path) throws Exception {
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            file = TestData.ensureNif(path.replace('\\', '/'));
        }
        NifFile nif = NifFile.parse(Files.readAllBytes(file), file.toString());
        System.out.print(io.github.jvmmw.nif.KfFile.load(nif, file.toString()).describe());
    }

    private static void exterior(String grid) throws Exception {
        String[] parts = grid.trim().split("[,\\s]+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Usage: exterior <gridX> <gridY>");
        }
        int gx = Integer.parseInt(parts[0]);
        int gy = Integer.parseInt(parts[1]);
        EsmFile.LoadedCell cell = EsmFile.loadExterior(EsmReader.open(TestData.esmPath()), gx, gy);
        for (EsmFile.GridTile tile : cell.tiles) {
            System.out.println("grid= " + tile.gridX + " " + tile.gridY
                + " name=" + tile.name
                + " refs=" + tile.refs
                + " land=" + (int) tile.land.minHeight + ".." + (int) tile.land.maxHeight
                + " vtex=" + tile.land.uniqueVtex()
                + " layers=" + LandMesh.layerCount(tile.land, cell.lands, cell.landTextures)
                + " " + pgrdCounts(tile.pathgrid));
        }
        System.out.println("cell=" + cell.name
            + " grid=(" + cell.gridX + "," + cell.gridY + ")"
            + " interior=" + cell.interior
            + " refs=" + cell.refs.size()
            + " land=" + (int) cell.land.minHeight + ".." + (int) cell.land.maxHeight
            + " ltex=" + cell.landTextures.size()
            + " water=-1");
        if (cell.hasSpawn) {
            System.out.println("spawn census-exit tes=" + xyz(cell.spawnPos)
                + " heading=" + cell.spawnRot[2]);
        } else {
            System.out.println("spawn census-exit=<none>");
        }
        int doors = 0;
        int stat = 0;
        int npcs = 0;
        int crea = 0;
        int levc = 0;
        int levcNone = 0;
        Random rng = new Random();
        for (CellRef ref : cell.refs) {
            if (ref.deleted) {
                continue;
            }
            String key = ref.refId.toLowerCase(Locale.ROOT);
            if (cell.npcs.containsKey(key)) {
                npcs++;
                continue;
            }
            if (cell.creatures.containsKey(key)) {
                crea++;
                continue;
            }
            EsmLevc list = cell.levc.get(key);
            if (list != null) {
                String id = LevelledCreatures.pick(list, EsmLevc.PLAYER_LEVEL, rng, cell.levc, cell.creatures);
                if (id.isEmpty()) {
                    levcNone++;
                } else {
                    levc++;
                }
                continue;
            }
            EsmObject obj = cell.objects.get(key);
            if (obj == null) {
                continue;
            }
            if ("DOOR".equals(obj.rec)) {
                doors++;
                System.out.println("door " + ref.refId
                    + " tes=" + xyz(ref.pos)
                    + (ref.teleport ? " dest=" + ref.destCell + " dodt=" + xyz(ref.destPos) : " swing"));
            }
            if ("STAT".equals(obj.rec)) {
                stat++;
            }
        }
        System.out.println("doors=" + doors + " stat=" + stat + " npcs=" + npcs + " crea=" + crea
            + " levc=" + levc + " levcNone=" + levcNone);
    }

    private static void cell(String name) throws Exception {
        EsmFile.LoadedCell cell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()), name);
        float fogStart = fogStart(cell.fogDensity);
        System.out.println("cell=" + cell.name
            + " refs=" + cell.refs.size()
            + " fogDensity=" + cell.fogDensity
            + " fogStart=" + (int) fogStart
            + " fogEnd=" + (int) CellLighting.VIEW_DISTANCE
            + " " + pgrdCounts(cell.pathgrid));
        if (cell.hasSpawn) {
            System.out.println("spawn inbound tes=" + xyz(cell.spawnPos)
                + " heading=" + cell.spawnRot[2]);
        } else {
            System.out.println("spawn inbound=<none>");
        }
        System.out.println("fogColor=" + xyz(cell.fogColor));
        int doors = 0;
        int kit = 0;
        int npcs = 0;
        int crea = 0;
        int cont = 0;
        int take = 0;
        int books = 0;
        for (CellRef ref : cell.refs) {
            if (ref.deleted) {
                continue;
            }
            String key = ref.refId.toLowerCase(Locale.ROOT);
            EsmNpc npc = cell.npcs.get(key);
            if (npc != null) {
                npcs++;
                System.out.println("npc " + npc.id
                    + " tes=" + xyz(ref.pos)
                    + " yaw=" + ref.rot[2]
                    + " female=" + npc.female()
                    + " race=" + npc.race
                    + " head=" + npc.head
                    + " hair=" + npc.hair);
                continue;
            }
            EsmCreature creature = cell.creatures.get(key);
            if (creature != null) {
                crea++;
                System.out.println("crea " + creature.id
                    + " tes=" + xyz(ref.pos)
                    + " yaw=" + ref.rot[2]
                    + " scl=" + (ref.scale * creature.scale)
                    + " flags=0x" + Integer.toHexString(creature.flags)
                    + " modl=" + creature.model);
                continue;
            }
            EsmLevc list = cell.levc.get(key);
            if (list != null) {
                continue;
            }
            EsmObject obj = cell.objects.get(key);
            if (obj == null) {
                continue;
            }
            if ("CONT".equals(obj.rec)) {
                cont++;
                System.out.println("cont " + ref.refId
                    + " tes=" + xyz(ref.pos)
                    + " modl=" + obj.model
                    + " kf=" + containerKf(obj.model));
            }
            if (EsmObject.isTakeable(obj)) {
                take++;
                System.out.println("take " + ref.refId
                    + " tes=" + xyz(ref.pos)
                    + " modl=" + obj.model
                    + " rec=" + obj.rec);
            }
            if (EsmObject.isBook(obj)) {
                books++;
                System.out.println("book " + ref.refId
                    + " tes=" + xyz(ref.pos)
                    + " modl=" + obj.model);
            }
            if ("DOOR".equals(obj.rec)) {
                doors++;
                System.out.println("door " + ref.refId
                    + " tes=" + xyz(ref.pos)
                    + " rot=" + xyz(ref.rot)
                    + " scl=" + ref.scale
                    + " modl=" + obj.model
                    + (ref.teleport ? " dest=" + ref.destCell + " dodt=" + xyz(ref.destPos) : " swing"));
            }
            if (KitSeams.isKitModel(obj.model)) {
                kit++;
                if (kit <= 40) {
                    System.out.println("kit " + obj.rec + " " + ref.refId
                        + " tes=" + xyz(ref.pos)
                        + " rot=" + xyz(ref.rot)
                        + " scl=" + ref.scale
                        + " " + obj.model
                        + " " + KitSeams.kitLine(ref, obj.model));
                }
            }
        }
        System.out.println("doors=" + doors + " kit=" + kit + " npcs=" + npcs + " crea=" + crea
            + " cont=" + cont + " take=" + take + " book=" + books);
        KitSeams.dump(cell);
    }

    private static void npc(String id) throws Exception {
        EsmFile.LoadedCell cell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()), TestData.CENSUS_CELL);
        String key = id.toLowerCase(Locale.ROOT);
        EsmNpc npc = cell.npcs.get(key);
        if (npc == null) {
            for (EsmNpc candidate : cell.npcs.values()) {
                if (candidate.id.toLowerCase(Locale.ROOT).contains(key)
                    || candidate.name.toLowerCase(Locale.ROOT).contains(key)) {
                    npc = candidate;
                    break;
                }
            }
        }
        if (npc == null) {
            throw new IllegalStateException("No NPC_ matching " + id);
        }
        System.out.print(NpcMannequin.describe(npc, cell));
    }

    private static void crea(String id) throws Exception {
        EsmFile.LoadedCell cell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()), TestData.PUNSABANIT);
        String key = id.toLowerCase(Locale.ROOT);
        EsmCreature crea = cell.creatures.get(key);
        if (crea == null) {
            for (EsmCreature candidate : cell.creatures.values()) {
                if (candidate.id.toLowerCase(Locale.ROOT).contains(key)
                    || candidate.name.toLowerCase(Locale.ROOT).contains(key)) {
                    crea = candidate;
                    break;
                }
            }
        }
        if (crea == null) {
            throw new IllegalStateException("No CREA matching " + id);
        }
        System.out.print(NpcMannequin.describeCreature(crea, cell));
    }

    private static void levc(String id) throws Exception {
        EsmFile.LoadedCell cell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()), TestData.CENSUS_CELL);
        String key = id.toLowerCase(Locale.ROOT);
        EsmLevc list = cell.levc.get(key);
        if (list == null) {
            for (EsmLevc candidate : cell.levc.values()) {
                if (candidate.id.toLowerCase(Locale.ROOT).contains(key)) {
                    list = candidate;
                    break;
                }
            }
        }
        if (list == null) {
            throw new IllegalStateException("No LEVC matching " + id);
        }
        System.out.println("levc " + list.id
            + " flags=0x" + Integer.toHexString(list.flags)
            + " allLevels=" + list.allLevels()
            + " chanceNone=" + list.chanceNone
            + " n=" + list.entries.size());
        for (EsmLevc.Entry e : list.entries) {
            System.out.println("  level=" + e.level + " " + e.id);
        }
    }

    private static void interiors(String filter) throws Exception {
        String needle = filter.toLowerCase(Locale.ROOT);
        List<EsmFile.InteriorSummary> all = EsmFile.listInteriors(EsmReader.open(TestData.esmPath()));
        List<EsmFile.InteriorSummary> hits = new ArrayList<>();
        for (EsmFile.InteriorSummary s : all) {
            if (needle.isEmpty() || s.name.toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(s);
            }
        }
        hits.sort(Comparator.comparing((EsmFile.InteriorSummary s) -> s.span).reversed());
        System.out.println("interiors=" + hits.size() + (needle.isEmpty() ? "" : " filter=" + filter)
            + " fogStart=7168*(1-density)");
        int n = 0;
        for (EsmFile.InteriorSummary s : hits) {
            if (n++ == 40) {
                System.out.println("... truncated, " + (hits.size() - 40) + " more");
                break;
            }
            float fogStart = fogStart(s.fogDensity);
            System.out.println(String.format(Locale.ROOT,
                "span=%6.0f fogD=%.2f fogStart=%5.0f refs=%4d spawn=%s  %s",
                s.span, s.fogDensity, fogStart, s.refs,
                s.hasSpawn ? xyz(s.spawnPos) : "-", s.name));
        }
    }

    private static void spawn(String name) throws Exception {
        List<EsmFile.InteriorSummary> all = EsmFile.listInteriors(EsmReader.open(TestData.esmPath()));
        EsmFile.InteriorSummary hit = null;
        for (EsmFile.InteriorSummary s : all) {
            if (s.name.equalsIgnoreCase(name)) {
                hit = s;
                break;
            }
        }
        if (hit == null) {
            throw new IllegalStateException("No interior named " + name);
        }
        if (!hit.hasSpawn) {
            System.out.println("cell=" + hit.name + " spawn inbound=<none>");
            return;
        }
        System.out.println("cell=" + hit.name
            + " spawn inbound tes=" + xyz(hit.spawnPos)
            + " rot=" + xyz(hit.spawnRot));
    }

    private static void pgrd(String spec) throws Exception {
        String[] parts = spec.trim().split("[,\\s]+");
        EsmFile.LoadedCell cell;
        EsmPathgrid grid;
        if (parts.length >= 2 && isInt(parts[0]) && isInt(parts[1])) {
            int gx = Integer.parseInt(parts[0]);
            int gy = Integer.parseInt(parts[1]);
            cell = EsmFile.loadExterior(EsmReader.open(TestData.esmPath()), gx, gy);
            grid = EsmPathgrid.NONE;
            for (EsmFile.GridTile tile : cell.tiles) {
                if (tile.gridX == gx && tile.gridY == gy) {
                    grid = tile.pathgrid;
                    break;
                }
            }
        } else {
            cell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()), spec);
            grid = cell.pathgrid;
        }
        System.out.println("cell=" + cell.name
            + " grid=(" + grid.gridX + "," + grid.gridY + ")"
            + " interior=" + cell.interior
            + " " + pgrdCounts(grid));
        for (int i = 0; i < grid.points.size(); i++) {
            EsmPathgrid.Point p = grid.points.get(i);
            System.out.println("node=" + i + " tes=" + p.x + " " + p.y + " " + p.z
                + " conn=" + p.connections);
        }
        for (EsmPathgrid.Edge e : grid.edges) {
            System.out.println("edge=" + e.v0 + " " + e.v1);
        }
    }

    private static String pgrdCounts(EsmPathgrid grid) {
        return "pgrd=" + grid.points.size() + " e=" + grid.edges.size();
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String containerKf(String model) {
        String mesh = TexturePaths.normalizeMeshPath(model);
        String anim = TexturePaths.correctActorModelPath(mesh, TestData::vfsExists);
        String kf = TexturePaths.nifToKf(anim);
        return TestData.vfsExists(kf) ? kf : "none";
    }

    private static float fogStart(float density) {
        if (density == 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return CellLighting.VIEW_DISTANCE * (1f - density);
    }

    private static String xyz(float[] v) {
        return "(" + v[0] + "," + v[1] + "," + v[2] + ")";
    }

    private static String require(String[] args, int i, String usage) {
        if (args.length <= i || args[i].isBlank()) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
        StringBuilder sb = new StringBuilder(args[i]);
        for (int n = i + 1; n < args.length; n++) {
            sb.append(' ').append(args[n]);
        }
        return sb.toString();
    }
}

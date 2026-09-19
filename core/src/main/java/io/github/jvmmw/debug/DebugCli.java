package io.github.jvmmw.debug;

import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmCreature;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmNpc;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.esm.EsmReader;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.render.CellLighting;
import io.github.jvmmw.render.NpcMannequin;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Headless ESM/NIF dumps. Run from repo root:
 * {@code gradlew.bat :core:debugCli --args="help"}
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
            case "kf" -> kf(require(args, 1, "kf <vfs-or-path>"));
            case "exterior" -> exterior(require(args, 1, "exterior <gridX> <gridY>"));
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
            gradlew.bat :core:debugCli --args="kf meshes/xbase_anim.kf"
            gradlew.bat :core:debugCli --args="exterior -2 -9"

            nif        Node tree + local transforms. VFS path extracts from BSA into testdata/.
            cell       One interior: fog range, inbound spawn, doors, NPCs, CREA, ref counts (full ESM parse).
            interiors  All interiors: span / fog / spawn. Optional substring filter. CELL-only pass.
            spawn      Inbound DODT for an interior (the OpenMW arrival point).
            npc        One NPC_: race, head, hair, skeleton, equipped CLOT/ARMO parts.
            crea       One CREA: model, corrected x-path, flags, scale.
            kf         Text-key groups and bone tracks from a Morrowind .kf (BSA or extra data dirs).
            exterior   One exterior grid cell: name, LAND min/max, spawn, ref counts.

            Viewer: F3 dumps camera TES3 pos + fog to the log and build/debug-snapshot.txt.
            E activates the closest door, container, or takeable item (192 units). Named interior dest loads that cell.
            HUD Town loads exterior (-2, -9). Empty-DNAM exits stay shut.
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
        System.out.println("cell=" + cell.name
            + " grid=(" + cell.gridX + "," + cell.gridY + ")"
            + " interior=" + cell.interior
            + " refs=" + cell.refs.size()
            + " land=" + (int) cell.land.minHeight + ".." + (int) cell.land.maxHeight);
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
        System.out.println("doors=" + doors + " stat=" + stat + " npcs=" + npcs + " crea=" + crea);
    }

    private static void cell(String name) throws Exception {
        EsmFile.LoadedCell cell = EsmFile.loadInterior(EsmReader.open(TestData.esmPath()), name);
        float fogStart = fogStart(cell.fogDensity);
        System.out.println("cell=" + cell.name
            + " refs=" + cell.refs.size()
            + " fogDensity=" + cell.fogDensity
            + " fogStart=" + (int) fogStart
            + " fogEnd=" + (int) CellLighting.VIEW_DISTANCE);
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
            String model = obj.model.toLowerCase(Locale.ROOT);
            if (model.contains("moldcave") || model.contains("cavern_door")) {
                kit++;
                if (kit <= 40) {
                    System.out.println("kit " + obj.rec + " " + ref.refId
                        + " tes=" + xyz(ref.pos)
                        + " rot=" + xyz(ref.rot)
                        + " scl=" + ref.scale
                        + " " + obj.model);
                }
            }
        }
        System.out.println("doors=" + doors + " kit=" + kit + " npcs=" + npcs + " crea=" + crea
            + " cont=" + cont + " take=" + take + " book=" + books);
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
        System.out.print(NpcMannequin.describeCreature(crea));
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

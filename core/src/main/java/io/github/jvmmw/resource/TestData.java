package io.github.jvmmw.resource;

import io.github.jvmmw.bsa.BsaArchive;
import io.github.jvmmw.nif.NiSourceTexture;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NifRecord;
import io.github.jvmmw.vfs.VfsManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Finds the Morrowind data folder, mounts BSA plus extra dirs, and names
 * the HUD cells (Census, Cave, Nix, Guild, Town, Zain, Manor, Club). Mesh and texture lookups go through
 * the VFS; files the GPU needs are extracted to loose paths first.
 */
public final class TestData {
    public static final String CHAIR = "meshes/f/furn_de_chair_01.nif";
    public static final String SHACK = "meshes/x/ex_de_shack_01.nif";
    public static final String TREE = "meshes/f/flora_bc_tree_01.nif";
    public static final String GLASS_DAGGER = "meshes/w/w_dagger_glass.nif";
    public static final String GLASS_STAFF = "meshes/w/w_staff_glass.nif";
    public static final String BANNER = "meshes/f/furn_6th_banner.nif";
    public static final String DWRV = "meshes/i/in_dwrv_corr1_00.nif";
    public static final String CENSUS_CELL = "Seyda Neen, Census and Excise Office";
    public static final String PRISON_SHIP = "Imperial Prison Ship";
    public static final String ADDAMASARTUS = "Addamasartus";
    public static final String PUNSABANIT = "Punsabanit";
    public static final String ZAINSIPILU = "Zainsipilu";
    public static final String VENIM_MANOR = "Ald-ruhn, Venim Manor Right Wing";
    public static final String COUNCIL_CLUB = "Balmora, Council Club";
    public static final String WOLVERINE_GUILD = "Sadrith Mora, Wolverine Hall: Mage's Guild";
    public static final int TOWN_GRID_X = -2;
    public static final int TOWN_GRID_Y = -9;

    private static BsaArchive cachedBsa;
    private static VfsManager cachedVfs;
    private static Properties localProps;

    private TestData() {
    }

    public static Path dataRoot() {
        String configured = configured("JVMMW_DATA", "jvmmw.data");
        if (configured != null) {
            return Path.of(configured);
        }
        return Path.of(".");
    }

    public static Path esmPath() {
        return dataRoot().resolve("Morrowind.esm");
    }

    public static Path testdataRoot() {
        return Path.of("testdata");
    }

    /**
     * OpenMW {@code navmesh.db} from umo / navmeshtool. Override with
     * {@code jvmmw.navmesh} / {@code JVMMW_NAVMESH}.
     */
    public static Path navmeshDb() {
        String configured = configured("JVMMW_NAVMESH", "jvmmw.navmesh");
        if (configured != null) {
            return Path.of(configured);
        }
        Path home = Path.of(System.getProperty("user.home", "."));
        Path docs = home.resolve("Documents").resolve("My Games").resolve("OpenMW").resolve("navmesh.db");
        if (Files.isRegularFile(docs)) {
            return docs;
        }
        Path linux = home.resolve(".config").resolve("openmw").resolve("navmesh.db");
        if (Files.isRegularFile(linux)) {
            return linux;
        }
        return docs;
    }

    public static boolean vfsExists(String vfsPath) {
        try {
            return vfs().exists(vfsPath);
        } catch (Exception e) {
            return false;
        }
    }

    public static Path openPath(String vfsPath) throws IOException {
        VfsManager.Source src = vfs().get(vfsPath);
        if (src == null) {
            throw new IOException("Not in VFS: " + vfsPath);
        }
        return src.open(testdataRoot());
    }

    public static Path localNif(String vfsPath) {
        return testdataRoot().resolve(vfsPath.replace('/', java.io.File.separatorChar));
    }

    public static Path ensureChair() throws Exception {
        return ensureNif(CHAIR);
    }

    public static Path ensureNif(String vfsPath) throws Exception {
        Path nifOut = openPath(vfsPath);
        byte[] nifBytes = Files.readAllBytes(nifOut);
        NifFile nif = NifFile.parse(nifBytes, vfsPath);
        List<String> textures = new ArrayList<>();
        for (NifRecord r : nif.records) {
            if (r instanceof NiSourceTexture src && src.external && !src.file.isEmpty()) {
                textures.add(src.file);
            }
        }
        for (String tex : textures) {
            String vfs = TexturePaths.correctTexturePath(tex, TestData::vfsExists);
            if (!vfsExists(vfs)) {
                continue;
            }
            openPath(vfs);
        }
        return nifOut;
    }

    private static VfsManager vfs() throws IOException {
        if (cachedVfs != null) {
            return cachedVfs;
        }
        VfsManager vfs = new VfsManager();
        Path data = dataRoot();
        BsaArchive archive = bsa(data);
        if (archive != null) {
            vfs.addBsa(archive);
        }
        if (Files.isDirectory(data)) {
            vfs.addDir(data);
        }
        Set<Path> seen = new LinkedHashSet<>();
        if (Files.isDirectory(data)) {
            seen.add(data.toAbsolutePath().normalize());
        }
        for (Path extra : extraDirs()) {
            Path abs = extra.toAbsolutePath().normalize();
            if (!seen.add(abs)) {
                continue;
            }
            if (!Files.isDirectory(abs)) {
                System.err.println("vfs extra missing " + abs);
                continue;
            }
            System.out.println("vfs extra=" + abs);
            vfs.addDir(abs);
        }
        vfs.buildIndex();
        cachedVfs = vfs;
        return cachedVfs;
    }

    private static List<Path> extraDirs() {
        List<Path> dirs = new ArrayList<>();
        String raw = configured("JVMMW_DATA_EXTRA", "jvmmw.data.extra");
        if (raw == null || raw.isBlank()) {
            return dirs;
        }
        for (String part : raw.split(";")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            if (!trimmed.isBlank()) {
                dirs.add(Path.of(trimmed));
            }
        }
        return dirs;
    }

    private static String configured(String envName, String propName) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty(propName);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        Properties p = localProperties();
        if (p != null) {
            String v = p.getProperty(propName);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static Properties localProperties() {
        if (localProps != null) {
            return localProps;
        }
        Path local = Path.of("local.properties");
        if (!Files.isRegularFile(local)) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(local)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + local, e);
        }
        localProps = p;
        return p;
    }

    private static BsaArchive bsa(Path data) throws IOException {
        if (cachedBsa != null) {
            return cachedBsa;
        }
        Path bsaPath = data.resolve("Morrowind.bsa");
        if (!Files.isRegularFile(bsaPath)) {
            return null;
        }
        cachedBsa = new BsaArchive(bsaPath);
        return cachedBsa;
    }
}

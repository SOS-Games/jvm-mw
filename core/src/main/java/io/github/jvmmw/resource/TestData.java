package io.github.jvmmw.resource;

import io.github.jvmmw.bsa.BsaArchive;
import io.github.jvmmw.nif.NiSourceTexture;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NifRecord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
    public static final String WOLVERINE_GUILD = "Sadrith Mora, Wolverine Hall: Mage's Guild";

    private static BsaArchive cachedBsa;

    private TestData() {
    }

    public static Path dataRoot() {
        String configured = configuredDataPath();
        if (configured != null) {
            return Path.of(configured);
        }
        return Path.of(".");
    }

    private static String configuredDataPath() {
        String env = System.getenv("JVMMW_DATA");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty("jvmmw.data");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        Path local = Path.of("local.properties");
        if (Files.isRegularFile(local)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(local)) {
                p.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + local, e);
            }
            String v = p.getProperty("jvmmw.data");
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    public static Path esmPath() {
        return dataRoot().resolve("Morrowind.esm");
    }

    public static Path testdataRoot() {
        return Path.of("testdata");
    }

    public static boolean vfsExists(String vfsPath) {
        String n = vfsPath.replace('\\', '/').toLowerCase();
        if (Files.isRegularFile(localNif(n))) {
            return true;
        }
        try {
            BsaArchive archive = bsa(dataRoot());
            return archive != null && archive.contains(n);
        } catch (Exception e) {
            return false;
        }
    }

    public static Path localNif(String vfsPath) {
        return testdataRoot().resolve(vfsPath.replace('/', java.io.File.separatorChar));
    }

    public static Path ensureChair() throws Exception {
        return ensureNif(CHAIR);
    }

    public static Path ensureNif(String vfsPath) throws Exception {
        Path testdata = testdataRoot();
        Path nifOut = localNif(vfsPath);
        Path data = dataRoot();
        BsaArchive bsa = bsa(data);
        if (!Files.isRegularFile(nifOut)) {
            if (bsa == null) {
                throw new IllegalStateException("Missing Morrowind.bsa and " + nifOut
                    + ". Set JVMMW_DATA, -Djvmmw.data, or jvmmw.data in gitignored local.properties "
                    + "to your Morrowind Data Files folder.");
            }
            bsa.extract(vfsPath, nifOut);
        }
        byte[] nifBytes = Files.readAllBytes(nifOut);
        NifFile nif = NifFile.parse(nifBytes, vfsPath);
        List<String> textures = new ArrayList<>();
        for (NifRecord r : nif.records) {
            if (r instanceof NiSourceTexture src && src.external && !src.file.isEmpty()) {
                textures.add(src.file);
            }
        }
        for (String tex : textures) {
            String vfs = TexturePaths.correctTexturePath(tex, p -> {
                if (Files.isRegularFile(testdata.resolve(p.replace('/', java.io.File.separatorChar)))) {
                    return true;
                }
                return bsa != null && bsa.contains(p);
            });
            Path dest = testdata.resolve(vfs.replace('/', java.io.File.separatorChar));
            if (Files.isRegularFile(dest)) {
                continue;
            }
            if (bsa != null && bsa.contains(vfs)) {
                bsa.extract(vfs, dest);
            }
        }
        return nifOut;
    }

    private static BsaArchive bsa(Path data) throws Exception {
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

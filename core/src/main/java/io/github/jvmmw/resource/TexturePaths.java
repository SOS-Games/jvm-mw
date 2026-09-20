/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Fixes mesh and texture paths from the ESM or .nif: slashes, textures/
 * prefix, missing extension.
 */
public final class TexturePaths {
    private TexturePaths() {
    }

    public static String normalizeMeshPath(String nifPath) {
        String p = nifPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!p.startsWith("meshes/") && !p.startsWith("nifs/")) {
            p = "meshes/" + p;
        }
        return p;
    }

    public static String correctTexturePath(String resPath, Predicate<String> exists) {
        String raw = resPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!raw.contains("/")) {
            raw = "textures/" + raw;
        }
        String dds = changeExtension(raw, "dds");
        if (exists.test(dds)) {
            return dds;
        }
        if (exists.test(raw)) {
            return raw;
        }
        String file = dds.substring(dds.lastIndexOf('/') + 1);
        String fallback = "textures/" + file;
        if (exists.test(fallback)) {
            return fallback;
        }
        return dds;
    }

    public static String correctActorModelPath(String resPath, Predicate<String> exists) {
        String mdl = resPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        int slash = mdl.lastIndexOf('/');
        String xname = slash >= 0
            ? mdl.substring(0, slash + 1) + "x" + mdl.substring(slash + 1)
            : "x" + mdl;
        String kf = changeExtension(xname, "kf");
        if (!exists.test(kf)) {
            return mdl;
        }
        return xname;
    }

    public static String nifToKf(String nifPath) {
        return changeExtension(nifPath.replace('\\', '/').toLowerCase(Locale.ROOT), "kf");
    }

    public static boolean fileExists(Path dataRoot, String vfsPath) {
        return Files.isRegularFile(dataRoot.resolve(vfsPath.replace('/', java.io.File.separatorChar)));
    }

    private static String changeExtension(String path, String ext) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot > slash) {
            return path.substring(0, dot + 1) + ext;
        }
        return path + "." + ext;
    }
}

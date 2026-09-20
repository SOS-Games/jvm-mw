/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.vfs;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loose files under a data folder (the game dir or extra folders). Last
 * source still wins in the VFS.
 */
public final class FileSystemArchive {
    private final Map<String, Path> files = new HashMap<>();

    public FileSystemArchive(Path root) throws IOException {
        Path abs = root.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(abs, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String rel = abs.relativize(file).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (rel.startsWith("../") || rel.equals("..")) {
                    return;
                }
                files.putIfAbsent(rel, file);
            });
        }
    }

    public void listResources(Map<String, Path> out) {
        out.putAll(files);
    }
}

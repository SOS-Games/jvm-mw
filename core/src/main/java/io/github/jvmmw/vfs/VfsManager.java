/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.vfs;

import io.github.jvmmw.bsa.BsaArchive;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Last archive wins a given path. Maps to {@code VFS::Manager} + {@code registerArchives}.
 */
public final class VfsManager {
    public sealed interface Source permits Loose, Bsa {
        Path open(Path testdataCache) throws IOException;
    }

    public record Loose(Path file) implements Source {
        @Override
        public Path open(Path testdataCache) {
            return file;
        }
    }

    public record Bsa(BsaArchive archive, String vfsPath) implements Source {
        @Override
        public Path open(Path testdataCache) throws IOException {
            Path dest = testdataCache.resolve(vfsPath.replace('/', java.io.File.separatorChar));
            if (!java.nio.file.Files.isRegularFile(dest)) {
                archive.extract(vfsPath, dest);
            }
            return dest;
        }
    }

    private final List<Lister> archives = new ArrayList<>();
    private final Map<String, Source> index = new HashMap<>();

    public void addBsa(BsaArchive bsa) {
        archives.add(out -> {
            for (String path : bsa.paths()) {
                out.put(path, new Bsa(bsa, path));
            }
        });
    }

    public void addDir(Path dir) throws IOException {
        FileSystemArchive fs = new FileSystemArchive(dir);
        archives.add(out -> {
            Map<String, Path> files = new HashMap<>();
            fs.listResources(files);
            for (Map.Entry<String, Path> e : files.entrySet()) {
                out.put(e.getKey(), new Loose(e.getValue()));
            }
        });
    }

    public void buildIndex() {
        index.clear();
        for (Lister archive : archives) {
            archive.listResources(index);
        }
    }

    public boolean exists(String vfsPath) {
        return index.containsKey(normalize(vfsPath));
    }

    public Source get(String vfsPath) {
        return index.get(normalize(vfsPath));
    }

    public static String normalize(String vfsPath) {
        return vfsPath.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface Lister {
        void listResources(Map<String, Source> out);
    }
}

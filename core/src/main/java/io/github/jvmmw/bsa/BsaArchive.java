/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.bsa;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** TES3 BSA. Maps to {@code Bsa::BSAFile}. */
public final class BsaArchive {
    private final Path file;
    private final Map<String, Entry> entries = new HashMap<>();

    public BsaArchive(Path file) throws IOException {
        this.file = file;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, head);
            head.flip();
            int id = head.getInt();
            if (id != 0x100) {
                throw new IOException("Unrecognized BSA header in " + file);
            }
            int dirSize = head.getInt();
            int fileNum = head.getInt();
            ByteBuffer dir = ByteBuffer.allocate(12 * fileNum + Math.max(0, dirSize - 12 * fileNum))
                .order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, dir);
            dir.flip();
            int[] sizes = new int[fileNum];
            int[] relOffsets = new int[fileNum];
            for (int i = 0; i < fileNum; i++) {
                sizes[i] = dir.getInt();
                relOffsets[i] = dir.getInt();
            }
            int[] nameOffsets = new int[fileNum];
            for (int i = 0; i < fileNum; i++) {
                nameOffsets[i] = dir.getInt();
            }
            int nameBufSize = dirSize - 12 * fileNum;
            byte[] names = new byte[nameBufSize];
            dir.get(names);
            int fileDataOffset = 12 + dirSize + 8 * fileNum;
            for (int i = 0; i < fileNum; i++) {
                int no = nameOffsets[i];
                int end = no;
                while (end < names.length && names[end] != 0) {
                    end++;
                }
                String name = new String(names, no, end - no, StandardCharsets.ISO_8859_1)
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);
                entries.put(name, new Entry(fileDataOffset + relOffsets[i], sizes[i] & 0x3FFFFFFF));
            }
        }
    }

    public boolean contains(String path) {
        return entries.containsKey(normalize(path));
    }

    public void extract(String path, Path dest) throws IOException {
        Entry e = entries.get(normalize(path));
        if (e == null) {
            throw new IOException("Not in BSA " + file.getFileName() + ": " + path);
        }
        Files.createDirectories(dest.getParent());
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(e.size);
            ch.position(e.offset);
            readFully(ch, buf);
            Files.write(dest, buf.array());
        }
    }

    private static void readFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) {
                throw new IOException("Unexpected EOF in BSA");
            }
        }
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private record Entry(int offset, int size) {
    }
}

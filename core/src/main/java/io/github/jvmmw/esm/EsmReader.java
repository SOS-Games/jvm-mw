/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** TES3 ESM stream. Maps to {@code ESM::ESMReader}. */
public final class EsmReader {
    private final ByteBuffer buf;
    private int leftFile;
    private int leftRec;
    private int leftSub;
    private boolean subCached;
    private String recName = "";
    private String subName = "";

    public EsmReader(byte[] data) {
        buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        leftFile = data.length;
    }

    public static EsmReader open(Path path) throws Exception {
        byte[] data = Files.readAllBytes(path);
        EsmReader esm = new EsmReader(data);
        if (!"TES3".equals(esm.getRecName())) {
            throw new IllegalArgumentException("Not a Morrowind ESM: " + path);
        }
        esm.getRecHeader();
        esm.skipRecord();
        return esm;
    }

    public int position() {
        return buf.position();
    }

    public boolean hasMoreRecs() {
        return leftFile > 0;
    }

    public boolean hasMoreSubs() {
        return leftRec > 0;
    }

    public String getRecName() {
        if (!hasMoreRecs()) {
            throw new IllegalStateException("No more records");
        }
        if (hasMoreSubs()) {
            throw new IllegalStateException("Unread bytes in previous record (" + leftRec + ")");
        }
        if (leftRec < 0) {
            buf.position(buf.position() + leftRec);
            leftRec = 0;
        }
        recName = readName();
        leftFile -= 4;
        subCached = false;
        return recName;
    }

    public int getRecHeader() {
        int size = buf.getInt();
        buf.getInt(); // unused
        int flags = buf.getInt();
        leftRec = size;
        leftFile -= 12;
        leftFile -= leftRec;
        return flags;
    }

    public void skipRecord() {
        if (leftRec > 0) {
            buf.position(buf.position() + leftRec);
        }
        leftRec = 0;
        subCached = false;
    }

    public String getSubName() {
        if (subCached) {
            subCached = false;
            return subName;
        }
        subName = readName();
        leftRec -= 4;
        return subName;
    }

    public void getSubHeader() {
        leftSub = buf.getInt();
        leftRec -= 4;
        leftRec -= leftSub;
    }

    public boolean isNextSub(String name) {
        if (!hasMoreSubs()) {
            return false;
        }
        getSubName();
        subCached = !subName.equals(name);
        return !subCached;
    }

    public boolean peekNextSub(String name) {
        if (!hasMoreSubs()) {
            return false;
        }
        getSubName();
        subCached = true;
        return subName.equals(name);
    }

    public void skipHSub() {
        getSubHeader();
        skip(leftSub);
    }

    public String getHString() {
        getSubHeader();
        return readString(leftSub);
    }

    public int getI32() {
        int v = buf.getInt();
        leftSub -= 4;
        return v;
    }

    public float getF32() {
        float v = buf.getFloat();
        leftSub -= 4;
        return v;
    }

    public int getHNTInt(String name) {
        expectSub(name);
        getSubHeader();
        return getI32();
    }

    public float getHNTFloat(String name) {
        expectSub(name);
        getSubHeader();
        return getF32();
    }

    public void cacheSubName() {
        subCached = true;
    }

    public void skip(int n) {
        if (n > 0) {
            buf.position(buf.position() + n);
        }
        leftSub -= n;
    }

    public void skipRestOfSub() {
        if (leftSub > 0) {
            skip(leftSub);
        }
    }

    private void expectSub(String name) {
        String got = getSubName();
        if (!got.equals(name)) {
            throw new IllegalStateException("Expected " + name + " got " + got);
        }
    }

    private String readName() {
        byte[] n = new byte[4];
        buf.get(n);
        return new String(n, StandardCharsets.US_ASCII);
    }

    private String readString(int size) {
        if (size <= 0) {
            return "";
        }
        byte[] raw = new byte[size];
        buf.get(raw);
        int end = 0;
        while (end < raw.length && raw[end] != 0) {
            end++;
        }
        return new String(raw, 0, end, StandardCharsets.ISO_8859_1);
    }
}

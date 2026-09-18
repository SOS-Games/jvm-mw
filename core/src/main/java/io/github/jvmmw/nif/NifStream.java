/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.jvmmw.nif;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Binary reader for Morrowind NIF 4.0.0.2. Maps to {@code Nif::NIFStream}. */
public final class NifStream {
    public static final int VER_MW = 0x04000002;
    public static final int VER_4_0_0_0 = 0x04000000;

    private final ByteBuffer buf;
    int version;

    public NifStream(byte[] data) {
        buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    public int getVersion() {
        return version;
    }

    public int remaining() {
        return buf.remaining();
    }

    public int position() {
        return buf.position();
    }

    public byte getI8() {
        return buf.get();
    }

    public short getI16() {
        return buf.getShort();
    }

    public int getU16() {
        return buf.getShort() & 0xFFFF;
    }

    public int getI32() {
        return buf.getInt();
    }

    public long getU32() {
        return buf.getInt() & 0xFFFFFFFFL;
    }

    public float getF32() {
        return buf.getFloat();
    }

    public boolean getBool() {
        // OpenMW: version < 4.1.0.0 → int32, else int8
        if (version < generateVersion(4, 1, 0, 0)) {
            return getI32() != 0;
        }
        return getI8() != 0;
    }

    public void getVec2(float[] out, int offset) {
        out[offset] = getF32();
        out[offset + 1] = getF32();
    }

    public void getVec3(float[] out, int offset) {
        out[offset] = getF32();
        out[offset + 1] = getF32();
        out[offset + 2] = getF32();
    }

    public void getVec4(float[] out, int offset) {
        out[offset] = getF32();
        out[offset + 1] = getF32();
        out[offset + 2] = getF32();
        out[offset + 3] = getF32();
    }

    public void skip(int bytes) {
        buf.position(buf.position() + bytes);
    }

    public String getVersionString() {
        StringBuilder sb = new StringBuilder();
        while (buf.hasRemaining()) {
            byte b = buf.get();
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                sb.append((char) (b & 0xFF));
            }
        }
        return sb.toString();
    }

    public String getSizedString() {
        int len = getI32();
        if (len < 0 || len > buf.remaining()) {
            throw new IllegalStateException("Bad sized string length " + len);
        }
        byte[] raw = new byte[len];
        buf.get(raw);
        int end = 0;
        while (end < raw.length && raw[end] != 0) {
            end++;
        }
        return new String(raw, 0, end, StandardCharsets.ISO_8859_1);
    }

    public static int generateVersion(int major, int minor, int patch, int rev) {
        return (major << 24) + (minor << 16) + (patch << 8) + rev;
    }

    public void requireRemaining(int n) throws IOException {
        if (buf.remaining() < n) {
            throw new IOException("Unexpected end of NIF");
        }
    }
}

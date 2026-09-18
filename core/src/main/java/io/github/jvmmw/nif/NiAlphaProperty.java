/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

public class NiAlphaProperty extends NifRecord {
    public static final int FLAG_BLEND = 0x0001;
    public static final int FLAG_TEST = 0x0200;
    public static final int FLAG_NO_SORTER = 0x2000;

    public int flags;
    public int threshold;

    public boolean blending() {
        return (flags & FLAG_BLEND) != 0;
    }

    public boolean testing() {
        return (flags & FLAG_TEST) != 0;
    }

    public boolean noSorter() {
        return (flags & FLAG_NO_SORTER) != 0;
    }

    public int sourceBlend() {
        return (flags >> 1) & 0xF;
    }

    public int destBlend() {
        return (flags >> 5) & 0xF;
    }

    public int testMode() {
        return (flags >> 10) & 0x7;
    }
}

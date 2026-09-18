/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

/** NAME + MODL for a placeable TES3 record. Rewrite of {@code MWClass::getClassModel}. */
public final class EsmObject {
    public String id = "";
    public String model = "";
    public String rec = "";
    public boolean hasLight;
    public int lightRadius;
    public int lightColor;
    public int lightFlags;

    public static final int LIGH_NEGATIVE = 0x004;
    public static final int LIGH_FLICKER = 0x008;
    public static final int LIGH_OFF_DEFAULT = 0x020;
    public static final int LIGH_FLICKER_SLOW = 0x040;
    public static final int LIGH_PULSE = 0x080;
    public static final int LIGH_PULSE_SLOW = 0x100;
    public static final int LIGH_IGNORABLE = 0x001 | 0x002 | 0x010;
}

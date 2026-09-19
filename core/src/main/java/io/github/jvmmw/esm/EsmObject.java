/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.List;

/** NAME + MODL for a placeable TES3 record. Rewrite of {@code MWClass::getClassModel}. */
public final class EsmObject {
    public static final int CLOT_PANTS = 0;
    public static final int CLOT_SHOES = 1;
    public static final int CLOT_SHIRT = 2;
    public static final int CLOT_BELT = 3;
    public static final int CLOT_ROBE = 4;
    public static final int CLOT_RGLOVE = 5;
    public static final int CLOT_LGLOVE = 6;
    public static final int CLOT_SKIRT = 7;
    public static final int CLOT_RING = 8;
    public static final int CLOT_AMULET = 9;

    public static final int ARMO_HELMET = 0;
    public static final int ARMO_CUIRASS = 1;
    public static final int ARMO_LPAULDRON = 2;
    public static final int ARMO_RPAULDRON = 3;
    public static final int ARMO_GREAVES = 4;
    public static final int ARMO_BOOTS = 5;
    public static final int ARMO_LGAUNTLET = 6;
    public static final int ARMO_RGAUNTLET = 7;
    public static final int ARMO_SHIELD = 8;
    public static final int ARMO_LBRACER = 9;
    public static final int ARMO_RBRACER = 10;

    public String id = "";
    public String model = "";
    public String rec = "";
    public boolean hasLight;
    public int lightRadius;
    public int lightColor;
    public int lightFlags;
    public int clothType = -1;
    public int armorType = -1;
    public int value;
    public final List<EsmPartRef> parts = new ArrayList<>();

    public static boolean isTakeable(EsmObject obj) {
        return switch (obj.rec) {
            case "WEAP", "ARMO", "CLOT", "MISC", "INGR", "ALCH", "APPA", "LOCK", "PROB", "REPA" -> true;
            case "LIGH" -> (obj.lightFlags & LIGH_CARRY) != 0;
            default -> false;
        };
    }

    public static boolean isBook(EsmObject obj) {
        return "BOOK".equals(obj.rec);
    }

    public static final int LIGH_CARRY = 0x002;
    public static final int LIGH_NEGATIVE = 0x004;
    public static final int LIGH_FLICKER = 0x008;
    public static final int LIGH_OFF_DEFAULT = 0x020;
    public static final int LIGH_FLICKER_SLOW = 0x040;
    public static final int LIGH_PULSE = 0x080;
    public static final int LIGH_PULSE_SLOW = 0x100;
    public static final int LIGH_IGNORABLE = 0x001 | 0x002 | 0x010;
}

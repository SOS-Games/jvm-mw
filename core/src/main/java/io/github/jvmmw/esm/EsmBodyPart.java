/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

/**
 * A body-part mesh (head, hair, chest, …). NPCs pick these by race and
 * sex; clothes and armor replace slots.
 */
public final class EsmBodyPart {
    public static final int MP_HEAD = 0;
    public static final int MP_HAIR = 1;
    public static final int MP_NECK = 2;
    public static final int MP_CHEST = 3;
    public static final int MP_GROIN = 4;
    public static final int MP_HAND = 5;
    public static final int MP_WRIST = 6;
    public static final int MP_FOREARM = 7;
    public static final int MP_UPPERARM = 8;
    public static final int MP_FOOT = 9;
    public static final int MP_ANKLE = 10;
    public static final int MP_KNEE = 11;
    public static final int MP_UPPERLEG = 12;
    public static final int MP_CLAVICLE = 13;
    public static final int MP_TAIL = 14;

    public static final int BPF_FEMALE = 1;
    public static final int BPF_NOT_PLAYABLE = 2;

    public static final int MT_SKIN = 0;
    public static final int MT_CLOTHING = 1;
    public static final int MT_ARMOR = 2;

    public String id = "";
    public String race = "";
    public String model = "";
    public int part;
    public int vampire;
    public int flags;
    public int type;

    public boolean female() {
        return (flags & BPF_FEMALE) != 0;
    }

    public boolean notPlayable() {
        return (flags & BPF_NOT_PLAYABLE) != 0;
    }

    public boolean firstPerson() {
        return id.regionMatches(true, Math.max(0, id.length() - 3), "1st", 0, 3);
    }
}

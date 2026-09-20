/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

/**
 * One creature from the ESM: mesh, flags (biped / swim / fly / walk), scale.
 * Placed from its own mesh file, not body parts.
 */
public final class EsmCreature {
    public static final int BIPEDAL = 0x01;
    public static final int WEAPON = 0x04;
    public static final int SWIMS = 0x10;
    public static final int FLIES = 0x20;
    public static final int WALKS = 0x40;

    public String id = "";
    public String name = "";
    public String model = "";
    public int flags;
    public float scale = 1f;

    public boolean bipedal() {
        return (flags & BIPEDAL) != 0;
    }

    public boolean weapon() {
        return (flags & WEAPON) != 0;
    }

    public boolean swims() {
        return (flags & SWIMS) != 0;
    }

    public boolean flies() {
        return (flags & FLIES) != 0;
    }

    public boolean walks() {
        return (flags & WALKS) != 0;
    }
}

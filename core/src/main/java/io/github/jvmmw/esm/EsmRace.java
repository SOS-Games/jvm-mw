/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

/** TES3 {@code RACE}. Maps to {@code ESM::Race}. */
public final class EsmRace {
    public static final int FLAG_BEAST = 0x02;

    public String id = "";
    public float maleHeight = 1f;
    public float femaleHeight = 1f;
    public float maleWeight = 1f;
    public float femaleWeight = 1f;
    public int flags;

    public boolean beast() {
        return (flags & FLAG_BEAST) != 0;
    }
}

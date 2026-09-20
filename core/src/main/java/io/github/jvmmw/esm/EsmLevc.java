/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.List;

/**
 * A creature leveled list: the wilderness spawn markers (rats, mudcrabs,
 * slaughterfish) that have no mesh of their own. Town’s empty coast is
 * these, not missing CREA records. {@code AllLevels} is bit 0 — not the
 * item-list bit.
 */
public final class EsmLevc {
    /** Include every entry at or below the player, not only the closest band. */
    public static final int ALL_LEVELS = 0x01;
    /** Viewer has no stats; chargen is 1. */
    public static final int PLAYER_LEVEL = 1;

    public String id = "";
    public int flags;
    public int chanceNone;
    public final List<Entry> entries = new ArrayList<>();

    public boolean allLevels() {
        return (flags & ALL_LEVELS) != 0;
    }

    public static final class Entry {
        public String id = "";
        public int level;
    }
}

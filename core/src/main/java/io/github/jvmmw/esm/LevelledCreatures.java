/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks one creature id from a leveled list. Chance-none can yield nothing.
 * Nested lists unroll with the same player level. Missing ids are skipped.
 *
 * Walk-grid rebuilds keep the last pick for each marker so the same rat
 * does not turn into a scrib when you cross a cell. HUD Town / Cave loads
 * forget and roll again.
 */
public final class LevelledCreatures {
    private static final Map<String, String> remembered = new ConcurrentHashMap<>();

    private LevelledCreatures() {
    }

    public static void forget() {
        remembered.clear();
    }

    /**
     * Empty string means spawn nothing (chance-none, no legal entry, or a
     * hole in the list).
     */
    public static String pick(EsmLevc list, int playerLevel, Random rng, Map<String, EsmLevc> lists,
            Map<String, EsmCreature> creatures) {
        return pick(list, playerLevel, rng, lists, creatures, 0);
    }

    /** Same roll as pick, reused when the walk grid rebuilds this marker. */
    public static String pickOrRemember(CellRef ref, EsmLevc list, int playerLevel, Random rng,
            Map<String, EsmLevc> lists, Map<String, EsmCreature> creatures) {
        String key = ref.takeKey();
        String cached = remembered.get(key);
        if (cached != null) {
            return cached;
        }
        String id = pick(list, playerLevel, rng, lists, creatures);
        remembered.put(key, id);
        return id;
    }

    private static String pick(EsmLevc list, int playerLevel, Random rng, Map<String, EsmLevc> lists,
            Map<String, EsmCreature> creatures, int depth) {
        if (list == null || depth > 16) {
            return "";
        }
        if (rng.nextInt(100) < list.chanceNone) {
            return "";
        }
        int highest = 0;
        for (EsmLevc.Entry e : list.entries) {
            if (e.level <= playerLevel && e.level > highest) {
                highest = e.level;
            }
        }
        boolean all = list.allLevels();
        List<String> candidates = new ArrayList<>();
        for (EsmLevc.Entry e : list.entries) {
            if (playerLevel >= e.level && (all || e.level == highest)) {
                candidates.add(e.id);
            }
        }
        if (candidates.isEmpty()) {
            return "";
        }
        String id = candidates.get(rng.nextInt(candidates.size()));
        if (id == null || id.isEmpty()) {
            return "";
        }
        String key = id.toLowerCase(Locale.ROOT);
        EsmLevc nested = lists.get(key);
        if (nested != null) {
            return pick(nested, playerLevel, rng, lists, creatures, depth + 1);
        }
        if (!creatures.containsKey(key)) {
            return "";
        }
        return id;
    }
}

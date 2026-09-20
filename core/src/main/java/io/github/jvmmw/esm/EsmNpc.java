/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.List;

/**
 * One NPC from the ESM: race, head, hair, female flag, equipped clothes.
 * The viewer ignores the mesh on this record and builds a dressed skeleton
 * instead.
 */
public final class EsmNpc {
    public static final int FLAG_FEMALE = 0x01;

    public String id = "";
    public String name = "";
    public String model = "";
    public String race = "";
    public String head = "";
    public String hair = "";
    public int flags;
    public final List<String> inventory = new ArrayList<>();

    public boolean female() {
        return (flags & FLAG_FEMALE) != 0;
    }
}

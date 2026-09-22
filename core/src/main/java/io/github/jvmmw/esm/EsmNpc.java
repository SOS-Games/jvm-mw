/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.List;

/**
 * One NPC from the ESM: race, head, hair, female flag, equipped clothes,
 * the first wander radius, and the full AI package list. The viewer ignores
 * the mesh on this record and builds a dressed skeleton instead. Only the
 * first wander distance is walked.
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
    /** First AI_W distance. 0 means stay (no package or radius 0). */
    public int wanderDistance;
    /** Every AI package in file order. Not executed. */
    public final List<AiPackage> packages = new ArrayList<>();
    public final List<String> inventory = new ArrayList<>();

    public boolean female() {
        return (flags & FLAG_FEMALE) != 0;
    }
}

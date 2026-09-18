/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

/** Maps to {@code Nif::NiSkinInstance}. Morrowind has no partitions on the instance. */
public final class NiSkinInstance extends NifRecord {
    public int data = -1;
    public int root = -1;
    public final List<Integer> bones = new ArrayList<>();
}

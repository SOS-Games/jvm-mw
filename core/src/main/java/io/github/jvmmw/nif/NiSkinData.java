/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

/** Maps to {@code Nif::NiSkinData}. */
public final class NiSkinData extends NifRecord {
    public final NiTransform transform = new NiTransform();
    public final List<Bone> bones = new ArrayList<>();

    public static final class Bone {
        public final NiTransform transform = new NiTransform();
        public final List<Weight> weights = new ArrayList<>();
    }

    public static final class Weight {
        public int vertex;
        public float weight;

        public Weight(int vertex, float weight) {
            this.vertex = vertex;
            this.weight = weight;
        }
    }
}

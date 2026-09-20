/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a .nif with a local pose, flags, and material links. skipMeshes
 * means collision or marker extra data — not drawn.
 */
public class NiAvObject extends NifRecord {
    public static final int FLAG_HIDDEN = 0x0001;

    public int flags;
    public final NiTransform transform = new NiTransform();
    public final float[] velocity = new float[3];
    public final List<Integer> properties = new ArrayList<>();
    public boolean skipMeshes;

    public boolean hidden() {
        return (flags & FLAG_HIDDEN) != 0;
    }
}

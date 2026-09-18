/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

public class NiStencilProperty extends NifRecord {
    public static final int DRAW_BOTH = 3;

    public int flags;
    public boolean enabled;
    public int drawMode;
}

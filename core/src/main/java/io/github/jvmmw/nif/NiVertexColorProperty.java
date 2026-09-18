/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

/** Maps to {@code Nif::NiVertexColorProperty}. */
public class NiVertexColorProperty extends NifRecord {
    public static final int VERT_IGNORE = 0;
    public static final int VERT_EMISSIVE = 1;
    public static final int VERT_AMB_DIFF = 2;
    public static final int LIGHT_EMISSIVE = 0;
    public static final int LIGHT_EMI_AMB_DIFF = 1;

    public int flags;
    public int vertexMode;
    public int lightingMode;
}

/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

/**
 * One thing placed in a cell: which object, where, scale, and (for doors)
 * where it teleports.
 */
public final class CellRef {
    public int frmr;
    public String refId = "";
    public float scale = 1f;
    public boolean deleted;
    public boolean teleport;
    public String destCell = "";
    public final float[] pos = new float[3];
    public final float[] rot = new float[3];
    public final float[] destPos = new float[3];
    public final float[] destRot = new float[3];

    public String takeKey() {
        return frmr + ":" + refId + ":" + pos[0] + ":" + pos[1] + ":" + pos[2];
    }
}

/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

public class NiTriShapeData extends NifRecord {
    public int numVertices;
    public float[] vertices = new float[0];
    public float[] normals = new float[0];
    public float[] colors = new float[0];
    public float[] uvs = new float[0];
    public short[] triangles = new short[0];
    public boolean strips;
    public final List<short[]> stripList = new ArrayList<>();
}

/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

public class NiMaterialProperty extends NifRecord {
    public int flags;
    public final float[] ambient = {1, 1, 1};
    public final float[] diffuse = {1, 1, 1};
    public final float[] specular = {0, 0, 0};
    public final float[] emissive = {0, 0, 0};
    public float glossiness;
    public float alpha = 1f;
}

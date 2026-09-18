/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

/** Maps to {@code Nif::NiKeyframeData}. */
public final class NiKeyframeData extends NifRecord {
    public NifKeyMap rotations = new NifKeyMap();
    public NifKeyMap xRot = new NifKeyMap();
    public NifKeyMap yRot = new NifKeyMap();
    public NifKeyMap zRot = new NifKeyMap();
    public NifKeyMap translations = new NifKeyMap();
    public NifKeyMap scales = new NifKeyMap();
    public int axisOrder;
}

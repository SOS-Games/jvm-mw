/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

public class NiZBufferProperty extends NifRecord {
    public int flags;

    public boolean depthTest() {
        return (flags & 1) != 0;
    }

    public boolean depthWrite() {
        return (flags & 2) != 0;
    }
}

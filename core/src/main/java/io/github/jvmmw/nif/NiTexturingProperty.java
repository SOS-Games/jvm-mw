/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

public class NiTexturingProperty extends NifRecord {
    public int flags;
    public int applyMode;
    public final List<TextureSlot> textures = new ArrayList<>();

    public static final class TextureSlot {
        public boolean enabled;
        public int source = -1;
        public int clamp;
        public int filter;
        public int uvSet;

        public boolean wrapT() {
            return (clamp & 1) != 0;
        }

        public boolean wrapS() {
            return (clamp & 2) != 0;
        }
    }
}

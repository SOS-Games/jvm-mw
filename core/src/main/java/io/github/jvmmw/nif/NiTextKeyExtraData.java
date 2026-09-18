/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

/** Maps to {@code Nif::NiTextKeyExtraData}. */
public final class NiTextKeyExtraData extends NifRecord {
    public final List<Key> keys = new ArrayList<>();

    public static final class Key {
        public final float time;
        public final String text;

        public Key(float time, String text) {
            this.time = time;
            this.text = text;
        }
    }
}

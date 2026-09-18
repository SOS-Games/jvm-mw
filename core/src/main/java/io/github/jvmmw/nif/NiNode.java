/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

public class NiNode extends NiAvObject {
    public final List<Integer> children = new ArrayList<>();
    public final List<Integer> effects = new ArrayList<>();
    public boolean rootCollision;
}

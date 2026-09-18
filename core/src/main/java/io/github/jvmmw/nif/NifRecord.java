/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.nif;

import java.util.ArrayList;
import java.util.List;

public class NifRecord {
    public String recordName = "";
    public int recordIndex = -1;
    public String name = "";
    public int extra = -1;
    public int controller = -1;

    public boolean isNode() {
        return this instanceof NiNode;
    }
}

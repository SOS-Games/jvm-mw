/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

/** CLOT/ARMO {@code INDX}+BNAM/CNAM. Maps to {@code ESM::PartReference}. */
public final class EsmPartRef {
    public int part;
    public String male = "";
    public String female = "";
}

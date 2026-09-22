/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.List;

/**
 * One row of an NPC or creature’s AI list: wander, travel, follow, escort,
 * or activate, in the order the ESM stored them. The viewer still walks only
 * the first wander distance. Pressing E, or the actor command, prints the rest.
 */
public final class AiPackage {
    public enum Kind {
        WANDER, TRAVEL, FOLLOW, ESCORT, ACTIVATE
    }

    public Kind kind = Kind.WANDER;
    /** Wander radius. Negative ESM values are stored as 0. */
    public int distance;
    /** Hours. 0 does not end the package. Not applied yet. */
    public int duration;
    /** Stored. Not used to decide when they wander. */
    public int timeOfDay;
    /** Chances for idle2 through idle9. Not played yet. */
    public final int[] idle = new int[8];
    public boolean repeat;
    public float x;
    public float y;
    public float z;
    public String targetId = "";
    public String cellName = "";

    public static String format(String id, String name, List<AiPackage> packages) {
        StringBuilder sb = new StringBuilder();
        int n = packages == null ? 0 : packages.size();
        sb.append("packages n=").append(n)
            .append(" id=").append(id == null ? "" : id)
            .append(" name=").append(name == null ? "" : name)
            .append('\n');
        if (packages == null) {
            return sb.toString();
        }
        for (AiPackage p : packages) {
            switch (p.kind) {
                case WANDER -> sb.append("W dist=").append(p.distance)
                    .append(" dur=").append(p.duration)
                    .append(" hour=").append(p.timeOfDay)
                    .append(" idle=").append(p.idle[0]);
                case TRAVEL -> sb.append("T x=").append(p.x)
                    .append(" y=").append(p.y)
                    .append(" z=").append(p.z)
                    .append(" rep=").append(p.repeat ? 1 : 0)
                    .append('\n');
                case FOLLOW, ESCORT -> {
                    sb.append(p.kind == Kind.FOLLOW ? "F" : "E")
                        .append(" id=").append(p.targetId)
                        .append(" x=").append(p.x)
                        .append(" y=").append(p.y)
                        .append(" z=").append(p.z)
                        .append(" dur=").append(p.duration);
                    if (p.cellName != null && !p.cellName.isEmpty()) {
                        sb.append(" cell=").append(p.cellName);
                    }
                    sb.append(" rep=").append(p.repeat ? 1 : 0).append('\n');
                }
                case ACTIVATE -> sb.append("A id=").append(p.targetId)
                    .append(" rep=").append(p.repeat ? 1 : 0)
                    .append('\n');
            }
            if (p.kind == Kind.WANDER) {
                for (int i = 1; i < p.idle.length; i++) {
                    sb.append(',').append(p.idle[i]);
                }
                sb.append(" rep=").append(p.repeat ? 1 : 0).append('\n');
            }
        }
        return sb.toString();
    }
}

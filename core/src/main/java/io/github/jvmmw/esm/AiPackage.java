/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.esm;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of an NPC or creature’s AI list: wander, travel, follow, escort,
 * or activate, in the order the ESM stored them. Each placement copies that
 * list. The front row is the one that runs. A front wander walks, using that
 * row’s distance. A front travel walks to its point when that point is within
 * one cell (7168), then the package ends. Farther than that, they stay. Follow,
 * escort, or activate in front leaves them standing. A front wander with a
 * duration above 0 ends after that many Clear hours; 0 does not end. While they
 * stand, idle2 through idle9 can play. Finishing drops the front row; if it
 * repeats, a fresh copy goes on the back.
 * Pressing E, or the actor command, prints the active tag and the list.
 */
public final class AiPackage {
    public enum Kind {
        WANDER, TRAVEL, FOLLOW, ESCORT, ACTIVATE
    }

    /** A travel point farther than this from the actor is ignored. One cell. */
    public static final float TRAVEL_MAX = 7168f;

    public Kind kind = Kind.WANDER;
    /** Wander radius. Negative ESM values are stored as 0. */
    public int distance;
    /** Hours. 0 does not end the package. A front wander spends Clear hours. */
    public int duration;
    /** Stored. Not used to decide when they wander. */
    public int timeOfDay;
    /** Chances for idle2 through idle9. Rolled while a front wander is standing. */
    public final int[] idle = new int[8];
    public boolean repeat;
    public float x;
    public float y;
    public float z;
    public String targetId = "";
    public String cellName = "";

    /** A separate row with the same fields. Idle chances are copied, not shared. */
    public AiPackage copy() {
        AiPackage copy = new AiPackage();
        copy.kind = kind;
        copy.distance = distance;
        copy.duration = duration;
        copy.timeOfDay = timeOfDay;
        System.arraycopy(idle, 0, copy.idle, 0, idle.length);
        copy.repeat = repeat;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.targetId = targetId;
        copy.cellName = cellName;
        return copy;
    }

    /**
     * Drop the front row. A repeating package is copied onto the back first,
     * so a list of one repeating wander stays one wander.
     */
    public static void finishFront(List<AiPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        AiPackage front = packages.remove(0);
        if (front.repeat) {
            packages.add(front.copy());
        }
    }

    /** Headless check that finish drops a one-shot and requeues a repeat. Throws if it does not. */
    public static void checkFinishFront() {
        List<AiPackage> list = new ArrayList<>();
        AiPackage travel = new AiPackage();
        travel.kind = Kind.TRAVEL;
        travel.repeat = true;
        travel.x = 3f;
        AiPackage wander = new AiPackage();
        wander.kind = Kind.WANDER;
        wander.distance = 256;
        wander.repeat = true;
        list.add(travel);
        list.add(wander);
        finishFront(list);
        if (list.size() != 2 || list.get(0).kind != Kind.WANDER || list.get(0).distance != 256
            || list.get(1).kind != Kind.TRAVEL || list.get(1).x != 3f || list.get(1) == travel) {
            throw new IllegalStateException("finishFront repeat");
        }
        finishFront(list);
        if (list.size() != 2 || list.get(0).kind != Kind.TRAVEL || list.get(1).kind != Kind.WANDER
            || list.get(1).distance != 256) {
            throw new IllegalStateException("finishFront second");
        }
        list.get(0).repeat = false;
        finishFront(list);
        if (list.size() != 1 || list.get(0).kind != Kind.WANDER || list.get(0).distance != 256) {
            throw new IllegalStateException("finishFront drop");
        }
        list.get(0).repeat = false;
        finishFront(list);
        if (!list.isEmpty()) {
            throw new IllegalStateException("finishFront empty");
        }
        finishFront(list);
    }

    /**
     * Spend Clear hours on a wander. Duration 0 does not end.
     * Returns the hours still left on this package, or -1 when it ends.
     * unused[0] is the leftover hours to give the next package.
     */
    public static float spendWanderHours(int duration, float hoursLeft, float delta, float[] unused) {
        unused[0] = 0f;
        if (duration <= 0 || delta <= 0f) {
            return hoursLeft;
        }
        if (hoursLeft > delta) {
            return hoursLeft - delta;
        }
        unused[0] = delta - Math.max(0f, hoursLeft);
        return -1f;
    }

    /** Headless check: 0 never ends, and a 5 hour wander ends twice across a 12 hour jump. */
    public static void checkWanderHours() {
        float[] unused = new float[1];
        if (spendWanderHours(0, 0f, 100f, unused) < 0f || unused[0] != 0f) {
            throw new IllegalStateException("wander duration 0");
        }
        float left = spendWanderHours(5, 5f, 4f, unused);
        if (left < 0f || Math.abs(left - 1f) > 0.001f) {
            throw new IllegalStateException("wander duration partial");
        }
        if (spendWanderHours(5, left, 1f, unused) >= 0f || Math.abs(unused[0]) > 0.001f) {
            throw new IllegalStateException("wander duration end");
        }
        left = 5f;
        float jump = 12f;
        int ends = 0;
        while (jump > 0f && ends < 8) {
            float next = spendWanderHours(5, left, jump, unused);
            if (next >= 0f) {
                left = next;
                break;
            }
            ends++;
            left = 5f;
            jump = unused[0];
        }
        if (ends != 2 || Math.abs(left - 3f) > 0.001f) {
            throw new IllegalStateException("wander duration jump");
        }
    }

    /** True when the travel point is within 7168 of the actor, in 3D. */
    public static boolean travelInRange(float x, float y, float z, float ax, float ay, float az) {
        float dx = x - ax;
        float dy = y - ay;
        float dz = z - az;
        return dx * dx + dy * dy + dz * dz <= TRAVEL_MAX * TRAVEL_MAX;
    }

    /**
     * Headless check: exactly 7168 is in range, 7169 is not. Malsa Ules’s first
     * point is in range. Tanusea Veloth’s arena point is not.
     */
    public static void checkTravelRange() {
        if (!travelInRange(TRAVEL_MAX, 0f, 0f, 0f, 0f, 0f)) {
            throw new IllegalStateException("travel 7168");
        }
        if (travelInRange(TRAVEL_MAX + 1f, 0f, 0f, 0f, 0f, 0f)) {
            throw new IllegalStateException("travel 7169");
        }
        if (!travelInRange(1375f, 470f, -340f, 706f, 8f, -320f)) {
            throw new IllegalStateException("travel malsa");
        }
        if (travelInRange(29472f, -82112f, 3008f, 731f, 1033f, -85f)) {
            throw new IllegalStateException("travel tanusea");
        }
    }

    public static String format(String id, String name, List<AiPackage> packages) {
        StringBuilder sb = new StringBuilder();
        int n = packages == null ? 0 : packages.size();
        sb.append("active=").append(activeTag(packages)).append('\n');
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

    public static String activeTag(List<AiPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return "none";
        }
        return switch (packages.get(0).kind) {
            case WANDER -> "W";
            case TRAVEL -> "T";
            case FOLLOW -> "F";
            case ESCORT -> "E";
            case ACTIVATE -> "A";
        };
    }
}

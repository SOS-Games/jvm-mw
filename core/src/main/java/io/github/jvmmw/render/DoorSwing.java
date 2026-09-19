/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import io.github.jvmmw.esm.CellRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-teleport door open/close. Rewrite of {@code World::activateDoor} /
 * {@code rotateDoor} / {@code processDoors}.
 */
public final class DoorSwing {
    /** GMST {@code iMaxActivateDist}. */
    public static final float MAX_ACTIVATE = 192f;
    static final float RAD_PER_SEC = (float) Math.toRadians(90);

    public enum State {
        Idle,
        Opening,
        Closing
    }

    public static final class Placed {
        public final SceneNode node;
        public final String refId;
        public final boolean teleport;
        public final float[] pos = new float[3];
        public final float[] closedRot = new float[3];
        public final float[] liveRot = new float[3];
        public final float scale;
        public State state = State.Idle;

        Placed(SceneNode node, CellRef ref) {
            this.node = node;
            this.refId = ref.refId;
            this.teleport = ref.teleport;
            this.scale = ref.scale;
            System.arraycopy(ref.pos, 0, pos, 0, 3);
            System.arraycopy(ref.rot, 0, closedRot, 0, 3);
            System.arraycopy(ref.rot, 0, liveRot, 0, 3);
        }

        float minRot() {
            return closedRot[2];
        }

        float maxRot() {
            return closedRot[2] + RAD_PER_SEC;
        }
    }

    public final List<Placed> doors = new ArrayList<>();
    private final Ray ray = new Ray();
    private final BoundingBox box = new BoundingBox();
    private final Vector3 hit = new Vector3();

    public void clear() {
        doors.clear();
    }

    public Placed add(SceneNode node, CellRef ref) {
        Placed placed = new Placed(node, ref);
        doors.add(placed);
        return placed;
    }

    public int swingCount() {
        int n = 0;
        for (Placed d : doors) {
            if (!d.teleport) {
                n++;
            }
        }
        return n;
    }

    public int teleportCount() {
        return doors.size() - swingCount();
    }

    public void process(float duration) {
        for (Placed door : doors) {
            if (door.state != State.Idle) {
                if (rotateDoor(door, duration)) {
                    door.state = State.Idle;
                }
            }
        }
    }

    /**
     * Camera-center pick. Teleport doors are a no-op. Returns a log line, or
     * null if nothing was in range.
     */
    public String activate(Vector3 origin, Vector3 direction) {
        Placed door = pick(origin, direction);
        if (door == null) {
            return null;
        }
        if (door.teleport) {
            return "door teleport (no swing) " + door.refId;
        }
        activateDoor(door);
        return "door " + door.refId + " " + door.state;
    }

    void activateDoor(Placed door) {
        switch (door.state) {
            case Idle -> door.state = door.liveRot[2] == door.closedRot[2] ? State.Opening : State.Closing;
            case Closing -> door.state = State.Opening;
            default -> door.state = State.Closing;
        }
    }

    boolean rotateDoor(Placed door, float duration) {
        float minRot = door.minRot();
        float maxRot = door.maxRot();
        float diff = duration * RAD_PER_SEC * (door.state == State.Opening ? 1f : -1f);
        float targetRot = clamp(door.liveRot[2] + diff, minRot, maxRot);
        door.liveRot[2] = targetRot;
        EsmTransforms.setLocal(door.node.local, door.pos, door.liveRot, door.scale);
        return (targetRot == maxRot && door.state != State.Idle) || targetRot == minRot;
    }

    Placed pick(Vector3 origin, Vector3 direction) {
        ray.set(origin, direction);
        Placed best = null;
        float bestDist = MAX_ACTIVATE;
        for (Placed door : doors) {
            box.inf();
            door.node.collectAabb(box);
            if (!box.isValid()) {
                continue;
            }
            if (!Intersector.intersectRayBounds(ray, box, hit)) {
                continue;
            }
            float dist = origin.dst(hit);
            if (dist <= bestDist) {
                bestDist = dist;
                best = door;
            }
        }
        return best;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

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
import io.github.jvmmw.esm.LandRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-teleport door open/close and interior load-door teleport. Rewrite of
 * {@code World::activateDoor} / {@code rotateDoor} / {@code processDoors} /
 * {@code ActionTeleport}. Empty {@code DNAM} is an exterior grid from {@code DODT}.
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
        public final String destCell;
        public final float[] destPos = new float[3];
        public final float[] destRot = new float[3];
        public final float[] pos = new float[3];
        public final float[] closedRot = new float[3];
        public final float[] liveRot = new float[3];
        public final float scale;
        public State state = State.Idle;

        Placed(SceneNode node, CellRef ref) {
            this.node = node;
            this.refId = ref.refId;
            this.teleport = ref.teleport;
            this.destCell = ref.destCell;
            this.scale = ref.scale;
            System.arraycopy(ref.pos, 0, pos, 0, 3);
            System.arraycopy(ref.rot, 0, closedRot, 0, 3);
            System.arraycopy(ref.rot, 0, liveRot, 0, 3);
            System.arraycopy(ref.destPos, 0, destPos, 0, 3);
            System.arraycopy(ref.destRot, 0, destRot, 0, 3);
        }

        float minRot() {
            return closedRot[2];
        }

        float maxRot() {
            return closedRot[2] + RAD_PER_SEC;
        }
    }

    public static final class InteriorTeleport {
        public final String destCell;
        public final float[] destPos;
        public final float[] destRot;

        InteriorTeleport(String destCell, float[] destPos, float[] destRot) {
            this.destCell = destCell;
            this.destPos = destPos;
            this.destRot = destRot;
        }
    }

    public static final class Hit {
        public final Placed door;
        public final float dist;

        Hit(Placed door, float dist) {
            this.door = door;
            this.dist = dist;
        }
    }

    public final List<Placed> doors = new ArrayList<>();
    private InteriorTeleport pendingTeleport;
    private final Ray ray = new Ray();
    private final BoundingBox box = new BoundingBox();
    private final Vector3 hit = new Vector3();

    public void clear() {
        doors.clear();
        pendingTeleport = null;
    }

    public InteriorTeleport consumeInteriorTeleport() {
        InteriorTeleport t = pendingTeleport;
        pendingTeleport = null;
        return t;
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

    public Hit nearest(Vector3 origin, Vector3 direction) {
        ray.set(origin, direction);
        Hit best = null;
        for (Placed door : doors) {
            box.inf();
            door.node.collectAabb(box);
            if (!box.isValid() || !Intersector.intersectRayBounds(ray, box, hit)) {
                continue;
            }
            float dist = origin.dst(hit);
            if (dist <= MAX_ACTIVATE && (best == null || dist < best.dist)) {
                best = new Hit(door, dist);
            }
        }
        return best;
    }

    public String activate(Hit picked) {
        pendingTeleport = null;
        Placed door = picked.door;
        if (door.teleport) {
            if (door.destCell.isEmpty()) {
                pendingTeleport = new InteriorTeleport("", door.destPos, door.destRot);
                int gx = LandRecord.cellGrid(door.destPos[0]);
                int gy = LandRecord.cellGrid(door.destPos[1]);
                return "door teleport exterior " + door.refId + " -> (" + gx + "," + gy + ")";
            }
            pendingTeleport = new InteriorTeleport(door.destCell, door.destPos, door.destRot);
            return "door teleport " + door.refId + " -> " + door.destCell;
        }
        activateDoor(door);
        return "door " + door.refId + " " + door.state;
    }

    /**
     * Camera-center pick. Empty-{@code DNAM} teleport doors queue an exterior
     * load ({@link #consumeInteriorTeleport()} with empty dest name). Named dest
     * queues an interior load. Returns a log line, or null if nothing was in range.
     */
    public String activate(Vector3 origin, Vector3 direction) {
        Hit picked = nearest(origin, direction);
        if (picked == null) {
            return null;
        }
        return activate(picked);
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
        Hit h = nearest(origin, direction);
        return h == null ? null : h.door;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

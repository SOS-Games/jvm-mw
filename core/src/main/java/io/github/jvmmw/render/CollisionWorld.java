package io.github.jvmmw.render;

import io.github.jvmmw.esm.LandRecord;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Player walk: a capsule on land and against object triangles. No Bullet.
 * WASD slides on walls, steps onto docks, and sticks to the floor. Ceilings
 * and dock undersides stop the camera from embedding. NPCs are ignored.
 */
public final class CollisionWorld {
    public static final float EYE_HEIGHT = 96f;
    public static final float HEIGHT = 128f;
    public static final float RADIUS = 30f;
    public static final float STEP_UP = 34f;
    public static final float STEP_DOWN = 62f;
    public static final float GROUND_OFFSET = 1f;
    public static final float GRAVITY = 627f;
    public static final float MARGIN = 0.2f;
    private static final float MAX_SLOPE_COS = (float) Math.cos(Math.toRadians(46));

    public boolean onGround;
    public float floorY = Float.NaN;
    public float ceilY = Float.NaN;

    private final List<LandRecord> lands = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private float vy;

    private final Vector3 a = new Vector3();
    private final Vector3 b = new Vector3();
    private final Vector3 c = new Vector3();
    private final Vector3 p = new Vector3();
    private final Vector3 q = new Vector3();
    private final Vector3 n = new Vector3();
    private final Vector3 closest = new Vector3();
    private final Vector3 capA = new Vector3();
    private final Vector3 capB = new Vector3();
    private final Vector3 tmpA = new Vector3();
    private final Vector3 tmpB = new Vector3();
    private final Vector3 tmpC = new Vector3();
    private final Vector3 tmpD = new Vector3();
    private final BoundingBox query = new BoundingBox();
    private final float[] slid = new float[3];
    private final Vector3 d1 = new Vector3();
    private final Vector3 d2 = new Vector3();
    private final Vector3 r = new Vector3();
    private final Hit best = new Hit();

    public void clear() {
        lands.clear();
        chunks.clear();
        onGround = false;
        floorY = Float.NaN;
        ceilY = Float.NaN;
        vy = 0f;
    }

    public void bake(List<LandRecord> land, List<Pending> pending) {
        clear();
        if (land != null) {
            lands.addAll(land);
        }
        Matrix4 world = new Matrix4();
        Vector3 v = new Vector3();
        for (Pending pnd : pending) {
            if (pnd.mesh.isEmpty() || pnd.node == null) {
                continue;
            }
            world.set(pnd.node.world);
            float[] src = pnd.mesh.tris;
            float[] dst = new float[src.length];
            Chunk chunk = new Chunk();
            chunk.aabb.inf();
            for (int i = 0; i + 2 < src.length; i += 3) {
                v.set(src[i], src[i + 1], src[i + 2]).mul(world);
                dst[i] = v.x;
                dst[i + 1] = v.y;
                dst[i + 2] = v.z;
                chunk.aabb.ext(v);
            }
            chunk.tris = dst;
            if (chunk.aabb.isValid()) {
                chunk.aabb.min.add(-1f, -1f, -1f);
                chunk.aabb.max.add(1f, 1f, 1f);
                chunks.add(chunk);
            }
        }
    }

    public void snapSpawn(Vector3 eye) {
        vy = 0f;
        float feet = eye.y - EYE_HEIGHT;
        Hit down = traceDown(eye.x, feet + 256f, eye.z, 512f);
        if (down.ok && down.walkable) {
            feet = down.y + GROUND_OFFSET;
        }
        Hit ceil = traceUp(eye.x, feet + HEIGHT, eye.z, 8f);
        if (ceil.ok && feet + HEIGHT > ceil.y - MARGIN) {
            feet = ceil.y - HEIGHT - MARGIN;
        }
        eye.y = feet + EYE_HEIGHT;
        refreshDebug(eye.x, feet, eye.z);
    }

    public void move(Vector3 eye, float dx, float dy, float dz, float dt) {
        float x = eye.x;
        float z = eye.z;
        float feet = eye.y - EYE_HEIGHT;
        float horiz = (float) Math.hypot(dx, dz);
        int parts = Math.max(1, (int) (horiz / 16f) + 1);
        float sdx = dx / parts;
        float sdz = dz / parts;
        for (int i = 0; i < parts; i++) {
            slide(x, feet, z, sdx, sdz);
            x = slid[0];
            feet = slid[1];
            z = slid[2];
        }
        if (dy != 0f) {
            feet = sweepY(x, feet, z, dy);
        }
        Hit down = traceDown(x, feet + 2f, z, STEP_DOWN + 4f);
        boolean floorHere = down.ok && down.walkable && down.y <= feet + 2f
            && feet - down.y <= STEP_DOWN;
        if (floorHere && dy <= 0f) {
            feet = down.y + GROUND_OFFSET;
            onGround = true;
            vy = 0f;
        } else {
            onGround = false;
            if (dy <= 0f) {
                vy -= GRAVITY * dt;
                if (vy > 0f) {
                    vy = 0f;
                }
                feet = sweepY(x, feet, z, vy * dt);
                Hit land = traceDown(x, feet + 2f, z, STEP_DOWN + 4f);
                if (land.ok && land.walkable && feet - land.y <= GROUND_OFFSET + 4f) {
                    feet = land.y + GROUND_OFFSET;
                    onGround = true;
                    vy = 0f;
                }
            } else {
                vy = 0f;
            }
        }
        Hit ceil = traceUp(x, feet + HEIGHT - 2f, z, STEP_UP + 8f);
        if (ceil.ok && feet + HEIGHT > ceil.y - MARGIN) {
            feet = ceil.y - HEIGHT - MARGIN;
            if (vy > 0f) {
                vy = 0f;
            }
        }
        depenetrate(x, feet, z);
        x = hitX;
        feet = hitY;
        z = hitZ;
        eye.set(x, feet + EYE_HEIGHT, z);
        refreshDebug(x, feet, z);
    }

    private float hitX;
    private float hitY;
    private float hitZ;

    private void slide(float x, float feet, float z, float dx, float dz) {
        if (dx == 0f && dz == 0f) {
            setSlid(x, feet, z);
            return;
        }
        float nx = x + dx;
        float nz = z + dz;
        float landAt = landHeight(nx, nz);
        if (!Float.isNaN(landAt) && landAt > feet + STEP_UP + GROUND_OFFSET + 4f) {
            nx = x;
            nz = z;
        } else if (fits(nx, feet, nz)) {
            setSlid(nx, feet, nz);
            return;
        }
        float up = STEP_UP;
        Hit head = traceUp(x, feet + HEIGHT, z, STEP_UP);
        if (head.ok) {
            up = Math.max(0f, head.y - (feet + HEIGHT) - MARGIN);
        }
        if (up > MARGIN) {
            float raised = feet + up;
            float lx = x + dx;
            float lz = z + dz;
            if (fits(lx, raised, lz)) {
                Hit floor = traceDown(lx, raised + 2f, lz, up + STEP_DOWN);
                if (floor.ok && floor.walkable) {
                    setSlid(lx, floor.y + GROUND_OFFSET, lz);
                    return;
                }
                setSlid(lx, raised, lz);
                return;
            }
        }
        if (!deepest(x + dx, feet, z + dz)) {
            setSlid(x + dx, feet, z + dz);
            return;
        }
        float px = best.nx;
        float pz = best.nz;
        float plen = (float) Math.hypot(px, pz);
        if (plen < 1e-4f) {
            setSlid(x, feet, z);
            return;
        }
        px /= plen;
        pz /= plen;
        float keep = dx * px + dz * pz;
        if (keep > 0f) {
            dx -= px * keep;
            dz -= pz * keep;
        }
        nx = x + dx;
        nz = z + dz;
        if (fits(nx, feet, nz)) {
            setSlid(nx, feet, nz);
            return;
        }
        setSlid(x, feet, z);
    }

    private void setSlid(float x, float feet, float z) {
        slid[0] = x;
        slid[1] = feet;
        slid[2] = z;
    }

    private float sweepY(float x, float feet, float z, float dy) {
        if (dy == 0f) {
            return feet;
        }
        if (dy > 0f) {
            Hit ceil = traceUp(x, feet + HEIGHT, z, Math.abs(dy) + 4f);
            if (ceil.ok) {
                return Math.min(feet + dy, ceil.y - HEIGHT - MARGIN);
            }
            return feet + dy;
        }
        Hit down = traceDown(x, feet + 2f, z, Math.abs(dy) + 4f);
        if (down.ok && down.walkable) {
            return Math.max(feet + dy, down.y + GROUND_OFFSET);
        }
        if (fits(x, feet + dy, z)) {
            return feet + dy;
        }
        return feet;
    }

    private float zOfLast;

    private boolean fits(float x, float feet, float z) {
        zOfLast = z;
        if (!deepest(x, feet, z)) {
            return true;
        }
        if (best.walkable || best.ny < -0.5f) {
            return true;
        }
        return best.depth < MARGIN * 4f;
    }

    private void depenetrate(float x, float feet, float z) {
        hitX = x;
        hitY = feet;
        hitZ = z;
        for (int i = 0; i < 8; i++) {
            if (!deepest(hitX, hitY, hitZ) || (best.walkable && best.depth < RADIUS * 0.25f)) {
                return;
            }
            if (best.walkable) {
                hitY = Math.max(hitY, best.y + GROUND_OFFSET);
                continue;
            }
            hitX += best.nx * (best.depth + MARGIN);
            hitY += best.ny * (best.depth + MARGIN) * (best.ny < -0.2f || best.ny > MAX_SLOPE_COS ? 1f : 0f);
            hitZ += best.nz * (best.depth + MARGIN);
        }
    }

    private void refreshDebug(float x, float feet, float z) {
        Hit down = traceDown(x, feet + 2f, z, STEP_DOWN + 8f);
        floorY = down.ok ? down.y : Float.NaN;
        onGround = down.ok && down.walkable && feet - down.y <= STEP_DOWN;
        Hit ceil = traceUp(x, feet + HEIGHT - 2f, z, 256f);
        ceilY = ceil.ok ? ceil.y : Float.NaN;
    }

    private Hit traceDown(float x, float fromY, float z, float maxDist) {
        best.clear();
        float land = landHeight(x, z);
        if (!Float.isNaN(land) && fromY - land <= maxDist && land <= fromY + 2f) {
            best.ok = true;
            best.y = land;
            best.walkable = landWalkable(x, z);
            best.ny = 1f;
        }
        query.min.set(x - RADIUS - 4f, fromY - maxDist - RADIUS, z - RADIUS - 4f);
        query.max.set(x + RADIUS + 4f, fromY + RADIUS + 4f, z + RADIUS + 4f);
        capA.set(x, fromY - maxDist + RADIUS, z);
        capB.set(x, fromY + RADIUS, z);
        collectHits(1);
        return best;
    }

    private Hit traceUp(float x, float fromY, float z, float maxDist) {
        best.clear();
        query.min.set(x - RADIUS - 4f, fromY - RADIUS, z - RADIUS - 4f);
        query.max.set(x + RADIUS + 4f, fromY + maxDist + RADIUS, z + RADIUS + 4f);
        capA.set(x, fromY - RADIUS, z);
        capB.set(x, fromY + maxDist - RADIUS, z);
        collectHits(2);
        return best;
    }

    private boolean deepest(float x, float feet, float z) {
        zOfLast = z;
        best.clear();
        query.min.set(x - RADIUS - 4f, feet - 4f, z - RADIUS - 4f);
        query.max.set(x + RADIUS + 4f, feet + HEIGHT + 4f, z + RADIUS + 4f);
        capA.set(x, feet + RADIUS, z);
        capB.set(x, feet + HEIGHT - RADIUS, z);
        collectHits(0);
        float land = landHeight(x, z);
        if (!Float.isNaN(land) && feet + 2f < land) {
            float depth = land - feet;
            if (!best.ok || depth > best.depth) {
                best.ok = true;
                best.depth = depth;
                best.y = land;
                best.nx = 0f;
                best.ny = 1f;
                best.nz = 0f;
                best.walkable = landWalkable(x, z);
            }
        }
        return best.ok;
    }

    private void collectHits(int mode) {
        for (Chunk chunk : chunks) {
            if (!overlaps(chunk.aabb, query)) {
                continue;
            }
            float[] t = chunk.tris;
            for (int i = 0; i + 8 < t.length; i += 9) {
                a.set(t[i], t[i + 1], t[i + 2]);
                b.set(t[i + 3], t[i + 4], t[i + 5]);
                c.set(t[i + 6], t[i + 7], t[i + 8]);
                if (!triAabbHits()) {
                    continue;
                }
                considerTri(mode);
            }
        }
    }

    private boolean triAabbHits() {
        float minx = Math.min(a.x, Math.min(b.x, c.x));
        float miny = Math.min(a.y, Math.min(b.y, c.y));
        float minz = Math.min(a.z, Math.min(b.z, c.z));
        float maxx = Math.max(a.x, Math.max(b.x, c.x));
        float maxy = Math.max(a.y, Math.max(b.y, c.y));
        float maxz = Math.max(a.z, Math.max(b.z, c.z));
        return maxx >= query.min.x && minx <= query.max.x
            && maxy >= query.min.y && miny <= query.max.y
            && maxz >= query.min.z && minz <= query.max.z;
    }

    private void considerTri(int mode) {
        float dist = capsuleTri();
        if (dist >= RADIUS - MARGIN) {
            return;
        }
        n.set(p).sub(closest);
        if (n.len2() < 1e-8f) {
            tmpA.set(b).sub(a);
            tmpB.set(c).sub(a);
            n.set(tmpA).crs(tmpB);
        }
        if (n.len2() < 1e-8f) {
            return;
        }
        n.nor();
        if (n.dot(tmpC.set(capA).sub(closest)) < 0f && n.dot(tmpD.set(capB).sub(closest)) < 0f) {
            n.scl(-1f);
        }
        float depth = RADIUS - dist;
        boolean walk = n.y > MAX_SLOPE_COS;
        float contactY = closest.y;
        if (mode == 1) {
            if (walk && (!best.ok || !best.walkable || contactY > best.y)) {
                store(depth, walk, contactY);
            }
            return;
        }
        if (mode == 2) {
            if (n.y < MAX_SLOPE_COS && (!best.ok || contactY < best.y)) {
                store(depth, walk, contactY);
            }
            return;
        }
        if (!best.ok || depth > best.depth) {
            store(depth, walk, contactY);
        }
    }

    private void store(float depth, boolean walk, float contactY) {
        best.ok = true;
        best.depth = depth;
        best.walkable = walk;
        best.y = contactY;
        best.nx = n.x;
        best.ny = n.y;
        best.nz = n.z;
    }

    private float capBest;

    private float capsuleTri() {
        closestOnTri(capA, closest);
        capBest = closest.dst(capA);
        p.set(capA);
        closestOnTri(capB, q);
        float db = q.dst(capB);
        if (db < capBest) {
            capBest = db;
            closest.set(q);
            p.set(capB);
        }
        edgeSeg(a, b);
        edgeSeg(b, c);
        edgeSeg(c, a);
        return capBest;
    }

    private void edgeSeg(Vector3 e0, Vector3 e1) {
        closestSegs(e0, e1, capA, capB, tmpA, tmpB);
        float d = tmpA.dst(tmpB);
        if (d < capBest) {
            capBest = d;
            closest.set(tmpA);
            p.set(tmpB);
        }
    }

    private void closestOnTri(Vector3 point, Vector3 out) {
        tmpA.set(b).sub(a);
        tmpB.set(c).sub(a);
        tmpC.set(point).sub(a);
        float d1 = tmpA.dot(tmpC);
        float d2 = tmpB.dot(tmpC);
        if (d1 <= 0f && d2 <= 0f) {
            out.set(a);
            return;
        }
        tmpC.set(point).sub(b);
        float d3 = tmpA.dot(tmpC);
        float d4 = tmpB.dot(tmpC);
        if (d3 >= 0f && d4 <= d3) {
            out.set(b);
            return;
        }
        float vc = d1 * d4 - d3 * d2;
        if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
            float v = d1 / (d1 - d3);
            out.set(a).mulAdd(tmpA, v);
            return;
        }
        tmpC.set(point).sub(c);
        float d5 = tmpA.dot(tmpC);
        float d6 = tmpB.dot(tmpC);
        if (d6 >= 0f && d5 <= d6) {
            out.set(c);
            return;
        }
        float vb = d5 * d2 - d1 * d6;
        if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
            float w = d2 / (d2 - d6);
            out.set(a).mulAdd(tmpB, w);
            return;
        }
        float va = d3 * d6 - d5 * d4;
        if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
            float w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            out.set(b).mulAdd(tmpD.set(c).sub(b), w);
            return;
        }
        float denom = 1f / (va + vb + vc);
        out.set(a).mulAdd(tmpA, vb * denom).mulAdd(tmpB, vc * denom);
    }

    private void closestSegs(Vector3 p1, Vector3 q1, Vector3 p2, Vector3 q2, Vector3 c1, Vector3 c2) {
        d1.set(q1).sub(p1);
        d2.set(q2).sub(p2);
        r.set(p1).sub(p2);
        float aa = d1.len2();
        float ee = d2.len2();
        float f = d2.dot(r);
        float s;
        float t;
        if (aa <= 1e-8f && ee <= 1e-8f) {
            c1.set(p1);
            c2.set(p2);
            return;
        }
        if (aa <= 1e-8f) {
            s = 0f;
            t = clamp01(f / ee);
        } else {
            float cc = d1.dot(r);
            if (ee <= 1e-8f) {
                t = 0f;
                s = clamp01(-cc / aa);
            } else {
                float bb = d1.dot(d2);
                float denom = aa * ee - bb * bb;
                s = denom != 0f ? clamp01((bb * f - cc * ee) / denom) : 0f;
                t = (bb * s + f) / ee;
                if (t < 0f) {
                    t = 0f;
                    s = clamp01(-cc / aa);
                } else if (t > 1f) {
                    t = 1f;
                    s = clamp01((bb - cc) / aa);
                }
            }
        }
        c1.set(p1).mulAdd(d1, s);
        c2.set(p2).mulAdd(d2, t);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    private static boolean overlaps(BoundingBox box, BoundingBox q) {
        return box.max.x >= q.min.x && box.min.x <= q.max.x
            && box.max.y >= q.min.y && box.min.y <= q.max.y
            && box.max.z >= q.min.z && box.min.z <= q.max.z;
    }

    float landHeight(float glX, float glZ) {
        float tesX = glX;
        float tesY = -glZ;
        int gx = LandRecord.cellGrid(tesX);
        int gy = LandRecord.cellGrid(tesY);
        LandRecord land = null;
        for (LandRecord l : lands) {
            if (l.gridX == gx && l.gridY == gy) {
                land = l;
                break;
            }
        }
        if (land == null) {
            return Float.NaN;
        }
        float lx = tesX - gx * (float) LandRecord.CELL_SIZE;
        float ly = tesY - gy * (float) LandRecord.CELL_SIZE;
        float step = LandRecord.CELL_SIZE / (float) (LandRecord.SIZE - 1);
        float fx = lx / step;
        float fy = ly / step;
        int x0 = clamp((int) Math.floor(fx), 0, LandRecord.SIZE - 2);
        int y0 = clamp((int) Math.floor(fy), 0, LandRecord.SIZE - 2);
        float tx = fx - x0;
        float ty = fy - y0;
        float h00 = land.height(x0, y0);
        float h10 = land.height(x0 + 1, y0);
        float h01 = land.height(x0, y0 + 1);
        float h11 = land.height(x0 + 1, y0 + 1);
        float h0 = h00 + (h10 - h00) * tx;
        float h1 = h01 + (h11 - h01) * tx;
        return h0 + (h1 - h0) * ty;
    }

    private boolean landWalkable(float glX, float glZ) {
        float tesX = glX;
        float tesY = -glZ;
        int gx = LandRecord.cellGrid(tesX);
        int gy = LandRecord.cellGrid(tesY);
        LandRecord land = null;
        for (LandRecord l : lands) {
            if (l.gridX == gx && l.gridY == gy) {
                land = l;
                break;
            }
        }
        if (land == null) {
            return false;
        }
        float lx = tesX - gx * (float) LandRecord.CELL_SIZE;
        float ly = tesY - gy * (float) LandRecord.CELL_SIZE;
        float step = LandRecord.CELL_SIZE / (float) (LandRecord.SIZE - 1);
        int x = clamp(Math.round(lx / step), 1, LandRecord.SIZE - 2);
        int y = clamp(Math.round(ly / step), 1, LandRecord.SIZE - 2);
        float dx = land.height(x + 1, y) - land.height(x - 1, y);
        float dy = land.height(x, y + 1) - land.height(x, y - 1);
        float nx = -dx;
        float ny = -dy;
        float nz = step * 2f;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len > 1e-5f && nz / len > MAX_SLOPE_COS;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static final class Pending {
        final CollisionMesh mesh;
        final SceneNode node;

        public Pending(CollisionMesh mesh, SceneNode node) {
            this.mesh = mesh;
            this.node = node;
        }
    }

    private static final class Chunk {
        final BoundingBox aabb = new BoundingBox();
        float[] tris = new float[0];
    }

    private static final class Hit {
        boolean ok;
        boolean walkable;
        float depth;
        float y;
        float nx;
        float ny = 1f;
        float nz;

        void clear() {
            ok = false;
            walkable = false;
            depth = 0f;
            y = 0f;
            nx = 0f;
            ny = 1f;
            nz = 0f;
        }
    }
}

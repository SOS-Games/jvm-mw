package io.github.jvmmw.render;

import io.github.jvmmw.esm.LandRecord;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.ClosestConvexResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.ContactResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObjectWrapper;
import com.badlogic.gdx.physics.bullet.collision.btCollisionWorld;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btManifoldPoint;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JNI Bullet collision world for the loaded cell. Land is one mesh per TES
 * tile; docks, trees, and kit are world-space triangles at load pose, same
 * as F7. WASD sweeps a capsule (not a rigid body) against World and
 * HeightMap. NPC feet snap with a down hit on the same world. Chair HUD has
 * no world.
 */
public final class BulletWorld {
    static final int WORLD = 1;
    static final int HEIGHT_MAP = 1 << 3;
    static final int ACTOR = 1 << 2;
    static final int PROJECTILE = 1 << 4;

    public static final float EYE_HEIGHT = 96f;
    public static final float HEIGHT = 128f;
    private static final float HALF_H = HEIGHT * 0.5f;
    public static final float RADIUS = 30f;
    public static final float STEP_UP = 34f;
    public static final float STEP_DOWN = 62f;
    public static final float GROUND_OFFSET = 1f;
    public static final float GRAVITY = 627f;
    public static final float MARGIN = 0.2f;
    private static final float MAX_SLOPE_COS = (float) Math.cos(Math.toRadians(46));
    private static final int PLAYER_MASK = WORLD | HEIGHT_MAP;

    private static final float STEP = LandRecord.CELL_SIZE / (float) (LandRecord.SIZE - 1);
    private static final float RAY_DOWN = 8192f;

    private static boolean natives;
    private static btDefaultCollisionConfiguration config;
    private static btCollisionDispatcher dispatcher;
    private static btDbvtBroadphase broadphase;
    private static btCollisionWorld world;
    private static int landBodies;
    private static int worldBodies;
    private static final List<btCollisionObject> bodies = new ArrayList<>();
    private static final List<btBvhTriangleMeshShape> landShapes = new ArrayList<>();
    private static final List<btTriangleMesh> landMeshes = new ArrayList<>();
    private static final List<btBvhTriangleMeshShape> objectShapes = new ArrayList<>();
    private static final List<btTriangleMesh> objectMeshes = new ArrayList<>();
    private static final Set<Long> readyCells = new HashSet<>();
    private static boolean interiorReady;
    private static final Vector3 hit = new Vector3();
    private static final Vector3 rayFrom = new Vector3();
    private static final Vector3 rayTo = new Vector3();
    private static final Vector3 va = new Vector3();
    private static final Vector3 vb = new Vector3();
    private static final Vector3 vc = new Vector3();
    private static final Vector3 vd = new Vector3();
    private static final Vector3 from = new Vector3();
    private static final Vector3 to = new Vector3();
    private static final Vector3 n = new Vector3();
    private static final Matrix4 tmpMat = new Matrix4();
    private static final Matrix4 fromMat = new Matrix4();
    private static final Matrix4 toMat = new Matrix4();
    private static final float[] slid = new float[3];
    private static final Hit best = new Hit();

    private static btCapsuleShape capsule;
    private static btCollisionObject probe;
    private static ClosestConvexResultCallback sweepCb;
    private static DeepestContact contactCb;
    private static ClosestRayResultCallback rayCb;

    public static boolean onGround;
    public static float floorY = Float.NaN;
    public static float ceilY = Float.NaN;
    private static float vy;

    private BulletWorld() {
    }

    /** Load natives once. Viewer only — not the headless CLI. */
    public static void initNatives() {
        if (natives) {
            return;
        }
        Bullet.init();
        natives = true;
        float cyl = HEIGHT - 2f * RADIUS;
        capsule = new btCapsuleShape(RADIUS, Math.max(0.01f, cyl));
        probe = new btCollisionObject();
        probe.setCollisionShape(capsule);
        probe.setCollisionFlags(btCollisionObject.CollisionFlags.CF_NO_CONTACT_RESPONSE);
        sweepCb = new ClosestConvexResultCallback(from, to);
        sweepCb.setCollisionFilterGroup(ACTOR);
        sweepCb.setCollisionFilterMask(PLAYER_MASK);
        contactCb = new DeepestContact();
        contactCb.setCollisionFilterGroup(ACTOR);
        contactCb.setCollisionFilterMask(PLAYER_MASK);
        rayCb = new ClosestRayResultCallback(rayFrom, rayTo);
        rayCb.setCollisionFilterGroup(ACTOR);
    }

    /** Same moments as a cell graph swap: interior load and walk-grid swap. */
    public static void rebuild() {
        if (!natives) {
            return;
        }
        disposeWorld();
        config = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(config);
        broadphase = new btDbvtBroadphase();
        world = new btCollisionWorld(dispatcher, broadphase, config);
        world.setForceUpdateAllAabbs(false);
        onGround = false;
        floorY = Float.NaN;
        ceilY = Float.NaN;
        vy = 0f;
        readyCells.clear();
        interiorReady = false;
    }

    /** One HeightMap body per loaded TES land tile. Interior passes an empty list. */
    public static void addLand(List<LandRecord> lands) {
        if (world == null || lands == null || lands.isEmpty()) {
            return;
        }
        for (LandRecord land : lands) {
            addLandMesh(land);
        }
    }

    /** One World body. Empty NC meshes are skipped. */
    public static void addObject(CollisionMesh.Pending pnd) {
        if (world == null || pnd == null || pnd.mesh == null || pnd.mesh.isEmpty() || pnd.node == null) {
            return;
        }
        addObjectMesh(pnd);
    }

    /** One World body per pending placement. Empty NC meshes are skipped. */
    public static void addObjects(List<CollisionMesh.Pending> pending) {
        if (world == null || pending == null) {
            return;
        }
        for (CollisionMesh.Pending pnd : pending) {
            if (pnd.mesh == null || pnd.mesh.isEmpty() || pnd.node == null) {
                continue;
            }
            addObjectMesh(pnd);
        }
    }

    public static void disposeWorld() {
        disposeBodies();
        if (world != null) {
            world.dispose();
            world = null;
        }
        if (dispatcher != null) {
            dispatcher.dispose();
            dispatcher = null;
        }
        if (broadphase != null) {
            broadphase.dispose();
            broadphase = null;
        }
        if (config != null) {
            config.dispose();
            config = null;
        }
    }

    /** Player capsule. Viewer shutdown only. */
    public static void disposeInterned() {
        if (sweepCb != null) {
            sweepCb.dispose();
            sweepCb = null;
        }
        if (contactCb != null) {
            contactCb.dispose();
            contactCb = null;
        }
        if (rayCb != null) {
            rayCb.dispose();
            rayCb = null;
        }
        if (probe != null) {
            probe.dispose();
            probe = null;
        }
        if (capsule != null) {
            capsule.dispose();
            capsule = null;
        }
    }

    public static boolean alive() {
        return world != null;
    }

    public static int bodyCount() {
        return world == null ? 0 : world.getNumCollisionObjects();
    }

    public static int landCount() {
        return landBodies;
    }

    public static int worldCount() {
        return worldBodies;
    }

    /** HeightMap hit Y under the camera, or NaN. */
    public static float floorY(float glX, float glY, float glZ) {
        return rayDown(glX, glY, glZ, HEIGHT_MAP);
    }

    /** World + HeightMap hit Y under the camera, or NaN. */
    public static float hitY(float glX, float glY, float glZ) {
        return rayDown(glX, glY, glZ, PLAYER_MASK);
    }

    /** Land or kit bodies for the current cell. Empty during Chair and while a cell is still placing. */
    public static boolean hasPhysics() {
        return world != null && !bodies.isEmpty();
    }

    public static void markReady(int gridX, int gridY) {
        readyCells.add(gridKey(gridX, gridY));
    }

    public static void markInteriorReady() {
        interiorReady = true;
    }

    /** TES cell under this point has HeightMap / kit in Bullet. */
    public static boolean readyAt(float tesX, float tesY) {
        if (world == null) {
            return false;
        }
        if (interiorReady) {
            return true;
        }
        return readyCells.contains(gridKey(LandRecord.cellGrid(tesX), LandRecord.cellGrid(tesY)));
    }

    public static boolean readyAtGl(float glX, float glZ) {
        return readyAt(glX, -glZ);
    }

    private static long gridKey(int gx, int gy) {
        return ((long) gx << 32) ^ (gy & 0xffffffffL);
    }

    public static Staged cookLand(LandRecord land) {
        if (land == null) {
            return null;
        }
        return cookLandMesh(land);
    }

    public static Staged cookObject(CollisionMesh.Pending pnd) {
        if (pnd == null || pnd.mesh == null || pnd.mesh.isEmpty() || pnd.node == null) {
            return null;
        }
        return cookObjectMesh(pnd);
    }

    public static void adopt(Staged staged) {
        if (world == null || staged == null) {
            return;
        }
        world.addCollisionObject(staged.obj, staged.group, ACTOR | PROJECTILE);
        world.updateSingleAabb(staged.obj);
        bodies.add(staged.obj);
        if (staged.land) {
            landShapes.add(staged.shape);
            landMeshes.add(staged.mesh);
            landBodies++;
        } else {
            objectShapes.add(staged.shape);
            objectMeshes.add(staged.mesh);
            worldBodies++;
        }
    }

    public static void disposeStaged(Staged staged) {
        if (staged == null) {
            return;
        }
        staged.obj.dispose();
        staged.shape.dispose();
        staged.mesh.dispose();
    }

    public static final class Staged {
        final btCollisionObject obj;
        final btBvhTriangleMeshShape shape;
        final btTriangleMesh mesh;
        final int group;
        final boolean land;

        Staged(btCollisionObject obj, btBvhTriangleMeshShape shape, btTriangleMesh mesh, int group, boolean land) {
            this.obj = obj;
            this.shape = shape;
            this.mesh = mesh;
            this.group = group;
            this.land = land;
        }
    }

    public static void snapSpawn(Vector3 eye, boolean interior) {
        if (!hasPhysics()) {
            return;
        }
        vy = 0f;
        float origin = eye.y - EYE_HEIGHT;
        float feet = origin;
        if (interior) {
            sweepDown(eye.x, origin + 8f, eye.z, STEP_DOWN + 96f);
            if (best.ok && best.walkable && best.y <= origin + 8f) {
                feet = best.y + GROUND_OFFSET;
            }
        } else {
            sweepDown(eye.x, origin + 256f, eye.z, 512f);
            if (best.ok && best.walkable) {
                feet = best.y + GROUND_OFFSET;
            }
        }
        sweepUp(eye.x, feet, eye.z, 8f);
        if (best.ok && feet + HEIGHT > best.y - MARGIN) {
            feet = best.y - HEIGHT - MARGIN;
        }
        eye.y = feet + EYE_HEIGHT;
        refreshDebug(eye.x, feet, eye.z);
    }

    /**
     * Player camera walk: split WASD, slide, step onto docks, stick to a
     * walkable floor, fall if there is none. Space and look-dolly stop at
     * roofs and dock undersides. The capsule is not added as a body.
     */
    public static void move(Vector3 eye, float dx, float dy, float dz, float dt) {
        if (!hasPhysics()) {
            vy = 0f;
            return;
        }
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
        sweepDown(x, feet + 2f, z, STEP_DOWN + 4f);
        boolean floorHere = best.ok && best.walkable && best.y <= feet + 2f
            && feet - best.y <= STEP_DOWN;
        if (floorHere && dy <= 0f) {
            feet = best.y + GROUND_OFFSET;
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
                sweepDown(x, feet + 2f, z, STEP_DOWN + 4f);
                if (best.ok && best.walkable && feet - best.y <= GROUND_OFFSET + 4f) {
                    feet = best.y + GROUND_OFFSET;
                    onGround = true;
                    vy = 0f;
                }
            } else {
                vy = 0f;
            }
        }
        sweepUp(x, feet, z, STEP_UP + 8f);
        if (best.ok && feet + HEIGHT > best.y - MARGIN) {
            feet = best.y - HEIGHT - MARGIN;
            if (vy > 0f) {
                vy = 0f;
            }
        }
        depenetrate(x, feet, z);
        x = best.px;
        feet = best.py;
        z = best.pz;
        eye.set(x, feet + EYE_HEIGHT, z);
        refreshDebug(x, feet, z);
    }

    private static void slide(float x, float feet, float z, float dx, float dz) {
        if (dx == 0f && dz == 0f) {
            setSlid(x, feet, z);
            return;
        }
        float landAt = rayDown(x + dx, feet + 512f, z + dz, HEIGHT_MAP);
        if (!Float.isNaN(landAt) && landAt > feet + STEP_UP + GROUND_OFFSET + 4f) {
            setSlid(x, feet, z);
            return;
        }
        sweepPose(x, feet, z, x + dx, feet, z + dz);
        if (!best.ok) {
            setSlid(x + dx, feet, z + dz);
            return;
        }
        float hitFrac = best.fraction;
        float hitNx = best.nx;
        float hitNy = best.ny;
        float hitNz = best.nz;
        float up = STEP_UP;
        sweepUp(x, feet, z, STEP_UP);
        if (best.ok) {
            up = Math.max(0f, best.y - (feet + HEIGHT) - MARGIN);
        }
        if (up > MARGIN) {
            sweepPose(x, feet + up, z, x + dx, feet + up, z + dz);
            if (!best.ok) {
                sweepDown(x + dx, feet + up + 2f, z + dz, up + STEP_DOWN);
                if (best.ok && best.walkable) {
                    setSlid(x + dx, best.y + GROUND_OFFSET, z + dz);
                } else {
                    setSlid(x + dx, feet + up, z + dz);
                }
                return;
            }
        }
        applyFraction(x, feet, z, dx, dz, hitFrac);
        float px = hitNx;
        float pz = hitNz;
        if (hitNy > MAX_SLOPE_COS || hitNy < -0.5f) {
            return;
        }
        float plen = (float) Math.hypot(px, pz);
        if (plen < 1e-4f) {
            return;
        }
        px /= plen;
        pz /= plen;
        float keep = dx * px + dz * pz;
        if (keep > 0f) {
            dx -= px * keep;
            dz -= pz * keep;
        }
        if (dx == 0f && dz == 0f) {
            return;
        }
        float sx = slid[0];
        float sz = slid[2];
        sweepPose(sx, feet, sz, sx + dx, feet, sz + dz);
        if (!best.ok) {
            setSlid(sx + dx, feet, sz + dz);
            return;
        }
        applyFraction(sx, feet, sz, dx, dz, best.fraction);
    }

    private static void applyFraction(float x, float feet, float z, float dx, float dz, float fraction) {
        float dist = (float) Math.hypot(dx, dz);
        float travel = fraction * dist - MARGIN;
        if (travel < 0f) {
            travel = 0f;
        }
        float s = dist > 1e-4f ? travel / dist : 0f;
        setSlid(x + dx * s, feet, z + dz * s);
    }

    private static void setSlid(float x, float feet, float z) {
        slid[0] = x;
        slid[1] = feet;
        slid[2] = z;
    }

    private static float sweepY(float x, float feet, float z, float dy) {
        if (dy == 0f) {
            return feet;
        }
        if (dy > 0f) {
            sweepUp(x, feet, z, Math.abs(dy) + 4f);
            if (best.ok) {
                return Math.min(feet + dy, best.y - HEIGHT - MARGIN);
            }
            return feet + dy;
        }
        sweepDown(x, feet + 2f, z, Math.abs(dy) + 4f);
        if (best.ok && best.walkable) {
            return Math.max(feet + dy, best.y + GROUND_OFFSET);
        }
        if (fits(x, feet + dy, z)) {
            return feet + dy;
        }
        return feet;
    }

    private static boolean fits(float x, float feet, float z) {
        if (!deepest(x, feet, z)) {
            return true;
        }
        if (best.walkable || best.ny < -0.5f) {
            return true;
        }
        return best.depth < MARGIN * 4f;
    }

    private static void depenetrate(float x, float feet, float z) {
        best.px = x;
        best.py = feet;
        best.pz = z;
        for (int i = 0; i < 8; i++) {
            if (!deepest(best.px, best.py, best.pz) || (best.walkable && best.depth < RADIUS * 0.25f)) {
                return;
            }
            if (best.walkable) {
                best.py = Math.max(best.py, best.y + GROUND_OFFSET);
                continue;
            }
            best.px += best.nx * (best.depth + MARGIN);
            best.py += best.ny * (best.depth + MARGIN) * (best.ny < -0.2f || best.ny > MAX_SLOPE_COS ? 1f : 0f);
            best.pz += best.nz * (best.depth + MARGIN);
        }
    }

    private static void refreshDebug(float x, float feet, float z) {
        sweepDown(x, feet + 2f, z, STEP_DOWN + 8f);
        floorY = best.ok ? best.y : Float.NaN;
        onGround = best.ok && best.walkable && feet - best.y <= STEP_DOWN;
        sweepUp(x, feet, z, 256f);
        ceilY = best.ok ? best.y : Float.NaN;
    }

    private static void sweepDown(float x, float fromFeet, float z, float maxDist) {
        sweepPose(x, fromFeet, z, x, fromFeet - maxDist, z);
    }

    private static void sweepUp(float x, float feet, float z, float maxDist) {
        sweepPose(x, feet, z, x, feet + maxDist, z);
    }

    private static void sweepPose(float x0, float feet0, float z0, float x1, float feet1, float z1) {
        best.clear();
        if (world == null || capsule == null) {
            return;
        }
        from.set(x0, feet0 + HALF_H, z0);
        to.set(x1, feet1 + HALF_H, z1);
        if (from.epsilonEquals(to, 1e-4f)) {
            return;
        }
        fromMat.setToTranslation(from);
        toMat.setToTranslation(to);
        sweepCb.setRayFromWorld(from);
        sweepCb.setConvexToWorld(to);
        sweepCb.setClosestHitFraction(1f);
        sweepCb.setHitCollisionObject(null);
        sweepCb.setCollisionFilterGroup(ACTOR);
        sweepCb.setCollisionFilterMask(PLAYER_MASK);
        world.convexSweepTest(capsule, fromMat, toMat, sweepCb);
        if (!sweepCb.hasHit()) {
            return;
        }
        sweepCb.getHitPointWorld(hit);
        sweepCb.getHitNormalWorld(n);
        best.ok = true;
        best.fraction = sweepCb.getClosestHitFraction();
        best.y = hit.y;
        best.nx = n.x;
        best.ny = n.y;
        best.nz = n.z;
        best.walkable = n.y > MAX_SLOPE_COS;
    }

    private static boolean deepest(float x, float feet, float z) {
        best.clear();
        if (world == null || probe == null) {
            return false;
        }
        fromMat.setToTranslation(x, feet + HALF_H, z);
        probe.setWorldTransform(fromMat);
        contactCb.reset();
        world.contactTest(probe, contactCb);
        if (!contactCb.ok) {
            return false;
        }
        best.ok = true;
        best.depth = contactCb.depth;
        best.y = contactCb.y;
        best.nx = contactCb.nx;
        best.ny = contactCb.ny;
        best.nz = contactCb.nz;
        best.walkable = contactCb.walkable;
        return true;
    }

    private static float rayDown(float glX, float glY, float glZ, int mask) {
        if (world == null || bodies.isEmpty() || rayCb == null) {
            return Float.NaN;
        }
        rayFrom.set(glX, glY, glZ);
        rayTo.set(glX, glY - RAY_DOWN, glZ);
        rayCb.setCollisionObject(null);
        rayCb.setClosestHitFraction(1f);
        rayCb.setRayFromWorld(rayFrom);
        rayCb.setRayToWorld(rayTo);
        rayCb.setCollisionFilterGroup(ACTOR);
        rayCb.setCollisionFilterMask(mask);
        world.rayTest(rayFrom, rayTo, rayCb);
        if (!rayCb.hasHit()) {
            return Float.NaN;
        }
        rayCb.getHitPointWorld(hit);
        return hit.y;
    }

    private static void addLandMesh(LandRecord land) {
        Staged staged = cookLandMesh(land);
        if (staged != null) {
            adopt(staged);
        }
    }

    private static Staged cookLandMesh(LandRecord land) {
        btTriangleMesh mesh = new btTriangleMesh();
        float originX = land.gridX * (float) LandRecord.CELL_SIZE;
        float originY = land.gridY * (float) LandRecord.CELL_SIZE;
        int n = LandRecord.SIZE;
        for (int y = 0; y < n - 1; y++) {
            for (int x = 0; x < n - 1; x++) {
                landVert(va, originX, originY, land, x, y);
                landVert(vb, originX, originY, land, x + 1, y);
                landVert(vc, originX, originY, land, x, y + 1);
                landVert(vd, originX, originY, land, x + 1, y + 1);
                mesh.addTriangle(va, vb, vc, true);
                mesh.addTriangle(vb, vd, vc, true);
            }
        }
        btBvhTriangleMeshShape shape = new btBvhTriangleMeshShape(mesh, true, true);
        btCollisionObject obj = new btCollisionObject();
        obj.setCollisionShape(shape);
        obj.setCollisionFlags(btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);
        tmpMat.idt();
        obj.setWorldTransform(tmpMat);
        return new Staged(obj, shape, mesh, HEIGHT_MAP, true);
    }

    private static void landVert(Vector3 out, float originX, float originY, LandRecord land, int x, int y) {
        float tesX = originX + x * STEP;
        float tesY = originY + y * STEP;
        out.set(tesX, land.height(x, y), -tesY);
    }

    private static void addObjectMesh(CollisionMesh.Pending pnd) {
        Staged staged = cookObjectMesh(pnd);
        if (staged != null) {
            adopt(staged);
        }
    }

    private static Staged cookObjectMesh(CollisionMesh.Pending pnd) {
        float[] tris = pnd.mesh.tris;
        if (tris.length < 9) {
            return null;
        }
        btTriangleMesh mesh = new btTriangleMesh();
        tmpMat.set(pnd.node.world);
        for (int i = 0; i + 8 < tris.length; i += 9) {
            va.set(tris[i], tris[i + 1], tris[i + 2]).mul(tmpMat);
            vb.set(tris[i + 3], tris[i + 4], tris[i + 5]).mul(tmpMat);
            vc.set(tris[i + 6], tris[i + 7], tris[i + 8]).mul(tmpMat);
            mesh.addTriangle(va, vb, vc, false);
            mesh.addTriangle(va, vc, vb, false);
        }
        if (mesh.getNumTriangles() == 0) {
            mesh.dispose();
            return null;
        }
        btBvhTriangleMeshShape shape = new btBvhTriangleMeshShape(mesh, true, true);
        btCollisionObject obj = new btCollisionObject();
        obj.setCollisionShape(shape);
        obj.setCollisionFlags(btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);
        tmpMat.idt();
        obj.setWorldTransform(tmpMat);
        return new Staged(obj, shape, mesh, WORLD, false);
    }

    private static void disposeBodies() {
        if (world != null) {
            for (btCollisionObject obj : bodies) {
                world.removeCollisionObject(obj);
            }
        }
        for (btCollisionObject obj : bodies) {
            obj.dispose();
        }
        for (btBvhTriangleMeshShape shape : landShapes) {
            shape.dispose();
        }
        for (btTriangleMesh mesh : landMeshes) {
            mesh.dispose();
        }
        for (btBvhTriangleMeshShape shape : objectShapes) {
            shape.dispose();
        }
        for (btTriangleMesh mesh : objectMeshes) {
            mesh.dispose();
        }
        bodies.clear();
        landShapes.clear();
        landMeshes.clear();
        objectShapes.clear();
        objectMeshes.clear();
        landBodies = 0;
        worldBodies = 0;
        readyCells.clear();
        interiorReady = false;
    }

    private static final class DeepestContact extends ContactResultCallback {
        boolean ok;
        float depth;
        float y;
        float nx;
        float ny = 1f;
        float nz;
        boolean walkable;
        private final Vector3 normal = new Vector3();
        private final Vector3 point = new Vector3();

        void reset() {
            ok = false;
            depth = 0f;
            y = 0f;
            nx = 0f;
            ny = 1f;
            nz = 0f;
            walkable = false;
        }

        @Override
        public float addSingleResult(btManifoldPoint cp, btCollisionObjectWrapper colObj0Wrap, int partId0, int index0,
            btCollisionObjectWrapper colObj1Wrap, int partId1, int index1) {
            float dist = cp.getDistance();
            if (dist >= 0f) {
                return 1f;
            }
            float d = -dist;
            if (ok && d <= depth) {
                return 1f;
            }
            cp.getNormalWorldOnB(normal);
            cp.getPositionWorldOnB(point);
            ok = true;
            depth = d;
            y = point.y;
            nx = normal.x;
            ny = normal.y;
            nz = normal.z;
            walkable = normal.y > MAX_SLOPE_COS;
            return d;
        }
    }

    private static final class Hit {
        boolean ok;
        boolean walkable;
        float depth;
        float fraction;
        float y;
        float nx;
        float ny = 1f;
        float nz;
        float px;
        float py;
        float pz;

        void clear() {
            ok = false;
            walkable = false;
            depth = 0f;
            fraction = 1f;
            y = 0f;
            nx = 0f;
            ny = 1f;
            nz = 0f;
        }
    }
}

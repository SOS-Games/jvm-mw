package io.github.jvmmw.render;

import io.github.jvmmw.esm.LandRecord;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionWorld;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;

import java.util.ArrayList;
import java.util.List;

/**
 * JNI Bullet collision world for the loaded cell. Exterior land is one
 * mesh per TES tile (the 21 Town cells), in camera Y-up. WASD still uses
 * the old capsule tracer. Chair HUD has no world.
 */
public final class BulletWorld {
    static final int HEIGHT_MAP = 1 << 3;
    static final int ACTOR = 1 << 2;
    static final int PROJECTILE = 1 << 4;

    private static final float STEP = LandRecord.CELL_SIZE / (float) (LandRecord.SIZE - 1);
    private static final float RAY_DOWN = 8192f;

    private static boolean natives;
    private static btDefaultCollisionConfiguration config;
    private static btCollisionDispatcher dispatcher;
    private static btDbvtBroadphase broadphase;
    private static btCollisionWorld world;
    private static final List<btCollisionObject> bodies = new ArrayList<>();
    private static final List<btBvhTriangleMeshShape> shapes = new ArrayList<>();
    private static final List<btTriangleMesh> meshes = new ArrayList<>();
    private static final Vector3 hit = new Vector3();
    private static final Vector3 rayFrom = new Vector3();
    private static final Vector3 rayTo = new Vector3();
    private static final Vector3 va = new Vector3();
    private static final Vector3 vb = new Vector3();
    private static final Vector3 vc = new Vector3();
    private static final Vector3 vd = new Vector3();
    private static final Matrix4 identity = new Matrix4();

    private BulletWorld() {
    }

    /** Load natives once. Viewer only — not the headless CLI. */
    public static void initNatives() {
        if (natives) {
            return;
        }
        Bullet.init();
        natives = true;
    }

    /** Same moments as CollisionWorld.clear: interior load and walk-grid swap. */
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

    public static boolean alive() {
        return world != null;
    }

    public static int bodyCount() {
        return world == null ? 0 : world.getNumCollisionObjects();
    }

    /** HeightMap hit Y under the camera, or NaN. Not used for walk. */
    public static float floorY(float glX, float glY, float glZ) {
        if (world == null || bodies.isEmpty()) {
            return Float.NaN;
        }
        rayFrom.set(glX, glY, glZ);
        rayTo.set(glX, glY - RAY_DOWN, glZ);
        ClosestRayResultCallback cb = new ClosestRayResultCallback(rayFrom, rayTo);
        cb.setCollisionFilterGroup(ACTOR);
        cb.setCollisionFilterMask(HEIGHT_MAP);
        world.rayTest(rayFrom, rayTo, cb);
        float y = Float.NaN;
        if (cb.hasHit()) {
            cb.getHitPointWorld(hit);
            y = hit.y;
        }
        cb.dispose();
        return y;
    }

    private static void addLandMesh(LandRecord land) {
        // World Y-up triangles. A Bullet heightfield is Z-up; negative scale
        // to flip TES Y made an empty AABB (Dump btFloorY=none on Town dirt).
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
        identity.idt();
        obj.setWorldTransform(identity);
        world.addCollisionObject(obj, HEIGHT_MAP, ACTOR | PROJECTILE);
        world.updateSingleAabb(obj);
        bodies.add(obj);
        shapes.add(shape);
        meshes.add(mesh);
    }

    private static void landVert(Vector3 out, float originX, float originY, LandRecord land, int x, int y) {
        float tesX = originX + x * STEP;
        float tesY = originY + y * STEP;
        out.set(tesX, land.height(x, y), -tesY);
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
        for (btBvhTriangleMeshShape shape : shapes) {
            shape.dispose();
        }
        for (btTriangleMesh mesh : meshes) {
            mesh.dispose();
        }
        bodies.clear();
        shapes.clear();
        meshes.clear();
    }
}

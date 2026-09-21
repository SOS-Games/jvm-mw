package io.github.jvmmw.render;

import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionWorld;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;

/**
 * JNI Bullet collision world for the loaded cell. Empty this phase — land
 * and shack meshes come later. WASD still uses the old capsule tracer.
 * Same Y-up space as the camera. Chair HUD has no world.
 */
public final class BulletWorld {
    private static boolean natives;
    private static btDefaultCollisionConfiguration config;
    private static btCollisionDispatcher dispatcher;
    private static btDbvtBroadphase broadphase;
    private static btCollisionWorld world;

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

    public static void disposeWorld() {
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
}

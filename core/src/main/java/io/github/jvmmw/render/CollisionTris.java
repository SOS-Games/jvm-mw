package io.github.jvmmw.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * World-space shack triangles Recast gathers. Copied once at cell load so
 * the nav worker does not read live SceneNode matrices. Not a physics
 * world — player and NPC feet are Bullet.
 */
public final class CollisionTris {
    private final List<Chunk> chunks = new ArrayList<>();
    private final Matrix4 world = new Matrix4();
    private final Vector3 v = new Vector3();

    public synchronized void clear() {
        chunks.clear();
    }

    public synchronized void bake(List<CollisionMesh.Pending> pending) {
        clear();
        if (pending == null) {
            return;
        }
        for (CollisionMesh.Pending pnd : pending) {
            if (pnd.mesh == null || pnd.mesh.isEmpty() || pnd.node == null) {
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

    /** Packed xyz triples of object triangles whose AABB overlaps the GL XZ box. */
    public synchronized void appendObjectTris(float minX, float maxX, float minZ, float maxZ, List<Float> xyz) {
        for (Chunk chunk : chunks) {
            if (chunk.aabb.max.x < minX || chunk.aabb.min.x > maxX
                || chunk.aabb.max.z < minZ || chunk.aabb.min.z > maxZ) {
                continue;
            }
            float[] t = chunk.tris;
            for (int i = 0; i < t.length; i++) {
                xyz.add(t[i]);
            }
        }
    }

    private static final class Chunk {
        final BoundingBox aabb = new BoundingBox();
        float[] tris = new float[0];
    }
}

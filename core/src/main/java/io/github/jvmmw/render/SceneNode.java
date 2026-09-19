package io.github.jvmmw.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** Owned scene graph. Rewrite of OSG {@code osg::Node}, not a 1:1 port. */
public final class SceneNode {
    public String name = "";
    public SceneNode parent;
    public final List<SceneNode> children = new ArrayList<>();
    public final Matrix4 local = new Matrix4();
    public final Matrix4 world = new Matrix4();
    public final List<MeshInstance> meshes = new ArrayList<>();
    public boolean skipMeshes;

    public void addChild(SceneNode child) {
        child.parent = this;
        children.add(child);
    }

    public void removeFromParent() {
        if (parent != null) {
            parent.children.remove(this);
            parent = null;
        }
    }

    public void updateWorld(Matrix4 parentWorld) {
        world.set(parentWorld).mul(local);
        for (SceneNode child : children) {
            child.updateWorld(world);
        }
    }

    public void collectAabb(BoundingBox box) {
        if (!skipMeshes) {
            for (MeshInstance inst : meshes) {
                inst.mesh.expandWorldAabb(world, box);
            }
        }
        for (SceneNode child : children) {
            child.collectAabb(box);
        }
    }
}

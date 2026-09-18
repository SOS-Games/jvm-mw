package io.github.jvmmw.render;

public final class MeshInstance {
    public final MeshGpu mesh;
    public boolean frontClockwise;

    public MeshInstance(MeshGpu mesh) {
        this.mesh = mesh;
    }
}

package io.github.jvmmw.render;

import io.github.jvmmw.nif.NiTriBasedGeom;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.Skinning;

import com.badlogic.gdx.math.Matrix4;

import java.util.Map;

public final class MeshInstance {
    public final MeshGpu mesh;
    public boolean frontClockwise;
    public NifFile skinNif;
    public NiTriBasedGeom skinGeom;
    public float[] bindVerts;
    public float[] bindNorms;
    public float[] workVerts;
    public float[] workNorms;
    public float[] interleaved;

    public MeshInstance(MeshGpu mesh) {
        this.mesh = mesh;
    }

    public void reskin(Map<String, Matrix4> boneWorld) {
        if (skinNif == null || bindVerts == null) {
            return;
        }
        System.arraycopy(bindVerts, 0, workVerts, 0, bindVerts.length);
        if (bindNorms.length > 0) {
            System.arraycopy(bindNorms, 0, workNorms, 0, bindNorms.length);
        }
        Skinning.apply(skinNif, skinGeom, workVerts, workNorms.length == 0 ? null : workNorms, boneWorld);
        int n = workVerts.length / 3;
        for (int i = 0; i < n; i++) {
            int o = i * MeshGpu.STRIDE_FLOATS;
            interleaved[o] = workVerts[i * 3];
            interleaved[o + 1] = workVerts[i * 3 + 1];
            interleaved[o + 2] = workVerts[i * 3 + 2];
            if (workNorms.length == n * 3) {
                interleaved[o + 3] = workNorms[i * 3];
                interleaved[o + 4] = workNorms[i * 3 + 1];
                interleaved[o + 5] = workNorms[i * 3 + 2];
            }
        }
        mesh.updateVertices(interleaved);
    }
}

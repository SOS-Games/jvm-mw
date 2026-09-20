package io.github.jvmmw.render;

import io.github.jvmmw.resource.DdsTexture;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Day sun disc. Camera-relative textured quad. Rewrite of OpenMW
 * {@code CelestialBody} / {@code Sun} (no glare/flash).
 */
public final class SkySun {
    public static final String TEXTURE = "textures/tx_sun_05.dds";
    public static final int PASS = 4;
    public static final float DISTANCE = 1000f;
    public static final float SCALE = 450f;

    private DdsTexture texture;
    private MeshGpu mesh;
    public SceneNode root;

    public void load() throws Exception {
        String vfs = TexturePaths.correctTexturePath(TEXTURE, TestData::vfsExists);
        texture = DdsTexture.load(TestData.openPath(vfs));
        mesh = uploadQuad();
        mesh.skyShader = true;
        mesh.skyPass = PASS;
        mesh.alphaBlend = true;
        mesh.depthWrite = false;
        mesh.cull = false;
        mesh.baseTex = texture.textureId;
        mesh.baseWrapS = GL20.GL_CLAMP_TO_EDGE;
        mesh.baseWrapT = GL20.GL_CLAMP_TO_EDGE;

        root = new SceneNode();
        root.name = "sky-sun";
        root.local.setToRotation(1, 0, 0, -90);
        SceneNode body = new SceneNode();
        body.name = "sun";
        body.meshes.add(new MeshInstance(mesh));
        placeMidday(body.local);
        root.addChild(body);
        root.updateWorld(new Matrix4());
        Gdx.app.log("SkySun", "loaded " + vfs);
    }

    /**
     * Midday: weather orbit 0 → {@code sunDir (0, 75, -100)};
     * {@code setSunDirection} → TES3 {@code (0, -75, 400)}.
     */
    private static void placeMidday(Matrix4 local) {
        Vector3 dir = new Vector3(0f, -75f, 400f).nor();
        Vector3 pos = new Vector3(dir).scl(DISTANCE);
        Quaternion rot = new Quaternion().setFromCross(new Vector3(0f, 0f, 1f), dir);
        local.idt();
        local.translate(pos.x, pos.y, pos.z);
        local.mul(new Matrix4().set(rot));
        local.scale(SCALE, SCALE, SCALE);
    }

    private static MeshGpu uploadQuad() {
        float[] v = new float[4 * MeshGpu.STRIDE_FLOATS];
        pack(v, 0, -0.5f, -0.5f, 0f, 0f, 0f);
        pack(v, 1, -0.5f, 0.5f, 0f, 0f, 1f);
        pack(v, 2, 0.5f, 0.5f, 0f, 1f, 1f);
        pack(v, 3, 0.5f, -0.5f, 0f, 1f, 0f);
        short[] idx = {0, 1, 2, 0, 2, 3};
        return new MeshGpu(v, idx);
    }

    private static void pack(float[] v, int i, float x, float y, float z, float u, float vv) {
        int o = i * MeshGpu.STRIDE_FLOATS;
        v[o] = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 5] = 1f;
        v[o + 6] = u;
        v[o + 7] = vv;
        v[o + 8] = 1f;
        v[o + 9] = 1f;
        v[o + 10] = 1f;
        v[o + 11] = 1f;
    }

    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
        root = null;
    }
}

package io.github.jvmmw.render;

import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.DdsTexture;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Day cloud dome ({@code meshes/sky_clouds_01.nif}). Camera-relative; Clear
 * {@code Tx_Sky_Clear.dds}. Rewrite of OpenMW {@code CloudUpdater} / cloud mesh.
 */
public final class SkyClouds {
    public static final String MODEL = "meshes/sky_clouds_01.nif";
    public static final String TEXTURE = "Tx_Sky_Clear.dds";
    public static final int PASS = 2;
    /** {@code Weather_Clear_Fog_Day_Color} 206,227,255 / 255. */
    public static final float[] CLEAR_FOG = {206f / 255f, 227f / 255f, 255f / 255f};
    /** Fog + {@code (0.13, 0.13, 0.13)} as in {@code SkyManager::setWeather}. */
    public static final float[] EMISSION = {CLEAR_FOG[0] + 0.13f, CLEAR_FOG[1] + 0.13f, CLEAR_FOG[2] + 0.13f};
    /** {@code Weather_Clear_Cloud_Speed}. */
    public static final float SPEED = 1.25f;

    private NifSceneBuilder builder;
    private DdsTexture texture;
    public SceneNode root;
    public float timer;

    public void load() throws Exception {
        Path nifPath = TestData.ensureNif(MODEL);
        NifFile nif = NifFile.parse(Files.readAllBytes(nifPath), MODEL);
        builder = new NifSceneBuilder(nif, TestData.testdataRoot(), TestData::vfsExists);
        root = builder.build(true);
        String vfs = TexturePaths.correctTexturePath(TEXTURE, TestData::vfsExists);
        texture = GpuCache.texture(vfs);
        if (texture == null) {
            throw new IllegalStateException("cloud texture " + vfs);
        }
        mark(root, texture.textureId);
        root.updateWorld(new Matrix4());
        Gdx.app.log("SkyClouds", "loaded " + MODEL + " tex=" + vfs);
    }

    /** UV offset from game hour so the HUD slider / Play / [ ] scrub the dome. One wrap per 24h. */
    public void setFromHour(float hour) {
        timer = hour * SPEED / 6f;
        timer -= 4f * (float) Math.floor(timer / 4f);
        if (timer < 0f) {
            timer += 4f;
        }
    }

    private static void mark(SceneNode node, int tex) {
        for (MeshInstance inst : node.meshes) {
            MeshGpu mesh = inst.mesh;
            mesh.skyShader = true;
            mesh.skyPass = PASS;
            mesh.alphaBlend = true;
            mesh.depthWrite = false;
            mesh.cull = false;
            mesh.baseTex = tex;
            mesh.baseWrapS = GL20.GL_REPEAT;
            mesh.baseWrapT = GL20.GL_REPEAT;
            mesh.applyCloudsVertexAlpha();
        }
        for (SceneNode child : node.children) {
            mark(child, tex);
        }
    }

    public void dispose() {
        if (builder != null) {
            builder.dispose();
            builder = null;
        }
        texture = null;
        root = null;
    }
}

package io.github.jvmmw.render;

import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.TestData;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Day sky dome. Moves with the camera so it always looks infinitely far.
 */
public final class SkyAtmosphere {
    public static final String MODEL = "meshes/sky_atmosphere.nif";
    /** {@code Weather_Clear_Sky_Day_Color} 095,135,203 / 255. */
    public static final float[] CLEAR_DAY = {95f / 255f, 135f / 255f, 203f / 255f};

    private NifSceneBuilder builder;
    public SceneNode root;

    public void load() throws Exception {
        Path nifPath = TestData.ensureNif(MODEL);
        NifFile nif = NifFile.parse(Files.readAllBytes(nifPath), MODEL);
        builder = new NifSceneBuilder(nif, TestData.testdataRoot(), TestData::vfsExists);
        root = builder.build(true);
        mark(root);
        root.updateWorld(new Matrix4());
        Gdx.app.log("SkyAtmosphere", "loaded " + MODEL);
    }

    private static void mark(SceneNode node) {
        for (MeshInstance inst : node.meshes) {
            MeshGpu mesh = inst.mesh;
            mesh.skyShader = true;
            mesh.alphaBlend = true;
            mesh.depthWrite = false;
            mesh.cull = false;
            mesh.applyAtmosphereVertexAlpha();
        }
        for (SceneNode child : node.children) {
            mark(child);
        }
    }

    public void dispose() {
        if (builder != null) {
            builder.dispose();
            builder = null;
        }
        root = null;
    }
}

package io.github.jvmmw.render;

import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.TestData;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Night star dome. Rewrite of OpenMW atmosphere-night mesh ({@code skynight01/02}).
 */
public final class SkyStars {
    public static final String NIGHT_02 = "meshes/sky_night_02.nif";
    public static final String NIGHT_01 = "meshes/sky_night_01.nif";
    public static final int PASS = 1;

    private NifSceneBuilder builder;
    public SceneNode root;

    public void load() throws Exception {
        String model = TestData.vfsExists(NIGHT_02) ? NIGHT_02 : NIGHT_01;
        Path nifPath = TestData.ensureNif(model);
        NifFile nif = NifFile.parse(Files.readAllBytes(nifPath), model);
        builder = new NifSceneBuilder(nif, TestData.testdataRoot(), TestData::vfsExists);
        root = builder.build(true);
        mark(root);
        root.updateWorld(new Matrix4());
        Gdx.app.log("SkyStars", "loaded " + model);
    }

    private static void mark(SceneNode node) {
        for (MeshInstance inst : node.meshes) {
            MeshGpu mesh = inst.mesh;
            mesh.skyShader = true;
            mesh.skyPass = PASS;
            mesh.alphaBlend = true;
            mesh.depthWrite = false;
            mesh.cull = false;
            mesh.applyStarsVertexAlpha();
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

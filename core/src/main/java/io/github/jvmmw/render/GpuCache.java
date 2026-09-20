/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.DdsTexture;
import io.github.jvmmw.resource.TestData;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared GPU copies of textures and static meshes, keyed by file path.
 * They live for the whole process — swapping cells must not delete them.
 * Skinned copies and land blend maps are per-cell and do go away.
 */
public final class GpuCache {
    private GpuCache() {
    }

    private static final Map<String, DdsTexture> textures = new HashMap<>();
    private static final Map<String, NifSceneBuilder> nifs = new HashMap<>();
    private static final Map<String, SceneNode> meshTemplates = new HashMap<>();
    private static final Map<String, SceneNode> boneTemplates = new HashMap<>();
    private static int whiteTex;

    public static int texGpu() {
        return textures.size();
    }

    public static int nifGpu() {
        return meshTemplates.size();
    }

    public static int whiteId() {
        if (whiteTex != 0) {
            return whiteTex;
        }
        whiteTex = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, whiteTex);
        java.nio.ByteBuffer px = java.nio.ByteBuffer.allocateDirect(4);
        px.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, 1, 1, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        return whiteTex;
    }

    public static DdsTexture texture(String vfs) {
        if (vfs == null || vfs.isEmpty()) {
            return null;
        }
        DdsTexture hit = textures.get(vfs);
        if (hit != null) {
            return hit;
        }
        try {
            DdsTexture dds = DdsTexture.load(TestData.openPath(vfs));
            textures.put(vfs, dds);
            return dds;
        } catch (Exception e) {
            Gdx.app.error("GpuCache", "Texture failed: " + vfs + " " + e.getMessage());
            return null;
        }
    }

    public static NifSceneBuilder nif(String vfs) throws Exception {
        NifSceneBuilder hit = nifs.get(vfs);
        if (hit != null) {
            return hit;
        }
        Path nifPath = TestData.ensureNif(vfs);
        NifFile parsed = NifFile.parse(Files.readAllBytes(nifPath), vfs);
        NifSceneBuilder builder = new NifSceneBuilder(parsed, TestData.testdataRoot(), TestData::vfsExists);
        builder.shared = true;
        nifs.put(vfs, builder);
        return builder;
    }

    public static SceneNode meshTemplate(String vfs) throws Exception {
        SceneNode hit = meshTemplates.get(vfs);
        if (hit != null) {
            return hit;
        }
        SceneNode template = nif(vfs).build(false);
        meshTemplates.put(vfs, template);
        return template;
    }

    public static SceneNode boneTemplate(String vfs) throws Exception {
        SceneNode hit = boneTemplates.get(vfs);
        if (hit != null) {
            return hit;
        }
        SceneNode template = nif(vfs).buildBones();
        boneTemplates.put(vfs, template);
        return template;
    }

    public static void dispose() {
        for (NifSceneBuilder builder : nifs.values()) {
            builder.dispose();
        }
        nifs.clear();
        meshTemplates.clear();
        boneTemplates.clear();
        for (DdsTexture dds : textures.values()) {
            dds.dispose();
        }
        textures.clear();
        if (whiteTex != 0) {
            Gdx.gl.glDeleteTexture(whiteTex);
            whiteTex = 0;
        }
    }
}

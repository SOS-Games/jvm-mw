package io.github.jvmmw.render;

import io.github.jvmmw.nif.NiAlphaProperty;
import io.github.jvmmw.nif.NiAvObject;
import io.github.jvmmw.nif.NiNode;
import io.github.jvmmw.nif.NiSourceTexture;
import io.github.jvmmw.nif.NiTexturingProperty;
import io.github.jvmmw.nif.NiTriBasedGeom;
import io.github.jvmmw.nif.NiTriShapeData;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NifRecord;
import io.github.jvmmw.resource.DdsTexture;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Rewrite of {@code NifOsg::Loader} onto {@link SceneNode}. */
public final class NifSceneBuilder {
    private final NifFile nif;
    private final Path testdata;
    private final Predicate<String> exists;
    private final Map<String, DdsTexture> textures = new HashMap<>();
    private final List<MeshGpu> gpus = new ArrayList<>();
    private int whiteTex;

    public NifSceneBuilder(NifFile nif, Path testdata, Predicate<String> exists) {
        this.nif = nif;
        this.testdata = testdata;
        this.exists = exists;
    }

    public SceneNode build() {
        ensureWhite();
        SceneNode root = new SceneNode();
        root.name = "nif-root";
        // Morrowind Z-up → OpenGL Y-up
        root.local.setToRotation(1, 0, 0, -90);
        for (int idx : nif.roots) {
            NifRecord rec = nif.get(idx);
            if (rec instanceof NiAvObject av) {
                root.addChild(buildAv(av, false));
            }
        }
        Matrix4 id = new Matrix4();
        root.updateWorld(id);
        return root;
    }

    public void dispose() {
        for (MeshGpu gpu : gpus) {
            gpu.dispose();
        }
        gpus.clear();
        for (DdsTexture t : textures.values()) {
            t.dispose();
        }
        textures.clear();
        if (whiteTex != 0) {
            Gdx.gl.glDeleteTexture(whiteTex);
            whiteTex = 0;
        }
    }

    private SceneNode buildAv(NiAvObject av, boolean skip) {
        SceneNode node = new SceneNode();
        node.name = av.name;
        av.transform.toMatrix(node.local);
        boolean skipHere = skip || av.skipMeshes || av instanceof NiNode n && n.rootCollision;
        node.skipMeshes = skipHere;
        if (av instanceof NiTriBasedGeom geom && !skipHere) {
            NifRecord dataRec = nif.get(geom.data);
            if (dataRec instanceof NiTriShapeData data && data.vertices.length > 0 && data.triangles.length >= 3) {
                MeshGpu gpu = upload(data, geom);
                node.meshes.add(new MeshInstance(gpu));
            }
        }
        if (av instanceof NiNode group) {
            for (int childIdx : group.children) {
                NifRecord rec = nif.get(childIdx);
                if (rec instanceof NiAvObject child) {
                    node.addChild(buildAv(child, skipHere));
                }
            }
        }
        return node;
    }

    private MeshGpu upload(NiTriShapeData data, NiTriBasedGeom geom) {
        int n = data.numVertices;
        float[] interleaved = new float[n * 8];
        for (int i = 0; i < n; i++) {
            interleaved[i * 8] = data.vertices[i * 3];
            interleaved[i * 8 + 1] = data.vertices[i * 3 + 1];
            interleaved[i * 8 + 2] = data.vertices[i * 3 + 2];
            if (data.normals.length == n * 3) {
                interleaved[i * 8 + 3] = data.normals[i * 3];
                interleaved[i * 8 + 4] = data.normals[i * 3 + 1];
                interleaved[i * 8 + 5] = data.normals[i * 3 + 2];
            } else {
                interleaved[i * 8 + 4] = 1f;
            }
            if (data.uvs.length == n * 2) {
                interleaved[i * 8 + 6] = data.uvs[i * 2];
                interleaved[i * 8 + 7] = data.uvs[i * 2 + 1];
            }
        }
        MeshGpu mesh = new MeshGpu(interleaved, data.triangles);
        gpus.add(mesh);
        mesh.textureId = resolveTexture(geom);
        for (int propIdx : geom.properties) {
            NifRecord p = nif.get(propIdx);
            if (p instanceof NiAlphaProperty a) {
                mesh.alphaBlend = (a.flags & 0x0001) != 0;
                mesh.alphaTest = (a.flags & 0x0200) != 0;
                mesh.alphaRef = a.threshold / 255f;
            }
        }
        return mesh;
    }

    private int resolveTexture(NiTriBasedGeom geom) {
        for (int propIdx : geom.properties) {
            NifRecord p = nif.get(propIdx);
            if (p instanceof NiTexturingProperty tex && !tex.textures.isEmpty()) {
                NiTexturingProperty.TextureSlot slot = tex.textures.get(0);
                if (slot.enabled) {
                    NifRecord src = nif.get(slot.source);
                    if (src instanceof NiSourceTexture st && !st.file.isEmpty()) {
                        String vfs = TexturePaths.correctTexturePath(st.file, exists);
                        Path resolved = testdata.resolve(vfs.replace('/', testdata.getFileSystem().getSeparator().charAt(0)));
                        if (!Files.isRegularFile(resolved)) {
                            resolved = testdata.resolve(vfs.replace('/', java.io.File.separatorChar));
                        }
                        final Path file = resolved;
                        try {
                            DdsTexture dds = textures.computeIfAbsent(vfs, k -> {
                                try {
                                    return DdsTexture.load(file);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                            return dds.textureId;
                        } catch (Exception e) {
                            Gdx.app.error("NifSceneBuilder", "Texture failed: " + vfs + " " + e.getMessage());
                        }
                    }
                }
            }
        }
        return whiteTex;
    }

    private void ensureWhite() {
        if (whiteTex != 0) {
            return;
        }
        whiteTex = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, whiteTex);
        java.nio.ByteBuffer px = java.nio.ByteBuffer.allocateDirect(4);
        px.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, 1, 1, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, px);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    }
}

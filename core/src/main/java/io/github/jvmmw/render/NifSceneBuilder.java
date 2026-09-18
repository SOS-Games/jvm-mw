package io.github.jvmmw.render;

import io.github.jvmmw.nif.NiAlphaProperty;
import io.github.jvmmw.nif.NiAvObject;
import io.github.jvmmw.nif.NiMaterialProperty;
import io.github.jvmmw.nif.NiNode;
import io.github.jvmmw.nif.NiSourceTexture;
import io.github.jvmmw.nif.Skinning;
import io.github.jvmmw.nif.NiSpecularProperty;
import io.github.jvmmw.nif.NiStencilProperty;
import io.github.jvmmw.nif.NiTexturingProperty;
import io.github.jvmmw.nif.NiTriBasedGeom;
import io.github.jvmmw.nif.NiTriShapeData;
import io.github.jvmmw.nif.NiVertexColorProperty;
import io.github.jvmmw.nif.NiZBufferProperty;
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
    public final StringBuilder flattenLog = new StringBuilder();

    private final NifFile nif;
    private final Path testdata;
    private final Predicate<String> exists;
    private final Map<String, DdsTexture> textures = new HashMap<>();
    private final List<MeshGpu> gpus = new ArrayList<>();
    private int whiteTex;
    private boolean bonesOnly;

    public NifSceneBuilder(NifFile nif, Path testdata, Predicate<String> exists) {
        this.nif = nif;
        this.testdata = testdata;
        this.exists = exists;
    }

    public SceneNode build() {
        return build(true);
    }

    public SceneNode build(boolean convertZUp) {
        return build(convertZUp, false);
    }

    public SceneNode buildBones() {
        return build(false, true);
    }

    public SceneNode build(boolean convertZUp, boolean bonesOnly) {
        this.bonesOnly = bonesOnly;
        ensureWhite();
        flattenLog.setLength(0);
        SceneNode root = new SceneNode();
        root.name = "nif-root";
        if (convertZUp) {
            // Morrowind Z-up → OpenGL Y-up
            root.local.setToRotation(1, 0, 0, -90);
        }
        for (int idx : nif.roots) {
            NifRecord rec = nif.get(idx);
            if (rec instanceof NiAvObject av) {
                root.addChild(buildAv(av, false, List.of()));
            }
        }
        Matrix4 id = new Matrix4();
        root.updateWorld(id);
        return root;
    }

    public boolean isSkeleton() {
        for (NifRecord rec : nif.records) {
            if (rec instanceof NiTriBasedGeom geom && geom.skin >= 0) {
                return true;
            }
        }
        return false;
    }

    public void copyMatchingSkinned(String filter, Map<String, Matrix4> boneWorld, SceneNode dest) {
        ensureWhite();
        for (NifRecord rec : nif.records) {
            if (!(rec instanceof NiTriBasedGeom geom) || geom.skin < 0 || geom.skipMeshes) {
                continue;
            }
            if (!filterMatches(geom.name, filter)) {
                continue;
            }
            NifRecord dataRec = nif.get(geom.data);
            if (!(dataRec instanceof NiTriShapeData data) || data.vertices.length == 0 || data.triangles.length < 3) {
                continue;
            }
            List<NiAvObject> path = pathTo(geom);
            MeshInstance inst = uploadSkinned(geom, data, path, boneWorld);
            SceneNode node = new SceneNode();
            node.name = geom.name;
            node.meshes.add(inst);
            dest.addChild(node);
        }
    }

    public static boolean filterMatches(String name, String filter) {
        if (startsWithIgnoreCase(name, filter)) {
            return true;
        }
        if (startsWithIgnoreCase(name, "tri ")) {
            return startsWithIgnoreCase(name.substring(4), filter);
        }
        return false;
    }

    private static boolean startsWithIgnoreCase(String name, String prefix) {
        return name.regionMatches(true, 0, prefix, 0, prefix.length());
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

    private SceneNode buildAv(NiAvObject av, boolean skip, List<NiAvObject> ancestors) {
        SceneNode node = new SceneNode();
        node.name = av.name;
        av.transform.toMatrix(node.local);
        boolean skipHere = skip || av.skipMeshes || av instanceof NiNode n && n.rootCollision;
        node.skipMeshes = skipHere;
        List<NiAvObject> path = new ArrayList<>(ancestors);
        path.add(av);
        if (av instanceof NiTriBasedGeom geom && !skipHere && !bonesOnly && geom.skin < 0) {
            NifRecord dataRec = nif.get(geom.data);
            if (dataRec instanceof NiTriShapeData data && data.vertices.length > 0 && data.triangles.length >= 3) {
                MeshGpu gpu = upload(data, path);
                node.meshes.add(new MeshInstance(gpu));
            }
        }
        if (av instanceof NiNode group) {
            for (int childIdx : group.children) {
                NifRecord rec = nif.get(childIdx);
                if (rec instanceof NiAvObject child) {
                    node.addChild(buildAv(child, skipHere, path));
                }
            }
        }
        return node;
    }

    private MeshGpu upload(NiTriShapeData data, List<NiAvObject> path) {
        float[] interleaved = pack(data, data.vertices, data.normals);
        MeshGpu gpu = new MeshGpu(interleaved, data.triangles);
        gpus.add(gpu);
        flattenOnto(gpu, path, data.colors.length == data.numVertices * 4);
        return gpu;
    }

    private MeshInstance uploadSkinned(NiTriBasedGeom geom, NiTriShapeData data, List<NiAvObject> path,
        Map<String, Matrix4> boneWorld) {
        float[] bindVerts = data.vertices.clone();
        float[] bindNorms = data.normals.length == data.numVertices * 3 ? data.normals.clone() : new float[0];
        float[] verts = bindVerts.clone();
        float[] norms = bindNorms.clone();
        int result = Skinning.apply(nif, geom, verts, norms.length == 0 ? null : norms, boneWorld);
        if (result < 0) {
            Gdx.app.error("NifSceneBuilder", "skin " + geom.name + " result=" + result);
        }
        float[] interleaved = pack(data, verts, norms);
        MeshGpu gpu = new MeshGpu(interleaved, data.triangles, true);
        gpus.add(gpu);
        flattenOnto(gpu, path, data.colors.length == data.numVertices * 4);
        MeshInstance inst = new MeshInstance(gpu);
        inst.skinNif = nif;
        inst.skinGeom = geom;
        inst.bindVerts = bindVerts;
        inst.bindNorms = bindNorms;
        inst.workVerts = verts;
        inst.workNorms = norms;
        inst.interleaved = interleaved;
        return inst;
    }

    private static float[] pack(NiTriShapeData data, float[] vertices, float[] normals) {
        int n = data.numVertices;
        boolean hasColors = data.colors.length == n * 4;
        float[] interleaved = new float[n * MeshGpu.STRIDE_FLOATS];
        for (int i = 0; i < n; i++) {
            int o = i * MeshGpu.STRIDE_FLOATS;
            interleaved[o] = vertices[i * 3];
            interleaved[o + 1] = vertices[i * 3 + 1];
            interleaved[o + 2] = vertices[i * 3 + 2];
            if (normals.length == n * 3) {
                interleaved[o + 3] = normals[i * 3];
                interleaved[o + 4] = normals[i * 3 + 1];
                interleaved[o + 5] = normals[i * 3 + 2];
            } else {
                interleaved[o + 4] = 1f;
            }
            if (data.uvs.length == n * 2) {
                interleaved[o + 6] = data.uvs[i * 2];
                interleaved[o + 7] = data.uvs[i * 2 + 1];
            }
            if (hasColors) {
                interleaved[o + 8] = data.colors[i * 4];
                interleaved[o + 9] = data.colors[i * 4 + 1];
                interleaved[o + 10] = data.colors[i * 4 + 2];
                interleaved[o + 11] = data.colors[i * 4 + 3];
            } else {
                interleaved[o + 8] = 1f;
                interleaved[o + 9] = 1f;
                interleaved[o + 10] = 1f;
                interleaved[o + 11] = 1f;
            }
        }
        return interleaved;
    }

    private void flattenOnto(MeshGpu mesh, List<NiAvObject> path, boolean hasColors) {
        NiTexturingProperty tex = null;
        NiZBufferProperty z = null;
        NiStencilProperty stencil = null;
        List<NifRecord> drawable = new ArrayList<>();
        for (NiAvObject node : path) {
            for (int idx : node.properties) {
                NifRecord p = nif.get(idx);
                if (p instanceof NiTexturingProperty t) {
                    tex = t;
                } else if (p instanceof NiZBufferProperty zz) {
                    z = zz;
                } else if (p instanceof NiStencilProperty st) {
                    stencil = st;
                } else if (p instanceof NiMaterialProperty || p instanceof NiVertexColorProperty
                    || p instanceof NiSpecularProperty || p instanceof NiAlphaProperty) {
                    drawable.add(p);
                }
            }
        }

        mesh.colorMode = hasColors ? MeshGpu.COLOR_AMB_DIFF : MeshGpu.COLOR_NONE;
        mesh.ambient[0] = mesh.ambient[1] = mesh.ambient[2] = 1f;
        mesh.diffuse[0] = mesh.diffuse[1] = mesh.diffuse[2] = 1f;
        mesh.emissive[0] = mesh.emissive[1] = mesh.emissive[2] = 0f;
        mesh.matAlpha = 1f;
        int lightingMode = NiVertexColorProperty.LIGHT_EMI_AMB_DIFF;
        for (NifRecord p : drawable) {
            if (p instanceof NiSpecularProperty) {
                // Morrowind forces specular off regardless of this flag.
            } else if (p instanceof NiMaterialProperty mat) {
                System.arraycopy(mat.ambient, 0, mesh.ambient, 0, 3);
                System.arraycopy(mat.diffuse, 0, mesh.diffuse, 0, 3);
                System.arraycopy(mat.emissive, 0, mesh.emissive, 0, 3);
                mesh.matAlpha = mat.alpha;
            } else if (p instanceof NiVertexColorProperty vc) {
                switch (vc.vertexMode) {
                    case NiVertexColorProperty.VERT_IGNORE -> mesh.colorMode = MeshGpu.COLOR_NONE;
                    case NiVertexColorProperty.VERT_EMISSIVE -> mesh.colorMode = MeshGpu.COLOR_EMISSION;
                    case NiVertexColorProperty.VERT_AMB_DIFF -> {
                        lightingMode = vc.lightingMode;
                        if (lightingMode == NiVertexColorProperty.LIGHT_EMISSIVE) {
                            mesh.colorMode = MeshGpu.COLOR_NONE;
                        } else {
                            mesh.colorMode = MeshGpu.COLOR_AMB_DIFF;
                        }
                    }
                    default -> {
                    }
                }
            } else if (p instanceof NiAlphaProperty a) {
                mesh.alphaBlend = a.blending();
                mesh.alphaTest = a.testing();
                mesh.noSorter = a.noSorter();
                mesh.blendSrc = glBlend(a.sourceBlend());
                mesh.blendDst = glBlend(a.destBlend());
                if (mesh.blendDst == GL20.GL_DST_ALPHA) {
                    mesh.blendDst = GL20.GL_ONE;
                }
                mesh.alphaFunc = a.testMode();
                if (mesh.alphaFunc < 0 || mesh.alphaFunc > 7) {
                    mesh.alphaFunc = 3;
                }
                mesh.alphaRef = a.threshold / 255f;
            }
        }
        if (lightingMode == NiVertexColorProperty.LIGHT_EMISSIVE) {
            mesh.diffuse[0] = mesh.diffuse[1] = mesh.diffuse[2] = 0f;
            mesh.ambient[0] = mesh.ambient[1] = mesh.ambient[2] = 0f;
        }
        if (!hasColors) {
            switch (mesh.colorMode) {
                case MeshGpu.COLOR_AMB_DIFF -> {
                    mesh.ambient[0] = mesh.ambient[1] = mesh.ambient[2] = 1f;
                    mesh.diffuse[0] = mesh.diffuse[1] = mesh.diffuse[2] = 1f;
                }
                case MeshGpu.COLOR_EMISSION -> {
                    mesh.emissive[0] = mesh.emissive[1] = mesh.emissive[2] = 1f;
                }
                default -> {
                }
            }
            mesh.colorMode = MeshGpu.COLOR_NONE;
        }

        if (z != null) {
            mesh.depthTest = z.depthTest();
            mesh.depthWrite = z.depthWrite();
        }
        boolean stencilBoth = stencil != null && stencil.drawMode == NiStencilProperty.DRAW_BOTH;
        boolean twoSided = mesh.alphaTest || mesh.alphaBlend;
        mesh.cull = !twoSided && !stencilBoth;

        bindTextures(mesh, tex, path.get(path.size() - 1).name);

        flattenLog.append(path.get(path.size() - 1).name)
            .append(" colorMode=").append(mesh.colorMode)
            .append(" hasVCol=").append(hasColors)
            .append(" dark=").append(mesh.useDark)
            .append(" detail=").append(mesh.useDetail)
            .append(" glow=").append(mesh.useGlow)
            .append(" blend=").append(mesh.alphaBlend)
            .append(" test=").append(mesh.alphaTest)
            .append(" func=").append(mesh.alphaFunc)
            .append('\n');
    }

    private void bindTextures(MeshGpu mesh, NiTexturingProperty tex, String nodeName) {
        mesh.baseTex = whiteTex;
        mesh.darkTex = whiteTex;
        mesh.detailTex = whiteTex;
        mesh.glowTex = whiteTex;
        mesh.textureId = whiteTex;
        if (tex == null) {
            return;
        }
        if (tex.applyMode != 2) {
            Gdx.app.log("NifSceneBuilder", "ApplyMode " + tex.applyMode + " on " + nodeName + " (still modulate)");
        }
        for (int i = 0; i < tex.textures.size(); i++) {
            NiTexturingProperty.TextureSlot slot = tex.textures.get(i);
            if (!slot.enabled) {
                continue;
            }
            if (i == 3 || i == 5 || i == 6) {
                Gdx.app.log("NifSceneBuilder", "Unused texture stage " + i + " on " + nodeName);
                continue;
            }
            if (i != 0 && i != 1 && i != 2 && i != 4) {
                Gdx.app.log("NifSceneBuilder", "Unhandled texture stage " + i + " on " + nodeName);
                continue;
            }
            int id = resolveSource(slot);
            int wrapS = wrapGl(slot.wrapS());
            int wrapT = wrapGl(slot.wrapT());
            switch (i) {
                case 0 -> {
                    mesh.baseTex = id;
                    mesh.textureId = id;
                    mesh.baseWrapS = wrapS;
                    mesh.baseWrapT = wrapT;
                }
                case 1 -> {
                    mesh.darkTex = id;
                    mesh.darkWrapS = wrapS;
                    mesh.darkWrapT = wrapT;
                    mesh.useDark = true;
                }
                case 2 -> {
                    mesh.detailTex = id;
                    mesh.detailWrapS = wrapS;
                    mesh.detailWrapT = wrapT;
                    mesh.useDetail = true;
                }
                case 4 -> {
                    mesh.glowTex = id;
                    mesh.glowWrapS = wrapS;
                    mesh.glowWrapT = wrapT;
                    mesh.useGlow = true;
                }
                default -> {
                }
            }
        }
    }

    private int resolveSource(NiTexturingProperty.TextureSlot slot) {
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
        return whiteTex;
    }

    private static int wrapGl(boolean wrap) {
        return wrap ? GL20.GL_REPEAT : GL20.GL_CLAMP_TO_EDGE;
    }

    private static int glBlend(int mode) {
        return switch (mode) {
            case 0 -> GL20.GL_ONE;
            case 1 -> GL20.GL_ZERO;
            case 2 -> GL20.GL_SRC_COLOR;
            case 3 -> GL20.GL_ONE_MINUS_SRC_COLOR;
            case 4 -> GL20.GL_DST_COLOR;
            case 5 -> GL20.GL_ONE_MINUS_DST_COLOR;
            case 6 -> GL20.GL_SRC_ALPHA;
            case 7 -> GL20.GL_ONE_MINUS_SRC_ALPHA;
            case 8 -> GL20.GL_DST_ALPHA;
            case 9 -> GL20.GL_ONE_MINUS_DST_ALPHA;
            case 10 -> GL20.GL_SRC_ALPHA_SATURATE;
            default -> GL20.GL_SRC_ALPHA;
        };
    }

    private List<NiAvObject> pathTo(NiAvObject target) {
        List<NiAvObject> path = new ArrayList<>();
        for (int idx : nif.roots) {
            if (walkPath(idx, target, path)) {
                return path;
            }
        }
        path.add(target);
        return path;
    }

    private boolean walkPath(int idx, NiAvObject target, List<NiAvObject> path) {
        NifRecord rec = nif.get(idx);
        if (!(rec instanceof NiAvObject av)) {
            return false;
        }
        path.add(av);
        if (av == target) {
            return true;
        }
        if (av instanceof NiNode node) {
            for (int child : node.children) {
                if (walkPath(child, target, path)) {
                    return true;
                }
            }
        }
        path.remove(path.size() - 1);
        return false;
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
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    }
}

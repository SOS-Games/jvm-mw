package io.github.jvmmw.render;

import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmStatic;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Place STAT refs from one interior. Rewrite of {@code MWWorld::Scene} insert for statics. */
public final class CellSceneBuilder {
    public final StringBuilder log = new StringBuilder();
    public int placed;
    public int skippedUnknown;
    public int skippedDeleted;
    public int skippedNif;
    public String cellName = "";

    private final List<NifSceneBuilder> builders = new ArrayList<>();
    private final Map<String, SceneNode> templates = new HashMap<>();

    public SceneNode build(EsmFile.LoadedCell cell) {
        cellName = cell.name;
        placed = skippedUnknown = skippedDeleted = skippedNif = 0;
        log.setLength(0);
        SceneNode root = new SceneNode();
        root.name = "cell-root:" + cell.name;
        root.local.setToRotation(1, 0, 0, -90);
        for (CellRef ref : cell.refs) {
            if (ref.deleted) {
                skippedDeleted++;
                continue;
            }
            if (ref.refId.isEmpty()) {
                skippedUnknown++;
                continue;
            }
            EsmStatic st = cell.statics.get(ref.refId.toLowerCase(Locale.ROOT));
            if (st == null || st.model.isEmpty()) {
                skippedUnknown++;
                log.append("skip id=").append(ref.refId).append('\n');
                continue;
            }
            try {
                SceneNode inst = instance(st.model);
                EsmTransforms.setLocal(inst.local, ref.pos, ref.rot, ref.scale);
                inst.name = ref.refId;
                root.addChild(inst);
                placed++;
            } catch (Exception e) {
                skippedNif++;
                log.append("nif fail ").append(ref.refId).append(' ').append(st.model).append(" ")
                    .append(e.getMessage()).append('\n');
                Gdx.app.error("CellSceneBuilder", "STAT " + ref.refId, e);
            }
        }
        Matrix4 id = new Matrix4();
        root.updateWorld(id);
        log.insert(0, "cell=" + cell.name + " refs=" + cell.refs.size() + " placed=" + placed
            + " unknown=" + skippedUnknown + " deleted=" + skippedDeleted + " nifFail=" + skippedNif + '\n');
        return root;
    }

    public void dispose() {
        for (NifSceneBuilder b : builders) {
            b.dispose();
        }
        builders.clear();
        templates.clear();
    }

    private SceneNode instance(String model) throws Exception {
        String vfs = TexturePaths.normalizeMeshPath(model);
        SceneNode template = templates.get(vfs);
        if (template == null) {
            Path nifPath = TestData.ensureNif(vfs);
            byte[] bytes = Files.readAllBytes(nifPath);
            NifFile nif = NifFile.parse(bytes, vfs);
            NifSceneBuilder builder = new NifSceneBuilder(nif, TestData.testdataRoot(), p ->
                Files.isRegularFile(TestData.testdataRoot().resolve(p.replace('/', java.io.File.separatorChar))));
            builders.add(builder);
            template = builder.build(false);
            templates.put(vfs, template);
        }
        return cloneTree(template);
    }

    private static SceneNode cloneTree(SceneNode src) {
        SceneNode n = new SceneNode();
        n.name = src.name;
        n.local.set(src.local);
        n.skipMeshes = src.skipMeshes;
        n.meshes.addAll(src.meshes);
        for (SceneNode child : src.children) {
            n.addChild(cloneTree(child));
        }
        return n;
    }
}

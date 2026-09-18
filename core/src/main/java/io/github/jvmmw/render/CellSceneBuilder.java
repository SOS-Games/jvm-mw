package io.github.jvmmw.render;

import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmNpc;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Place cell refs that have a MODL. Rewrite of {@code MWWorld::Scene} insert for non-actors. */
public final class CellSceneBuilder {
    public final StringBuilder log = new StringBuilder();
    public int placed;
    public int skippedUnknown;
    public int skippedEmpty;
    public int skippedActor;
    public int placedNpc;
    public int skippedDeleted;
    public int skippedNif;
    public int placedStat;
    public String cellName = "";
    public final CellLighting lighting = new CellLighting();

    private final List<NifSceneBuilder> builders = new ArrayList<>();
    private final NpcMannequin mannequin = new NpcMannequin(TestData.testdataRoot());
    private final Map<String, SceneNode> templates = new HashMap<>();
    private EsmFile.LoadedCell cell;
    private SceneNode buildingRoot;
    private Map<String, Integer> byRec;
    private int refIndex;
    private boolean finished;
    private final List<PendingLight> pendingLights = new ArrayList<>();
    private final Vector3 tmpPos = new Vector3();

    public SceneNode build(EsmFile.LoadedCell cell) {
        begin(cell);
        while (!step(Long.MAX_VALUE)) {
            // place remaining refs
        }
        return end();
    }

    public void begin(EsmFile.LoadedCell cell) {
        this.cell = cell;
        cellName = cell.name;
        placed = skippedUnknown = skippedEmpty = skippedActor = skippedDeleted = skippedNif = placedStat = placedNpc = 0;
        log.setLength(0);
        byRec = new TreeMap<>();
        buildingRoot = new SceneNode();
        buildingRoot.name = "cell-root:" + cell.name;
        buildingRoot.local.setToRotation(1, 0, 0, -90);
        refIndex = 0;
        finished = false;
        pendingLights.clear();
        lighting.lights.clear();
        lighting.resetTime();
        System.arraycopy(cell.ambient, 0, lighting.ambient, 0, 3);
        System.arraycopy(cell.sunlight, 0, lighting.sunDiffuse, 0, 3);
        lighting.configureFog(cell.fogColor, cell.fogDensity);
    }

    /** Place refs until {@code budgetNanos} elapses. Returns true when the cell is done. */
    public boolean step(long budgetNanos) {
        if (finished) {
            return true;
        }
        long start = System.nanoTime();
        while (refIndex < cell.refs.size()) {
            place(cell.refs.get(refIndex++));
            if (System.nanoTime() - start >= budgetNanos) {
                return false;
            }
        }
        Matrix4 id = new Matrix4();
        buildingRoot.updateWorld(id);
        finishLights();
        log.insert(0, "cell=" + cell.name + " refs=" + cell.refs.size() + " placed=" + placed
            + " npc=" + placedNpc + " byRec=" + byRec + " empty=" + skippedEmpty + " actor=" + skippedActor
            + " unknown=" + skippedUnknown + " deleted=" + skippedDeleted + " nifFail=" + skippedNif
            + " lights=" + lighting.lights.size() + " fog=" + lighting.fogDensity + '\n');
        finished = true;
        return true;
    }

    public SceneNode end() {
        return buildingRoot;
    }

    public void update(float dt) {
        mannequin.update(dt);
        if (buildingRoot != null) {
            Matrix4 id = new Matrix4();
            buildingRoot.updateWorld(id);
        }
    }

    public int refCount() {
        return cell == null ? 0 : cell.refs.size();
    }

    public int refIndex() {
        return refIndex;
    }

    private void place(CellRef ref) {
        if (ref.deleted) {
            skippedDeleted++;
            return;
        }
        if (ref.refId.isEmpty()) {
            skippedUnknown++;
            return;
        }
        String key = ref.refId.toLowerCase(Locale.ROOT);
        if (EsmFile.isHiddenMarker(ref.refId)) {
            skippedUnknown++;
            log.append("skip marker=").append(ref.refId).append('\n');
            return;
        }
        if (cell.actorIds.contains(key)) {
            EsmNpc npc = cell.npcs.get(key);
            if (npc == null) {
                skippedActor++;
                log.append("skip actor=").append(ref.refId).append('\n');
                return;
            }
            try {
                SceneNode inst = mannequin.build(npc, ref, cell);
                buildingRoot.addChild(inst);
                placed++;
                placedNpc++;
                byRec.merge("NPC_", 1, Integer::sum);
            } catch (Exception e) {
                skippedNif++;
                log.append("npc fail ").append(ref.refId).append(" ").append(e.getMessage()).append('\n');
                Gdx.app.error("CellSceneBuilder", "NPC " + ref.refId, e);
            }
            return;
        }
        EsmObject obj = cell.objects.get(key);
        if (obj == null) {
            skippedUnknown++;
            log.append("skip id=").append(ref.refId).append('\n');
            return;
        }
        if (obj.model.isEmpty()) {
            skippedEmpty++;
            log.append("skip empty=").append(obj.rec).append(' ').append(ref.refId).append('\n');
            if (isEmittingLight(obj)) {
                SceneNode inst = new SceneNode();
                inst.name = ref.refId;
                EsmTransforms.setLocal(inst.local, ref.pos, ref.rot, ref.scale);
                buildingRoot.addChild(inst);
                pendingLights.add(new PendingLight(inst, obj, ref.refId));
            }
            return;
        }
        try {
            SceneNode inst = instance(obj.model);
            EsmTransforms.setLocal(inst.local, ref.pos, ref.rot, ref.scale);
            inst.name = ref.refId;
            buildingRoot.addChild(inst);
            placed++;
            if ("STAT".equals(obj.rec)) {
                placedStat++;
            }
            byRec.merge(obj.rec, 1, Integer::sum);
            if (isEmittingLight(obj)) {
                pendingLights.add(new PendingLight(inst, obj, ref.refId));
            }
        } catch (Exception e) {
            skippedNif++;
            log.append("nif fail ").append(obj.rec).append(' ').append(ref.refId).append(' ').append(obj.model)
                .append(" ").append(e.getMessage()).append('\n');
            Gdx.app.error("CellSceneBuilder", obj.rec + " " + ref.refId, e);
            if (isEmittingLight(obj)) {
                SceneNode inst = new SceneNode();
                inst.name = ref.refId;
                EsmTransforms.setLocal(inst.local, ref.pos, ref.rot, ref.scale);
                buildingRoot.addChild(inst);
                pendingLights.add(new PendingLight(inst, obj, ref.refId));
            }
        }
    }

    private static boolean isEmittingLight(EsmObject obj) {
        return obj.hasLight && (obj.lightFlags & EsmObject.LIGH_OFF_DEFAULT) == 0;
    }

    private void finishLights() {
        tmpPos.set(1f, (float) -Math.toRadians(45), (float) -Math.toRadians(45));
        tmpPos.rot(buildingRoot.world);
        tmpPos.nor();
        lighting.sunDir[0] = tmpPos.x;
        lighting.sunDir[1] = tmpPos.y;
        lighting.sunDir[2] = tmpPos.z;
        for (PendingLight pending : pendingLights) {
            SceneNode attach = findAttachLight(pending.node);
            if (attach == null) {
                attach = pending.node;
            }
            attach.world.getTranslation(tmpPos);
            CellLight light = new CellLight();
            light.pos[0] = tmpPos.x;
            light.pos[1] = tmpPos.y;
            light.pos[2] = tmpPos.z;
            float radius = Math.max(pending.obj.lightRadius, 16f);
            EsmFile.colourFromRgb(pending.obj.lightColor, light.baseDiffuse);
            if ((pending.obj.lightFlags & EsmObject.LIGH_NEGATIVE) != 0) {
                light.baseDiffuse[0] *= -1f;
                light.baseDiffuse[1] *= -1f;
                light.baseDiffuse[2] *= -1f;
            }
            System.arraycopy(light.baseDiffuse, 0, light.diffuse, 0, 3);
            light.radius = radius;
            light.constant = 0f;
            light.linear = 3f / radius;
            light.quadratic = 0f;
            light.type = CellLight.typeFromFlags(pending.obj.lightFlags);
            if (light.type != CellLight.TYPE_NORMAL) {
                light.phase = lighting.rollPhase();
                light.brightness = 0.675f;
            }
            lighting.lights.add(light);
            log.append("light ").append(pending.refId).append(" r=").append((int) radius)
                .append(" rgb=").append(light.baseDiffuse[0]).append(',').append(light.baseDiffuse[1]).append(',')
                .append(light.baseDiffuse[2]);
            if (light.type != CellLight.TYPE_NORMAL) {
                log.append(" type=").append(light.type);
            }
            if (attach != pending.node) {
                log.append(" attach=").append(attach.name);
            }
            int ignored = pending.obj.lightFlags & EsmObject.LIGH_IGNORABLE;
            if (ignored != 0) {
                log.append(" ignoreFlags=0x").append(Integer.toHexString(ignored));
            }
            log.append('\n');
        }
    }

    private static SceneNode findAttachLight(SceneNode node) {
        if ("AttachLight".equals(node.name)) {
            return node;
        }
        for (SceneNode child : node.children) {
            SceneNode found = findAttachLight(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private record PendingLight(SceneNode node, EsmObject obj, String refId) {
    }

    public void dispose() {
        mannequin.dispose();
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
        return NpcMannequin.cloneTree(template);
    }
}

/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import io.github.jvmmw.nif.KfFile;
import io.github.jvmmw.nif.NiKeyframeController;
import io.github.jvmmw.nif.NiKeyframeData;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chest lid {@code containeropen}. Rewrite of {@code ActionOpen} /
 * {@code CharacterController::onOpen} without {@code GM_Container}.
 */
public final class ContainerOpen {
    public static final class Hit {
        public final Placed container;
        public final float dist;

        Hit(Placed container, float dist) {
            this.container = container;
            this.dist = dist;
        }
    }

    public static final class Placed {
        public final SceneNode node;
        public final String refId;
        public final KfFile kf;
        final Map<String, SceneNode> bones = new HashMap<>();
        final List<Bind> bindings = new ArrayList<>();
        KfFile.IdleLoop playing;
        boolean moving;
        boolean opened;

        Placed(SceneNode node, String refId, KfFile kf) {
            this.node = node;
            this.refId = refId;
            this.kf = kf;
            index(node);
        }

        boolean hasOpen() {
            return kf != null && kf.play("containeropen", "start", "stop", false) != null;
        }

        private void index(SceneNode n) {
            if (n.name != null && !n.name.isEmpty()) {
                bones.putIfAbsent(n.name.toLowerCase(Locale.ROOT), n);
            }
            for (SceneNode child : n.children) {
                index(child);
            }
        }
    }

    private static final class Bind {
        final SceneNode node;
        final Matrix4 rest = new Matrix4();
        final NiKeyframeController controller;
        final NiKeyframeData data;

        Bind(SceneNode node, NiKeyframeController controller, NiKeyframeData data) {
            this.node = node;
            this.controller = controller;
            this.data = data;
        }
    }

    public final List<Placed> containers = new ArrayList<>();
    private final Map<String, KfFile> kfs = new HashMap<>();
    private final Ray ray = new Ray();
    private final BoundingBox box = new BoundingBox();
    private final Vector3 hit = new Vector3();
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmpS = new Vector3();
    private final Quaternion tmpQ = new Quaternion();
    private final Quaternion tmpQ2 = new Quaternion();
    private final Quaternion tmpQ3 = new Quaternion();

    public void clear() {
        containers.clear();
    }

    public Placed add(SceneNode node, String refId, String model) {
        KfFile kf = loadKf(model);
        Placed placed = new Placed(node, refId, kf);
        containers.add(placed);
        return placed;
    }

    public int withOpen() {
        int n = 0;
        for (Placed c : containers) {
            if (c.hasOpen()) {
                n++;
            }
        }
        return n;
    }

    public Hit nearest(Vector3 origin, Vector3 direction) {
        ray.set(origin, direction);
        Hit best = null;
        for (Placed c : containers) {
            box.inf();
            c.node.collectAabb(box);
            if (!box.isValid() || !Intersector.intersectRayBounds(ray, box, hit)) {
                continue;
            }
            float dist = origin.dst(hit);
            if (dist <= DoorSwing.MAX_ACTIVATE && (best == null || dist < best.dist)) {
                best = new Hit(c, dist);
            }
        }
        return best;
    }

    public String activate(Hit picked) {
        Placed c = picked.container;
        if (!c.hasOpen()) {
            return "cont " + c.refId + " no containeropen";
        }
        if (c.moving) {
            return "cont " + c.refId + " busy";
        }
        if (c.opened) {
            return "cont " + c.refId + " open";
        }
        KfFile.IdleLoop loop = c.kf.play("containeropen", "start", "stop", false);
        c.bindings.clear();
        for (KfFile.BoneTrack track : c.kf.tracks.values()) {
            SceneNode node = c.bones.get(track.bone.toLowerCase(Locale.ROOT));
            if (node == null) {
                Gdx.app.log("ContainerOpen", "missing bone " + track.bone + " on " + c.refId);
                continue;
            }
            Bind bind = new Bind(node, track.controller, track.data);
            bind.rest.set(node.local);
            c.bindings.add(bind);
        }
        c.playing = loop;
        c.moving = true;
        c.opened = true;
        pose(c);
        return "cont " + c.refId + " containeropen";
    }

    public void process(float dt) {
        for (Placed c : containers) {
            if (!c.moving || c.playing == null) {
                continue;
            }
            c.playing.time += dt;
            if (c.playing.time >= c.playing.stopTime) {
                c.playing.time = c.playing.stopTime;
                pose(c);
                c.moving = false;
            } else {
                pose(c);
            }
        }
    }

    private KfFile loadKf(String model) {
        String mesh = TexturePaths.normalizeMeshPath(model);
        String anim = TexturePaths.correctActorModelPath(mesh, TestData::vfsExists);
        String kfPath = TexturePaths.nifToKf(anim);
        if (!TestData.vfsExists(kfPath)) {
            return null;
        }
        KfFile kf = kfs.get(kfPath);
        if (kf != null) {
            return kf;
        }
        try {
            Path file = TestData.ensureNif(kfPath);
            NifFile nif = NifFile.parse(Files.readAllBytes(file), kfPath);
            kf = KfFile.load(nif, kfPath);
            kfs.put(kfPath, kf);
            return kf;
        } catch (Exception e) {
            Gdx.app.error("ContainerOpen", kfPath, e);
            return null;
        }
    }

    private void pose(Placed c) {
        float animTime = c.playing.time;
        for (Bind bind : c.bindings) {
            float time = bind.controller.sampleTime(animTime);
            bind.rest.getTranslation(tmp);
            bind.rest.getRotation(tmpQ, true);
            bind.rest.getScale(tmpS);
            NiKeyframeData data = bind.data;
            if (!data.rotations.empty()) {
                data.rotations.interpQuat(time, tmpQ);
            } else if (!data.xRot.empty() || !data.yRot.empty() || !data.zRot.empty()) {
                xyzQuat(data, time, tmpQ);
            }
            if (!data.translations.empty()) {
                data.translations.interpVec3(time, tmp);
            }
            if (!data.scales.empty()) {
                float sc = data.scales.interpFloat(time);
                tmpS.set(sc, sc, sc);
            }
            bind.node.local.set(tmp, tmpQ, tmpS);
        }
    }

    private void xyzQuat(NiKeyframeData data, float time, Quaternion out) {
        float x = data.xRot.empty() ? 0f : data.xRot.interpFloat(time);
        float y = data.yRot.empty() ? 0f : data.yRot.interpFloat(time);
        float z = data.zRot.empty() ? 0f : data.zRot.interpFloat(time);
        tmpQ2.setFromAxisRad(1, 0, 0, x);
        tmpQ3.setFromAxisRad(0, 1, 0, y);
        Quaternion zr = new Quaternion().setFromAxisRad(0, 0, 1, z);
        switch (data.axisOrder) {
            case 1 -> out.set(tmpQ2).mul(zr).mul(tmpQ3);
            case 2 -> out.set(tmpQ3).mul(zr).mul(tmpQ2);
            case 3 -> out.set(tmpQ3).mul(tmpQ2).mul(zr);
            case 4 -> out.set(zr).mul(tmpQ2).mul(tmpQ3);
            case 5 -> out.set(zr).mul(tmpQ3).mul(tmpQ2);
            default -> out.set(tmpQ2).mul(tmpQ3).mul(zr);
        }
    }
}

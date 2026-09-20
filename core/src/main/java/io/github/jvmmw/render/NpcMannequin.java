/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmBodyPart;
import io.github.jvmmw.esm.EsmCreature;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmNpc;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.esm.EsmPartRef;
import io.github.jvmmw.esm.EsmRace;
import io.github.jvmmw.nif.KfFile;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NiKeyframeController;
import io.github.jvmmw.nif.NiKeyframeData;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NPC with idle {@code .kf}. Rewrite of {@code MWRender::NpcAnimation} bind-pose plus idle.
 */
public final class NpcMannequin {
    public static final int PRT_COUNT = 27;
    public static final int PRT_HEAD = 0;
    public static final int PRT_HAIR = 1;
    public static final int PRT_NECK = 2;
    public static final int PRT_CUIRASS = 3;
    public static final int PRT_GROIN = 4;
    public static final int PRT_SKIRT = 5;
    public static final int PRT_RHAND = 6;
    public static final int PRT_LHAND = 7;
    public static final int PRT_RWRIST = 8;
    public static final int PRT_LWRIST = 9;
    public static final int PRT_SHIELD = 10;
    public static final int PRT_RFOREARM = 11;
    public static final int PRT_LFOREARM = 12;
    public static final int PRT_RUPPERARM = 13;
    public static final int PRT_LUPPERARM = 14;
    public static final int PRT_RFOOT = 15;
    public static final int PRT_LFOOT = 16;
    public static final int PRT_RANKLE = 17;
    public static final int PRT_LANKLE = 18;
    public static final int PRT_RKNEE = 19;
    public static final int PRT_LKNEE = 20;
    public static final int PRT_RLEG = 21;
    public static final int PRT_LLEG = 22;
    public static final int PRT_RPAULDRON = 23;
    public static final int PRT_LPAULDRON = 24;
    public static final int PRT_WEAPON = 25;
    public static final int PRT_TAIL = 26;

    public static final int SLOT_HELMET = 0;
    public static final int SLOT_CUIRASS = 1;
    public static final int SLOT_GREAVES = 2;
    public static final int SLOT_LEFT_PAULDRON = 3;
    public static final int SLOT_RIGHT_PAULDRON = 4;
    public static final int SLOT_LEFT_GAUNTLET = 5;
    public static final int SLOT_RIGHT_GAUNTLET = 6;
    public static final int SLOT_BOOTS = 7;
    public static final int SLOT_SHIRT = 8;
    public static final int SLOT_PANTS = 9;
    public static final int SLOT_SKIRT = 10;
    public static final int SLOT_ROBE = 11;
    public static final int SLOTS = 19;

    private static final String[] PART_BONE = {
        "Head", "Head", "Neck", "Chest", "Groin", "Groin",
        "Right Hand", "Left Hand", "Right Wrist", "Left Wrist", "Shield Bone",
        "Right Forearm", "Left Forearm", "Right Upper Arm", "Left Upper Arm",
        "Right Foot", "Left Foot", "Right Ankle", "Left Ankle",
        "Right Knee", "Left Knee", "Right Upper Leg", "Left Upper Leg",
        "Right Clavicle", "Left Clavicle", "Weapon Bone", "Tail"
    };

    private static final int[][] SLOT_LIST = {
        {SLOT_ROBE, 11}, {SLOT_SKIRT, 3}, {SLOT_HELMET, 0}, {SLOT_CUIRASS, 0},
        {SLOT_GREAVES, 0}, {SLOT_LEFT_PAULDRON, 0}, {SLOT_RIGHT_PAULDRON, 0}, {SLOT_BOOTS, 0},
        {SLOT_LEFT_GAUNTLET, 0}, {SLOT_RIGHT_GAUNTLET, 0}, {SLOT_SHIRT, 0}, {SLOT_PANTS, 0}
    };

    private static final int[] ROBE_RESERVE = {
        PRT_GROIN, PRT_SKIRT, PRT_RLEG, PRT_LLEG, PRT_RUPPERARM, PRT_LUPPERARM,
        PRT_RKNEE, PRT_LKNEE, PRT_RFOREARM, PRT_LFOREARM, PRT_CUIRASS
    };

    private static final int[][] SKIN_MAP = {
        {EsmBodyPart.MP_NECK, PRT_NECK},
        {EsmBodyPart.MP_CHEST, PRT_CUIRASS},
        {EsmBodyPart.MP_GROIN, PRT_GROIN},
        {EsmBodyPart.MP_HAND, PRT_RHAND}, {EsmBodyPart.MP_HAND, PRT_LHAND},
        {EsmBodyPart.MP_WRIST, PRT_RWRIST}, {EsmBodyPart.MP_WRIST, PRT_LWRIST},
        {EsmBodyPart.MP_FOREARM, PRT_RFOREARM}, {EsmBodyPart.MP_FOREARM, PRT_LFOREARM},
        {EsmBodyPart.MP_UPPERARM, PRT_RUPPERARM}, {EsmBodyPart.MP_UPPERARM, PRT_LUPPERARM},
        {EsmBodyPart.MP_FOOT, PRT_RFOOT}, {EsmBodyPart.MP_FOOT, PRT_LFOOT},
        {EsmBodyPart.MP_ANKLE, PRT_RANKLE}, {EsmBodyPart.MP_ANKLE, PRT_LANKLE},
        {EsmBodyPart.MP_KNEE, PRT_RKNEE}, {EsmBodyPart.MP_KNEE, PRT_LKNEE},
        {EsmBodyPart.MP_UPPERLEG, PRT_RLEG}, {EsmBodyPart.MP_UPPERLEG, PRT_LLEG},
        {EsmBodyPart.MP_TAIL, PRT_TAIL}
    };

    private static final String XBASE = "meshes/xbase_anim.nif";

    private final Path testdata;
    private final List<MeshGpu> ownedGpus = new ArrayList<>();
    private final Map<String, PartNif> parts = new HashMap<>();
    private final Map<String, KfFile> kfs = new HashMap<>();
    private final List<NpcActor> actors = new ArrayList<>();
    private final Matrix4 id = new Matrix4();
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmpS = new Vector3();
    private final Quaternion tmpQ = new Quaternion();
    private final Quaternion tmpQ2 = new Quaternion();
    private final Quaternion tmpQ3 = new Quaternion();

    public NpcMannequin(Path testdata) {
        this.testdata = testdata;
    }

    public void dispose() {
        for (MeshGpu gpu : ownedGpus) {
            gpu.dispose();
        }
        ownedGpus.clear();
        parts.clear();
        kfs.clear();
        actors.clear();
    }

    public SceneNode build(EsmNpc npc, CellRef ref, EsmFile.LoadedCell cell) throws Exception {
        String defaultSkel = defaultSkeletonPath(npc, cell);
        String smodel = defaultSkel;
        boolean custom = false;
        if (!npc.model.isEmpty()) {
            String mesh = TexturePaths.normalizeMeshPath(npc.model);
            if (!isDefaultSkeleton(mesh)) {
                smodel = TexturePaths.correctActorModelPath(mesh, TestData::vfsExists);
                custom = true;
            }
        }
        SceneNode skeleton = cloneTree(skeleton(smodel));
        SceneNode placed = new SceneNode();
        placed.name = npc.id;
        placed.actor = true;
        placed.addChild(skeleton);
        skeleton.updateWorld(id);
        Map<String, Matrix4> boneWorld = new HashMap<>();
        Map<String, SceneNode> boneNodes = new HashMap<>();
        collectBones(skeleton, boneWorld, boneNodes);
        int[] priority = new int[PRT_COUNT];
        EsmObject[] equipped = autoEquip(npc, cell);
        for (int[] slot : SLOT_LIST) {
            EsmObject item = equipped[slot[0]];
            if (item == null) {
                continue;
            }
            int prio = ((slot[1] + 1) << 1) + ("ARMO".equals(item.rec) ? 1 : 0);
            addPartGroup(npc, cell, item, prio, priority, placed, boneWorld, boneNodes);
            if (slot[0] == SLOT_ROBE) {
                for (int p : ROBE_RESERVE) {
                    if (prio > priority[p]) {
                        priority[p] = prio;
                    }
                }
            } else if (slot[0] == SLOT_SKIRT) {
                if (prio > priority[PRT_GROIN]) {
                    priority[PRT_GROIN] = prio;
                }
                if (prio > priority[PRT_RLEG]) {
                    priority[PRT_RLEG] = prio;
                }
                if (prio > priority[PRT_LLEG]) {
                    priority[PRT_LLEG] = prio;
                }
            }
        }
        String headMesh = bodyModel(cell, npc.head);
        String hairMesh = bodyModel(cell, npc.hair);
        if (priority[PRT_HEAD] < 1 && !headMesh.isEmpty()) {
            attachPart(PRT_HEAD, headMesh, placed, boneWorld, boneNodes);
            priority[PRT_HEAD] = 1;
        }
        if (priority[PRT_HAIR] < 1 && priority[PRT_HEAD] <= 1 && !hairMesh.isEmpty()) {
            attachPart(PRT_HAIR, hairMesh, placed, boneWorld, boneNodes);
            priority[PRT_HAIR] = 1;
        }
        EsmBodyPart[] skins = racialSkin(npc, cell);
        for (int part = PRT_NECK; part < PRT_COUNT; part++) {
            if (priority[part] < 1 && skins[part] != null && !skins[part].model.isEmpty()) {
                attachPart(part, TexturePaths.normalizeMeshPath(skins[part].model), placed, boneWorld, boneNodes);
            }
        }
        NpcActor actor = new NpcActor(placed, skeleton, boneNodes);
        collectSkins(placed, actor.skins);
        addAnimSource(actor, XBASE);
        if (!defaultSkel.equals(XBASE)) {
            addAnimSource(actor, defaultSkel);
        }
        if (custom) {
            addAnimSource(actor, smodel);
        }
        playIdle(actor);
        pose(actor);
        actors.add(actor);
        EsmRace race = cell.races.get(npc.race.toLowerCase(Locale.ROOT));
        float weight = 1f;
        float height = 1f;
        if (race != null) {
            weight = npc.female() ? race.femaleWeight : race.maleWeight;
            height = npc.female() ? race.femaleHeight : race.maleHeight;
        }
        float s = ref.scale;
        EsmTransforms.setActorLocal(placed.local, ref.pos, ref.rot[2], s * weight, s * weight, s * height);
        return placed;
    }

    public SceneNode buildCreature(EsmCreature crea, CellRef ref) throws Exception {
        String mesh = TexturePaths.normalizeMeshPath(crea.model);
        String animationMesh = TexturePaths.correctActorModelPath(mesh, TestData::vfsExists);
        boolean animated = true;
        if (animationMesh.equals(mesh) && mesh.endsWith(".nif")) {
            animated = false;
        }
        NifSceneBuilder builder = loadNif(animationMesh);
        SceneNode skeleton = cloneTree(skeleton(animationMesh));
        SceneNode placed = new SceneNode();
        placed.name = crea.id;
        placed.actor = true;
        placed.addChild(skeleton);
        skeleton.updateWorld(id);
        Map<String, Matrix4> boneWorld = new HashMap<>();
        Map<String, SceneNode> boneNodes = new HashMap<>();
        collectBones(skeleton, boneWorld, boneNodes);
        builder.copyCreatureGeometry(boneWorld, placed, skeleton, ownedGpus);
        NpcActor actor = new NpcActor(placed, skeleton, boneNodes);
        collectSkins(placed, actor.skins);
        if (crea.bipedal()) {
            addAnimSource(actor, XBASE);
        }
        if (animated) {
            addAnimSource(actor, animationMesh);
        }
        playIdle(actor);
        pose(actor);
        actors.add(actor);
        float s = ref.scale * crea.scale;
        EsmTransforms.setActorLocal(placed.local, ref.pos, ref.rot[2], s, s, s);
        return placed;
    }

    public void update(float dt) {
        for (NpcActor actor : actors) {
            if (actor.idle == null) {
                continue;
            }
            actor.idle.time += dt;
            float span = actor.idle.loopStop - actor.idle.loopStart;
            if (span > 0f && actor.idle.time >= actor.idle.loopStop) {
                actor.idle.time = actor.idle.loopStart
                    + ((actor.idle.time - actor.idle.loopStart) % span);
            }
            pose(actor);
        }
    }

    public static String skeletonPath(EsmNpc npc, EsmFile.LoadedCell cell) {
        String def = defaultSkeletonPath(npc, cell);
        if (!npc.model.isEmpty()) {
            String custom = TexturePaths.normalizeMeshPath(npc.model);
            if (!isDefaultSkeleton(custom)) {
                return TexturePaths.correctActorModelPath(custom, TestData::vfsExists);
            }
        }
        return def;
    }

    public static String defaultSkeletonPath(EsmNpc npc, EsmFile.LoadedCell cell) {
        EsmRace race = cell.races.get(npc.race.toLowerCase(Locale.ROOT));
        boolean beast = race != null && race.beast();
        String base;
        if (beast) {
            base = "meshes/base_animkna.nif";
        } else if (npc.female()) {
            base = "meshes/base_anim_female.nif";
        } else {
            base = "meshes/base_anim.nif";
        }
        return TexturePaths.correctActorModelPath(base, TestData::vfsExists);
    }

    public static String describe(EsmNpc npc, EsmFile.LoadedCell cell) {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(npc.id)
            .append(" name=").append(npc.name)
            .append(" race=").append(npc.race)
            .append(" female=").append(npc.female())
            .append(" head=").append(npc.head)
            .append(" hair=").append(npc.hair)
            .append('\n');
        sb.append("skeleton=").append(skeletonPath(npc, cell)).append('\n');
        EsmObject[] equipped = autoEquip(npc, cell);
        for (int[] slot : SLOT_LIST) {
            EsmObject item = equipped[slot[0]];
            if (item == null) {
                continue;
            }
            sb.append("slot=").append(slot[0]).append(' ').append(item.rec).append(' ').append(item.id);
            for (EsmPartRef part : item.parts) {
                String bodyId = npc.female() && !part.female.isEmpty() ? part.female : part.male;
            EsmBodyPart body = cell.bodies.get(bodyId.toLowerCase(Locale.ROOT));
            sb.append(" [").append(part.part).append(':').append(bodyId);
            if (body != null && !body.model.isEmpty()) {
                sb.append('=').append(body.model);
            }
            sb.append(']');
            }
            sb.append('\n');
        }
        sb.append("kf=").append(TexturePaths.nifToKf(skeletonPath(npc, cell))).append('\n');
        return sb.toString();
    }

    public static String describeCreature(EsmCreature crea) {
        String mesh = TexturePaths.normalizeMeshPath(crea.model);
        String corrected = TexturePaths.correctActorModelPath(mesh, TestData::vfsExists);
        boolean animated = !(corrected.equals(mesh) && mesh.endsWith(".nif"));
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(crea.id)
            .append(" name=").append(crea.name)
            .append(" model=").append(crea.model)
            .append('\n');
        sb.append("corrected=").append(corrected)
            .append(" animated=").append(animated)
            .append('\n');
        sb.append("flags=0x").append(Integer.toHexString(crea.flags))
            .append(" biped=").append(crea.bipedal())
            .append(" weapon=").append(crea.weapon())
            .append(" swims=").append(crea.swims())
            .append(" flies=").append(crea.flies())
            .append(" walks=").append(crea.walks())
            .append(" scale=").append(crea.scale)
            .append('\n');
        sb.append("kf=").append(TexturePaths.nifToKf(corrected)).append('\n');
        return sb.toString();
    }

    public static EsmObject[] autoEquip(EsmNpc npc, EsmFile.LoadedCell cell) {
        EsmObject[] slots = new EsmObject[SLOTS];
        int[] kind = new int[SLOTS];
        for (String itemId : npc.inventory) {
            EsmObject obj = cell.objects.get(itemId.toLowerCase(Locale.ROOT));
            if (obj == null) {
                continue;
            }
            int slot = slotFor(obj);
            if (slot < 0) {
                continue;
            }
            boolean armor = "ARMO".equals(obj.rec);
            if (armor) {
                if (kind[slot] == 0 || kind[slot] == 1) {
                    slots[slot] = obj;
                    kind[slot] = 2;
                }
            } else {
                if (kind[slot] == 0 || (kind[slot] == 1 && obj.value > slots[slot].value)) {
                    slots[slot] = obj;
                    kind[slot] = 1;
                }
            }
        }
        return slots;
    }

    private void addPartGroup(EsmNpc npc, EsmFile.LoadedCell cell, EsmObject item, int prio, int[] priority,
        SceneNode actor, Map<String, Matrix4> boneWorld, Map<String, SceneNode> boneNodes) throws Exception {
        boolean female = npc.female();
        for (EsmPartRef ref : item.parts) {
            if (ref.part < 0 || ref.part >= PRT_COUNT) {
                continue;
            }
            if (prio <= priority[ref.part]) {
                continue;
            }
            String bodyId = female && !ref.female.isEmpty() ? ref.female : ref.male;
            if (bodyId.isEmpty() && !ref.male.isEmpty()) {
                bodyId = ref.male;
            }
            EsmBodyPart body = cell.bodies.get(bodyId.toLowerCase(Locale.ROOT));
            priority[ref.part] = prio;
            if (body == null || body.model.isEmpty()) {
                continue;
            }
            attachPart(ref.part, TexturePaths.normalizeMeshPath(body.model), actor, boneWorld, boneNodes);
        }
    }

    private void attachPart(int type, String mesh, SceneNode actor, Map<String, Matrix4> boneWorld,
        Map<String, SceneNode> boneNodes) throws Exception {
        String bone = PART_BONE[type];
        String filter = type == PRT_HAIR ? "hair" : bone;
        PartNif part = partNif(mesh);
        if (part.builder.isSkeleton()) {
            part.builder.copyMatchingSkinned(filter, boneWorld, actor, ownedGpus);
            return;
        }
        SceneNode attach = boneNodes.get(bone.toLowerCase(Locale.ROOT));
        if (attach == null) {
            return;
        }
        SceneNode clone = cloneTree(part.rigid);
        Vector3 offset = findBoneOffset(clone);
        stripBoneOffset(clone);
        boolean left = bone.contains("Left");
        SceneNode wrap = new SceneNode();
        wrap.name = "attach:" + filter;
        wrap.local.idt();
        if (offset != null) {
            wrap.local.translate(offset.x, offset.y, offset.z);
        }
        if (left) {
            wrap.local.scale(-1f, 1f, 1f);
            markClockwise(clone);
        }
        wrap.addChild(clone);
        attach.addChild(wrap);
    }

    private static EsmBodyPart[] racialSkin(EsmNpc npc, EsmFile.LoadedCell cell) {
        EsmBodyPart[] parts = new EsmBodyPart[PRT_COUNT];
        String race = npc.race.toLowerCase(Locale.ROOT);
        boolean female = npc.female();
        for (EsmBodyPart body : cell.bodies.values()) {
            if (body.notPlayable() || body.type != EsmBodyPart.MT_SKIN || body.vampire != 0) {
                continue;
            }
            if (!body.race.toLowerCase(Locale.ROOT).equals(race)) {
                continue;
            }
            if (body.firstPerson()) {
                continue;
            }
            boolean sameGender = body.female() == female;
            if (!sameGender && !(female && !body.female())) {
                continue;
            }
            for (int[] map : SKIN_MAP) {
                if (map[0] != body.part) {
                    continue;
                }
                int prt = map[1];
                if (parts[prt] == null || (sameGender && parts[prt].female() != female)) {
                    parts[prt] = body;
                }
            }
        }
        return parts;
    }

    private static String bodyModel(EsmFile.LoadedCell cell, String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        EsmBodyPart body = cell.bodies.get(id.toLowerCase(Locale.ROOT));
        if (body == null || body.model.isEmpty()) {
            return "";
        }
        return TexturePaths.normalizeMeshPath(body.model);
    }

    private static int slotFor(EsmObject obj) {
        if ("CLOT".equals(obj.rec)) {
            return switch (obj.clothType) {
                case EsmObject.CLOT_SHIRT -> SLOT_SHIRT;
                case EsmObject.CLOT_BELT -> -1;
                case EsmObject.CLOT_ROBE -> SLOT_ROBE;
                case EsmObject.CLOT_PANTS -> SLOT_PANTS;
                case EsmObject.CLOT_SHOES -> SLOT_BOOTS;
                case EsmObject.CLOT_LGLOVE -> SLOT_LEFT_GAUNTLET;
                case EsmObject.CLOT_RGLOVE -> SLOT_RIGHT_GAUNTLET;
                case EsmObject.CLOT_SKIRT -> SLOT_SKIRT;
                default -> -1;
            };
        }
        if ("ARMO".equals(obj.rec)) {
            return switch (obj.armorType) {
                case EsmObject.ARMO_HELMET -> SLOT_HELMET;
                case EsmObject.ARMO_CUIRASS -> SLOT_CUIRASS;
                case EsmObject.ARMO_LPAULDRON -> SLOT_LEFT_PAULDRON;
                case EsmObject.ARMO_RPAULDRON -> SLOT_RIGHT_PAULDRON;
                case EsmObject.ARMO_GREAVES -> SLOT_GREAVES;
                case EsmObject.ARMO_BOOTS -> SLOT_BOOTS;
                case EsmObject.ARMO_LGAUNTLET, EsmObject.ARMO_LBRACER -> SLOT_LEFT_GAUNTLET;
                case EsmObject.ARMO_RGAUNTLET, EsmObject.ARMO_RBRACER -> SLOT_RIGHT_GAUNTLET;
                default -> -1;
            };
        }
        return -1;
    }

    private static boolean isDefaultSkeleton(String path) {
        return path.equals("meshes/base_anim.nif") || path.equals("meshes/base_anim_female.nif")
            || path.equals("meshes/base_animkna.nif");
    }

    private void addAnimSource(NpcActor actor, String nifPath) throws Exception {
        String kfPath = TexturePaths.nifToKf(nifPath);
        if (!TestData.vfsExists(kfPath)) {
            return;
        }
        KfFile kf = kfs.get(kfPath);
        if (kf == null) {
            Path file = TestData.ensureNif(kfPath);
            NifFile nif = NifFile.parse(Files.readAllBytes(file), kfPath);
            kf = KfFile.load(nif, kfPath);
            kfs.put(kfPath, kf);
        }
        if (!kf.textKeys.isEmpty() && !kf.tracks.isEmpty()) {
            actor.sources.add(kf);
        }
    }

    private void playIdle(NpcActor actor) {
        for (int i = actor.sources.size() - 1; i >= 0; i--) {
            KfFile kf = actor.sources.get(i);
            KfFile.IdleLoop loop = kf.playIdle();
            if (loop == null) {
                continue;
            }
            actor.kf = kf;
            actor.idle = loop;
            for (KfFile.BoneTrack track : kf.tracks.values()) {
                SceneNode node = actor.boneNodes.get(track.bone.toLowerCase(Locale.ROOT));
                if (node == null) {
                    Gdx.app.log("NpcMannequin", "idle: missing bone " + track.bone);
                    continue;
                }
                BoneBinding bind = new BoneBinding(node, track.controller, track.data);
                bind.rest.set(node.local);
                actor.bindings.add(bind);
            }
            return;
        }
    }

    private void pose(NpcActor actor) {
        if (actor.idle == null) {
            return;
        }
        float animTime = actor.idle.time;
        for (BoneBinding bind : actor.bindings) {
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
        actor.boneWorld.clear();
        actor.skeleton.updateWorld(id);
        collectWorlds(actor.skeleton, actor.boneWorld);
        for (MeshInstance skin : actor.skins) {
            skin.reskin(actor.boneWorld);
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

    private static void collectSkins(SceneNode node, List<MeshInstance> skins) {
        for (MeshInstance inst : node.meshes) {
            if (inst.skinNif != null) {
                skins.add(inst);
            }
        }
        for (SceneNode child : node.children) {
            collectSkins(child, skins);
        }
    }

    private static void collectWorlds(SceneNode node, Map<String, Matrix4> world) {
        if (!node.name.isEmpty()) {
            world.putIfAbsent(node.name.toLowerCase(Locale.ROOT), new Matrix4(node.world));
        }
        for (SceneNode child : node.children) {
            collectWorlds(child, world);
        }
    }

    private SceneNode skeleton(String vfs) throws Exception {
        return GpuCache.boneTemplate(vfs);
    }

    private PartNif partNif(String vfs) throws Exception {
        PartNif cached = parts.get(vfs);
        if (cached != null) {
            return cached;
        }
        NifSceneBuilder builder = GpuCache.nif(vfs);
        PartNif part = new PartNif(builder, builder.isSkeleton() ? null : GpuCache.meshTemplate(vfs));
        parts.put(vfs, part);
        return part;
    }

    private NifSceneBuilder loadNif(String vfs) throws Exception {
        return GpuCache.nif(vfs);
    }

    private static void collectBones(SceneNode node, Map<String, Matrix4> world, Map<String, SceneNode> nodes) {
        if (!node.name.isEmpty()) {
            String key = node.name.toLowerCase(Locale.ROOT);
            nodes.putIfAbsent(key, node);
            world.putIfAbsent(key, new Matrix4(node.world));
        }
        for (SceneNode child : node.children) {
            collectBones(child, world, nodes);
        }
    }

    private Vector3 findBoneOffset(SceneNode node) {
        if ("BoneOffset".equalsIgnoreCase(node.name)) {
            node.local.getTranslation(tmp);
            return tmp.cpy();
        }
        for (SceneNode child : node.children) {
            Vector3 found = findBoneOffset(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void stripBoneOffset(SceneNode node) {
        node.children.removeIf(child -> {
            if ("BoneOffset".equalsIgnoreCase(child.name) && child.children.isEmpty() && child.meshes.isEmpty()) {
                child.parent = null;
                return true;
            }
            stripBoneOffset(child);
            return false;
        });
    }

    private static void markClockwise(SceneNode node) {
        for (MeshInstance inst : node.meshes) {
            inst.frontClockwise = true;
        }
        for (SceneNode child : node.children) {
            markClockwise(child);
        }
    }

    static SceneNode cloneTree(SceneNode src) {
        SceneNode n = new SceneNode();
        n.name = src.name;
        n.local.set(src.local);
        n.skipMeshes = src.skipMeshes;
        n.actor = src.actor;
        for (MeshInstance inst : src.meshes) {
            MeshInstance copy = new MeshInstance(inst.mesh);
            copy.frontClockwise = inst.frontClockwise;
            n.meshes.add(copy);
        }
        for (SceneNode child : src.children) {
            n.addChild(cloneTree(child));
        }
        return n;
    }

    private record PartNif(NifSceneBuilder builder, SceneNode rigid) {
    }

    private static final class NpcActor {
        final SceneNode placed;
        final SceneNode skeleton;
        final Map<String, SceneNode> boneNodes;
        final List<KfFile> sources = new ArrayList<>();
        final List<BoneBinding> bindings = new ArrayList<>();
        final List<MeshInstance> skins = new ArrayList<>();
        final Map<String, Matrix4> boneWorld = new HashMap<>();
        KfFile kf;
        KfFile.IdleLoop idle;

        NpcActor(SceneNode placed, SceneNode skeleton, Map<String, SceneNode> boneNodes) {
            this.placed = placed;
            this.skeleton = skeleton;
            this.boneNodes = boneNodes;
        }
    }

    private static final class BoneBinding {
        final SceneNode node;
        final Matrix4 rest = new Matrix4();
        final NiKeyframeController controller;
        final NiKeyframeData data;

        BoneBinding(SceneNode node, NiKeyframeController controller, NiKeyframeData data) {
            this.node = node;
            this.controller = controller;
            this.data = data;
        }
    }
}

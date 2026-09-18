/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmBodyPart;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmNpc;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.esm.EsmPartRef;
import io.github.jvmmw.esm.EsmRace;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bind-pose NPC. Rewrite of {@code MWRender::NpcAnimation} without {@code .kf}.
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

    private final Path testdata;
    private final List<NifSceneBuilder> builders = new ArrayList<>();
    private final Map<String, SceneNode> skeletonTemplates = new HashMap<>();
    private final Map<String, PartNif> parts = new HashMap<>();
    private final Matrix4 id = new Matrix4();
    private final Vector3 tmp = new Vector3();

    public NpcMannequin(Path testdata) {
        this.testdata = testdata;
    }

    public void dispose() {
        for (NifSceneBuilder b : builders) {
            b.dispose();
        }
        builders.clear();
        skeletonTemplates.clear();
        parts.clear();
    }

    public SceneNode build(EsmNpc npc, CellRef ref, EsmFile.LoadedCell cell) throws Exception {
        String skelPath = skeletonPath(npc, cell);
        SceneNode actor = cloneTree(skeleton(skelPath));
        actor.updateWorld(id);
        Map<String, Matrix4> boneWorld = new HashMap<>();
        Map<String, SceneNode> boneNodes = new HashMap<>();
        collectBones(actor, boneWorld, boneNodes);
        int[] priority = new int[PRT_COUNT];
        EsmObject[] equipped = autoEquip(npc, cell);
        for (int[] slot : SLOT_LIST) {
            EsmObject item = equipped[slot[0]];
            if (item == null) {
                continue;
            }
            int prio = ((slot[1] + 1) << 1) + ("ARMO".equals(item.rec) ? 1 : 0);
            addPartGroup(npc, cell, item, prio, priority, actor, boneWorld, boneNodes);
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
            attachPart(PRT_HEAD, headMesh, actor, boneWorld, boneNodes);
            priority[PRT_HEAD] = 1;
        }
        if (priority[PRT_HAIR] < 1 && priority[PRT_HEAD] <= 1 && !hairMesh.isEmpty()) {
            attachPart(PRT_HAIR, hairMesh, actor, boneWorld, boneNodes);
            priority[PRT_HAIR] = 1;
        }
        EsmBodyPart[] skins = racialSkin(npc, cell);
        for (int part = PRT_NECK; part < PRT_COUNT; part++) {
            if (priority[part] < 1 && skins[part] != null && !skins[part].model.isEmpty()) {
                attachPart(part, TexturePaths.normalizeMeshPath(skins[part].model), actor, boneWorld, boneNodes);
            }
        }
        EsmRace race = cell.races.get(npc.race.toLowerCase(Locale.ROOT));
        float weight = 1f;
        float height = 1f;
        if (race != null) {
            weight = npc.female() ? race.femaleWeight : race.maleWeight;
            height = npc.female() ? race.femaleHeight : race.maleHeight;
        }
        float s = ref.scale;
        EsmTransforms.setActorLocal(actor.local, ref.pos, ref.rot[2], s * weight, s * weight, s * height);
        actor.name = npc.id;
        return actor;
    }

    public static String skeletonPath(EsmNpc npc, EsmFile.LoadedCell cell) {
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
        if (!npc.model.isEmpty()) {
            String custom = TexturePaths.normalizeMeshPath(npc.model);
            if (!isDefaultSkeleton(custom)) {
                base = custom;
            }
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
            part.builder.copyMatchingSkinned(filter, boneWorld, actor);
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

    private SceneNode skeleton(String vfs) throws Exception {
        SceneNode template = skeletonTemplates.get(vfs);
        if (template != null) {
            return template;
        }
        NifSceneBuilder builder = loadNif(vfs);
        template = builder.buildBones();
        skeletonTemplates.put(vfs, template);
        return template;
    }

    private PartNif partNif(String vfs) throws Exception {
        PartNif cached = parts.get(vfs);
        if (cached != null) {
            return cached;
        }
        NifSceneBuilder builder = loadNif(vfs);
        PartNif part = new PartNif(builder, builder.isSkeleton() ? null : builder.build(false));
        parts.put(vfs, part);
        return part;
    }

    private NifSceneBuilder loadNif(String vfs) throws Exception {
        Path nifPath = TestData.ensureNif(vfs);
        NifFile nif = NifFile.parse(Files.readAllBytes(nifPath), vfs);
        NifSceneBuilder builder = new NifSceneBuilder(nif, testdata, TestData::vfsExists);
        builders.add(builder);
        return builder;
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
}

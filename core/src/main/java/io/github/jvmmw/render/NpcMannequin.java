/*
 * Ported to Java from OpenMW (https://openmw.org)
 * Original work Copyright (C) OpenMW Contributors
 * Java translation Copyright (C) 2026 JVM-MW contributors
 */
package io.github.jvmmw.render;

import io.github.jvmmw.debug.DebugVars;
import io.github.jvmmw.debug.PerfTrace;
import io.github.jvmmw.esm.AiPackage;
import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmBodyPart;
import io.github.jvmmw.esm.EsmCreature;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmNpc;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.esm.EsmPartRef;
import io.github.jvmmw.esm.EsmRace;
import io.github.jvmmw.esm.LandRecord;
import io.github.jvmmw.esm.PathgridGraph;
import io.github.jvmmw.nif.KfFile;
import io.github.jvmmw.nif.NifFile;
import io.github.jvmmw.nif.NiKeyframeController;
import io.github.jvmmw.nif.NiKeyframeData;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * An NPC as a dressed skeleton (head, hair, clothes on base_anim), not the
 * mesh listed on the NPC record. Faces the yaw of the placement, scaled by
 * race. Idle animation moves the bones, then the skin is rebuilt on the CPU.
 * The front package, when it is wander and its distance is greater than 0,
 * walks them around spawn. A front travel walks to that point when it is
 * within 7168, then the package ends; a farther point leaves them standing.
 * Follow, escort, or activate in front leaves them standing. A front wander
 * with a duration above 0 ends after that many Clear hours (0 does not).
 * While they stand, idle2–idle9 can play; walking still uses walkforward.
 * When the cell has a usable pathgrid they follow its edges (the F5 spheres); otherwise they
 * follow the F6 carpet around shacks when Detour has a path, or a straight
 * line if the mesh is still loading. While they move the same .kf plays
 * walkforward, then idle again when they stop. They turn toward the next
 * point instead of snapping yaw. Walk clips shove Bip01 (or root bone)
 * forward — we zero that XY so the loop does not yank them back each
 * stride. Creatures use the same tick with their own mesh. Feet sit on
 * Bullet land, docks, and interior floors. They still walk through shacks.
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
    /** Walk TES units/s when the .kf has no Bip01 travel. */
    private static final float WALK_CLIP_FALLBACK = 154.064f;
    private static final float WALK_ANIM_MAX = 10f;
    private static final float WANDER_ARRIVE = 8f;
    /** Another actor this close already owns the dest. */
    private static final float WANDER_OCCUPY = 96f;
    private static final float ANIM_BLEND = 0.2f;
    private static final float TURN_EPS = (float) Math.toRadians(0.5f);
    /** Walk only when facing dest; 90° plus a slow turn circles forever. */
    private static final float WALK_ALIGN = (float) Math.toRadians(15f);
    /** Chance a pathgrid dest is anywhere on the connected graph, not nearby. */
    private static final float GRID_FAR = 0.28f;
    private static final float STICK_LIFT = 64f;
    /** Vanilla GMST fIdleChanceMultiplier. A roll above this keeps the plain idle. */
    private static final float IDLE_CHANCE_MULT = 0.75f;
    private static final String[] IDLE_GROUPS = {
        "idle2", "idle3", "idle4", "idle5", "idle6", "idle7", "idle8", "idle9"
    };

    private final Path testdata;
    private final List<MeshGpu> ownedGpus = new ArrayList<>();
    private final Map<String, PartNif> parts = new HashMap<>();
    private final Map<String, KfFile> kfs = new HashMap<>();
    private final List<NpcActor> actors = new ArrayList<>();
    private final Ray pickRay = new Ray();
    private final BoundingBox pickBox = new BoundingBox();
    private final Vector3 pickHit = new Vector3();
    private final Vector3 aimPoint = new Vector3();
    private final Map<Long, PathgridGraph> graphs = new HashMap<>();
    private final Random wanderRng = new Random();
    private final float[] hourUnused = new float[1];
    private String navWorld = "";
    private final Matrix4 id = new Matrix4();
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmpS = new Vector3();
    private final Vector3 tmpFrom = new Vector3();
    private final Quaternion tmpQ = new Quaternion();
    private final Quaternion tmpQ2 = new Quaternion();
    private final Quaternion tmpQ3 = new Quaternion();
    private final Quaternion tmpFromQ = new Quaternion();

    public NpcMannequin(Path testdata) {
        this.testdata = testdata;
    }

    public void setNavWorld(String world) {
        navWorld = world == null ? "" : world;
    }

    public void dispose() {
        for (MeshGpu gpu : ownedGpus) {
            gpu.dispose();
        }
        ownedGpus.clear();
        parts.clear();
        kfs.clear();
        actors.clear();
        graphs.clear();
    }

    /** Keep overlapping wanderers on their last TES pose when the 5×5 rebuilds. */
    void copyWanderFrom(NpcMannequin live, EsmFile.LoadedCell cell) {
        if (live == null || live == this) {
            return;
        }
        Map<String, NpcActor> byKey = new HashMap<>();
        for (NpcActor actor : actors) {
            if (!actor.refKey.isEmpty()) {
                byKey.put(actor.refKey, actor);
            }
        }
        for (NpcActor src : live.actors) {
            if (src.refKey.isEmpty()) {
                continue;
            }
            NpcActor dst = byKey.get(src.refKey);
            if (dst == null) {
                continue;
            }
            dst.tesPos[0] = src.tesPos[0];
            dst.tesPos[1] = src.tesPos[1];
            dst.tesPos[2] = src.tesPos[2];
            dst.yaw = src.yaw;
            dst.destX = src.destX;
            dst.destY = src.destY;
            dst.destRange = src.destRange;
            dst.onGrid = src.onGrid;
            dst.walking = src.walking;
            dst.travelWalk = src.travelWalk;
            dst.travelSettled = src.travelSettled;
            dst.idleLeft = src.idleLeft;
            dst.waypoints.clear();
            dst.waypoints.addAll(src.waypoints);
            dst.packages.clear();
            for (AiPackage pack : src.packages) {
                dst.packages.add(pack.copy());
            }
            dst.wanderDistance = src.wanderDistance;
            dst.water = src.water;
            dst.hoursLeft = src.hoursLeft;
            dst.seenSpent = src.seenSpent;
            dst.spentReady = src.spentReady;
            dst.badIdles = src.badIdles;
            dst.idleChosen = src.idleChosen;
            dst.graph = src.graph == PathgridGraph.NONE ? PathgridGraph.NONE : graphFor(cell, dst.tesPos);
            stickLand(dst);
            EsmTransforms.setActorLocal(dst.placed.local, dst.tesPos, dst.yaw, dst.sx, dst.sy, dst.sz);
            if (dst.walking) {
                if (playGroup(dst, "walkforward")) {
                    dst.playingWalk = true;
                    dst.noWalk = false;
                }
            }
        }
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
        pose(actor, 0f);
        actors.add(actor);
        EsmRace race = cell.races.get(npc.race.toLowerCase(Locale.ROOT));
        float weight = 1f;
        float height = 1f;
        if (race != null) {
            weight = npc.female() ? race.femaleWeight : race.maleWeight;
            height = npc.female() ? race.femaleHeight : race.maleHeight;
        }
        float s = ref.scale;
        float sx = s * weight;
        float sy = s * weight;
        float sz = s * height;
        EsmTransforms.setActorLocal(placed.local, ref.pos, ref.rot[2], sx, sy, sz);
        copyPackages(actor, npc.packages);
        beginWander(actor, ref.pos, ref.rot[2], sx, sy, sz, activeDistance(actor), cell, false);
        actor.refKey = ref.takeKey();
        return placed;
    }

    public SceneNode buildCreature(EsmCreature crea, CellRef ref, EsmFile.LoadedCell cell) throws Exception {
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
        pose(actor, 0f);
        actors.add(actor);
        float s = ref.scale * crea.scale;
        EsmTransforms.setActorLocal(placed.local, ref.pos, ref.rot[2], s, s, s);
        copyPackages(actor, crea.packages);
        beginWander(actor, ref.pos, ref.rot[2], s, s, s, activeDistance(actor), cell, crea.pureWater());
        actor.creature = true;
        actor.refKey = ref.takeKey();
        return placed;
    }

    public void update(float dt, float hoursPassed, EsmFile.LoadedCell cell) {
        for (NpcActor actor : actors) {
            spendHours(actor, hoursPassed, cell);
            if (BulletWorld.readyAt(actor.tesPos[0], actor.tesPos[1])) {
                wander(actor, dt, wanderScale(actor), cell);
                stickLand(actor);
                EsmTransforms.setActorLocal(actor.placed.local, actor.tesPos, actor.yaw, actor.sx, actor.sy, actor.sz);
            } else {
                actor.moving = false;
                actor.moved = 0f;
            }
            syncWalkAnim(actor);
            if (actor.specialIdle && (actor.walking || actor.moving)) {
                actor.specialIdle = false;
            } else if (actor.specialIdle && idleFinished(actor)) {
                actor.specialIdle = false;
                playIdle(actor);
                actor.idleChosen = true;
                actor.idleLeft = actor.wanderDistance > 0 ? pause(0.2f, 0.6f) : pause(3f, 6f);
            }
            if (actor.wanderDistance <= 0 && actor.idleChosen && !actor.specialIdle && !actor.walking) {
                actor.idleLeft -= dt;
                if (actor.idleLeft <= 0f) {
                    actor.idleChosen = false;
                }
            }
            rollStandingIdle(actor);
            if (actor.idle == null) {
                continue;
            }
            float animDt = walkAnimDt(actor, dt);
            actor.idle.time += animDt;
            if (!actor.specialIdle) {
                float span = actor.idle.loopStop - actor.idle.loopStart;
                if (span > 0f && actor.idle.time >= actor.idle.loopStop) {
                    actor.idle.time = actor.idle.loopStart
                        + ((actor.idle.time - actor.idle.loopStart) % span);
                }
            }
            pose(actor, dt);
        }
    }

    private void beginWander(NpcActor actor, float[] spawn, float yaw, float sx, float sy, float sz, int distance,
        EsmFile.LoadedCell cell, boolean water) {
        actor.tesPos[0] = spawn[0];
        actor.tesPos[1] = spawn[1];
        actor.tesPos[2] = spawn[2];
        actor.spawnX = spawn[0];
        actor.spawnY = spawn[1];
        actor.spawnZ = spawn[2];
        actor.yaw = yaw;
        actor.sx = sx;
        actor.sy = sy;
        actor.sz = sz;
        actor.water = water;
        actor.wanderDistance = distance;
        actor.walking = false;
        actor.travelWalk = false;
        actor.travelSettled = false;
        actor.waypoints.clear();
        boolean travel = frontTravel(actor);
        actor.graph = (distance > 0 || travel) && !water ? graphFor(cell, spawn) : PathgridGraph.NONE;
        actor.idleLeft = distance > 0 ? pause(0.5f, 1.5f) : 0f;
        armPackageTimer(actor);
    }

    /**
     * Drop the front package and walk whatever is now in front. A wander's
     * hours and a travel's arrival both call this.
     */
    private void completeActive(NpcActor actor, EsmFile.LoadedCell cell) {
        AiPackage.finishFront(actor.packages);
        int distance = activeDistance(actor);
        actor.wanderDistance = distance;
        actor.walking = false;
        actor.travelWalk = false;
        actor.travelSettled = false;
        actor.waypoints.clear();
        float[] spawn = new float[] { actor.spawnX, actor.spawnY, actor.spawnZ };
        boolean travel = frontTravel(actor);
        actor.graph = (distance > 0 || travel) && !actor.water ? graphFor(cell, spawn) : PathgridGraph.NONE;
        actor.idleLeft = distance > 0 ? pause(0.5f, 1.5f) : 0f;
        armPackageTimer(actor);
    }

    private void armPackageTimer(NpcActor actor) {
        actor.badIdles = 0;
        actor.specialIdle = false;
        actor.idleChosen = false;
        actor.hoursLeft = 0f;
        if (!actor.packages.isEmpty() && actor.packages.get(0).kind == AiPackage.Kind.WANDER) {
            actor.hoursLeft = actor.packages.get(0).duration;
        }
    }

    private void spendHours(NpcActor actor, float hoursPassed, EsmFile.LoadedCell cell) {
        if (!actor.spentReady) {
            actor.seenSpent = hoursPassed;
            actor.spentReady = true;
            return;
        }
        float delta = hoursPassed - actor.seenSpent;
        actor.seenSpent = hoursPassed;
        if (delta < 0f) {
            return;
        }
        float[] unused = hourUnused;
        int guard = 0;
        boolean ended = false;
        while (delta > 0f && guard++ < 48) {
            if (actor.packages.isEmpty() || actor.packages.get(0).kind != AiPackage.Kind.WANDER) {
                break;
            }
            int duration = actor.packages.get(0).duration;
            float left = AiPackage.spendWanderHours(duration, actor.hoursLeft, delta, unused);
            if (left >= 0f) {
                actor.hoursLeft = left;
                break;
            }
            String id = actor.placed.name == null ? "" : actor.placed.name;
            completeActive(actor, cell);
            ended = true;
            Gdx.app.log("JVM-MW", "package done id=" + id + " active=" + AiPackage.activeTag(actor.packages));
            delta = unused[0];
        }
        if (ended && !actor.walking) {
            playIdle(actor);
        }
    }

    /** The gesture reached its end, or idleDuration seconds when that knob is above 0. */
    private static boolean idleFinished(NpcActor actor) {
        if (actor.idle == null || actor.idle.time >= actor.idle.stopTime) {
            return true;
        }
        float cap = DebugVars.idleDuration;
        return cap > 0f && actor.idle.time >= actor.idle.startTime + cap;
    }

    /** While a front wander stands, roll idle2–idle9 once. He stays until that clip ends. */
    private void rollStandingIdle(NpcActor actor) {
        if (actor.walking || actor.moving || actor.specialIdle || actor.idleChosen) {
            return;
        }
        if (actor.packages.isEmpty() || actor.packages.get(0).kind != AiPackage.Kind.WANDER) {
            return;
        }
        int[] chances = actor.packages.get(0).idle;
        actor.idleChosen = true;
        for (int attempt = 0; attempt < 8; attempt++) {
            int idx = rollIdleIndex(chances);
            if (idx < 0) {
                actor.idleLeft = actor.wanderDistance <= 0 ? pause(1.2f, 2.5f) : pause(0.4f, 1.2f);
                return;
            }
            int bit = 1 << idx;
            if ((actor.badIdles & bit) != 0) {
                continue;
            }
            if (playGroup(actor, IDLE_GROUPS[idx], false)) {
                actor.specialIdle = true;
                actor.idleLeft = 0f;
                return;
            }
            actor.badIdles |= bit;
        }
        actor.idleLeft = pause(0.4f, 1.2f);
    }

    /** OpenMW getRandomIdle. -1 is the plain idle. fIdleChanceMultiplier is 0.75. */
    private int rollIdleIndex(int[] chances) {
        if (wanderRng.nextFloat() > IDLE_CHANCE_MULT) {
            return -1;
        }
        int best = -1;
        float maxRoll = 0f;
        for (int i = 0; i < chances.length && i < IDLE_GROUPS.length; i++) {
            float roll = wanderRng.nextFloat() * 100f;
            if (roll <= chances[i] && roll > maxRoll) {
                best = i;
                maxRoll = roll;
            }
        }
        return best;
    }

    private static void copyPackages(NpcActor actor, List<AiPackage> source) {
        actor.packages.clear();
        if (source == null) {
            return;
        }
        for (AiPackage pack : source) {
            actor.packages.add(pack.copy());
        }
    }

    /** Front wander distance, or 0 when the front row is anything else. */
    private static int activeDistance(NpcActor actor) {
        if (actor.packages.isEmpty()) {
            return 0;
        }
        AiPackage front = actor.packages.get(0);
        return front.kind == AiPackage.Kind.WANDER ? front.distance : 0;
    }

    private PathgridGraph graphFor(EsmFile.LoadedCell cell, float[] spawn) {
        if (cell == null) {
            return PathgridGraph.NONE;
        }
        long key;
        if (cell.interior) {
            key = 1L;
        } else {
            int gx = LandRecord.cellGrid(spawn[0]);
            int gy = LandRecord.cellGrid(spawn[1]);
            key = ((long) gx << 32) ^ (gy & 0xffffffffL);
        }
        return graphs.computeIfAbsent(key, k -> PathgridGraph.of(cell, spawn));
    }

    private void wander(NpcActor actor, float dt, float scale, EsmFile.LoadedCell cell) {
        if (frontTravel(actor)) {
            travel(actor, dt, scale, cell);
            return;
        }
        if (actor.wanderDistance <= 0) {
            return;
        }
        actor.moving = false;
        actor.moved = 0f;
        float nodeCap = nodeRange(actor.wanderDistance);
        float roamCap = wanderRange(actor.wanderDistance);
        if (nodeCap <= WANDER_ARRIVE && roamCap <= WANDER_ARRIVE) {
            actor.walking = false;
            actor.waypoints.clear();
            return;
        }
        if (actor.walking) {
            stepPath(actor, dt, scale);
        } else if (!actor.specialIdle && actor.idleChosen) {
            actor.idleLeft -= dt;
            if (actor.idleLeft <= 0f) {
                pickWanderDest(actor);
            }
        }
    }

    /** Front travel: walk to the point, then drop the package. A far point stays put. */
    private void travel(NpcActor actor, float dt, float scale, EsmFile.LoadedCell cell) {
        actor.moving = false;
        actor.moved = 0f;
        AiPackage dest = actor.packages.get(0);
        if (!AiPackage.travelInRange(dest.x, dest.y, dest.z,
            actor.tesPos[0], actor.tesPos[1], actor.tesPos[2])) {
            actor.walking = false;
            actor.travelWalk = false;
            actor.waypoints.clear();
            return;
        }
        if (actor.travelSettled) {
            if (travelAlreadyThere(actor)) {
                return;
            }
            actor.travelSettled = false;
        }
        if (!actor.walking) {
            if (travelAlreadyThere(actor)) {
                finishTravel(actor, cell);
                return;
            }
            armTravel(actor);
        }
        if (actor.walking) {
            stepPath(actor, dt, scale);
            if (!actor.walking) {
                finishTravel(actor, cell);
            }
        }
    }

    /** Walk the current polyline. Wander and travel both use this. */
    private void stepPath(NpcActor actor, float dt, float scale) {
        float range = actor.destRange;
        float range2 = range * range;
        float remaining = walkClip(actor) * scale * dt;
        boolean turned = false;
        int hops = 0;
        while (actor.walking && remaining > 0.001f && hops++ < 16) {
            if (!actor.onGrid && !actor.travelWalk) {
                float ddx = actor.destX - actor.spawnX;
                float ddy = actor.destY - actor.spawnY;
                if (ddx * ddx + ddy * ddy > range2) {
                    actor.waypoints.clear();
                    pickWanderDest(actor);
                    if (!actor.walking) {
                        actor.idleChosen = false;
                        actor.idleLeft = pause(2f, 3f);
                        break;
                    }
                    range = actor.destRange;
                    range2 = range * range;
                    continue;
                }
            }
            float dx = actor.destX - actor.tesPos[0];
            float dy = actor.destY - actor.tesPos[1];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= WANDER_ARRIVE) {
                actor.tesPos[0] = actor.destX;
                actor.tesPos[1] = actor.destY;
                if (!actor.waypoints.isEmpty()) {
                    float[] next = actor.waypoints.remove(0);
                    if (!actor.onGrid && !actor.travelWalk) {
                        float ndx = next[0] - actor.spawnX;
                        float ndy = next[1] - actor.spawnY;
                        if (ndx * ndx + ndy * ndy > range2) {
                            actor.waypoints.clear();
                            pickWanderDest(actor);
                            if (!actor.walking) {
                                actor.idleChosen = false;
                                actor.idleLeft = pause(2f, 3f);
                                break;
                            }
                            range = actor.destRange;
                            range2 = range * range;
                            continue;
                        }
                    }
                    actor.destX = next[0];
                    actor.destY = next[1];
                    continue;
                }
                beginStand(actor);
                break;
            }
            float want = (float) Math.atan2(dx, dy);
            float diff;
            if (!turned) {
                diff = turnToward(actor, want, dt);
                turned = true;
            } else {
                diff = wrapPi(want - actor.yaw);
            }
            if (Math.abs(diff) > WALK_ALIGN) {
                break;
            }
            float step = Math.min(remaining, dist);
            float nx = actor.tesPos[0] + (float) Math.sin(actor.yaw) * step;
            float ny = actor.tesPos[1] + (float) Math.cos(actor.yaw) * step;
            float ndx = actor.destX - nx;
            float ndy = actor.destY - ny;
            if (ndx * ndx + ndy * ndy >= dist * dist) {
                break;
            }
            actor.tesPos[0] = nx;
            actor.tesPos[1] = ny;
            remaining -= step;
            actor.moved += step;
            actor.moving = true;
        }
    }

    private void finishTravel(NpcActor actor, EsmFile.LoadedCell cell) {
        String id = actor.placed.name == null ? "" : actor.placed.name;
        completeActive(actor, cell);
        Gdx.app.log("JVM-MW", "package done id=" + id + " active=" + AiPackage.activeTag(actor.packages));
        if (!actor.walking) {
            playIdle(actor);
        }
        actor.travelSettled = frontTravel(actor) && travelAlreadyThere(actor);
    }

    /** Pathgrid to the travel point, else the carpet, else a straight line. */
    private void armTravel(NpcActor actor) {
        AiPackage dest = actor.packages.get(0);
        actor.travelWalk = true;
        if (!actor.water && actor.graph.usable()) {
            List<float[]> path = actor.graph.pathTo(
                actor.tesPos[0], actor.tesPos[1], actor.tesPos[2],
                dest.x, dest.y, dest.z,
                actor.tesPos[0], actor.tesPos[1], actor.tesPos[2],
                Float.POSITIVE_INFINITY);
            if (!path.isEmpty()) {
                actor.destX = path.get(0)[0];
                actor.destY = path.get(0)[1];
                actor.waypoints.clear();
                for (int i = 1; i < path.size(); i++) {
                    actor.waypoints.add(path.get(i));
                }
                actor.walking = true;
                actor.onGrid = true;
                actor.destRange = AiPackage.TRAVEL_MAX;
                return;
            }
        }
        actor.destX = dest.x;
        actor.destY = dest.y;
        actor.destRange = AiPackage.TRAVEL_MAX;
        actor.onGrid = false;
        actor.walking = true;
        actor.waypoints.clear();
        followDetour(actor, dest.x, dest.y, dest.z);
        float endX = actor.destX;
        float endY = actor.destY;
        if (!actor.waypoints.isEmpty()) {
            float[] last = actor.waypoints.get(actor.waypoints.size() - 1);
            endX = last[0];
            endY = last[1];
        }
        float edx = endX - dest.x;
        float edy = endY - dest.y;
        if (edx * edx + edy * edy > 1f) {
            actor.waypoints.add(new float[] { dest.x, dest.y, dest.z });
        }
    }

    private static boolean frontTravel(NpcActor actor) {
        return !actor.packages.isEmpty() && actor.packages.get(0).kind == AiPackage.Kind.TRAVEL;
    }

    /** Close enough on the ground that the walk counts as arrived. */
    private static boolean travelAlreadyThere(NpcActor actor) {
        if (!frontTravel(actor)) {
            return false;
        }
        AiPackage dest = actor.packages.get(0);
        float dx = dest.x - actor.tesPos[0];
        float dy = dest.y - actor.tesPos[1];
        return dx * dx + dy * dy <= WANDER_ARRIVE * WANDER_ARRIVE;
    }

    /** Path finished. Stay put so a standing idle can play before the next walk. */
    private void beginStand(NpcActor actor) {
        actor.walking = false;
        actor.moving = false;
        actor.waypoints.clear();
        actor.idleChosen = false;
        actor.idleLeft = 0f;
    }

    private void pickWanderDest(NpcActor actor) {
        if (pickPathgridDest(actor)) {
            return;
        }
        pickStraightDest(actor);
    }

    private boolean pickPathgridDest(NpcActor actor) {
        if (!actor.graph.usable()) {
            return false;
        }
        if (wanderRng.nextFloat() < GRID_FAR && tryFarGridDest(actor)) {
            return true;
        }
        float range = nodeRange(actor.wanderDistance);
        if (range <= WANDER_ARRIVE) {
            return false;
        }
        List<float[]> left = actor.graph.allowed(
            actor.tesPos[0], actor.tesPos[1], actor.tesPos[2], range);
        if (left.size() <= 2) {
            return false;
        }
        float arrive2 = WANDER_ARRIVE * WANDER_ARRIVE;
        float far2 = range * 0.4f;
        far2 *= far2;
        List<float[]> far = new ArrayList<>();
        List<float[]> near = new ArrayList<>();
        for (float[] dest : left) {
            float cdx = dest[0] - actor.tesPos[0];
            float cdy = dest[1] - actor.tesPos[1];
            float c2 = cdx * cdx + cdy * cdy;
            if (c2 <= arrive2 || occupied(actor, dest[0], dest[1])) {
                continue;
            }
            if (c2 >= far2) {
                far.add(dest);
            } else {
                near.add(dest);
            }
        }
        return tryPathgridPool(actor, far, range) || tryPathgridPool(actor, near, range);
    }

    private boolean tryFarGridDest(NpcActor actor) {
        List<float[]> all = actor.graph.componentOf(actor.tesPos[0], actor.tesPos[1], actor.tesPos[2]);
        if (all.size() <= 2) {
            return false;
        }
        float arrive2 = WANDER_ARRIVE * WANDER_ARRIVE;
        float max2 = 0f;
        for (float[] dest : all) {
            float dx = dest[0] - actor.tesPos[0];
            float dy = dest[1] - actor.tesPos[1];
            float c2 = dx * dx + dy * dy;
            if (c2 > max2) {
                max2 = c2;
            }
        }
        float far2 = max2 * 0.55f * 0.55f;
        List<float[]> far = new ArrayList<>();
        for (float[] dest : all) {
            float dx = dest[0] - actor.tesPos[0];
            float dy = dest[1] - actor.tesPos[1];
            float c2 = dx * dx + dy * dy;
            if (c2 <= arrive2 || occupied(actor, dest[0], dest[1])) {
                continue;
            }
            if (c2 >= far2) {
                far.add(dest);
            }
        }
        return tryPathgridPool(actor, far, Float.POSITIVE_INFINITY);
    }

    private boolean tryPathgridPool(NpcActor actor, List<float[]> pool, float range) {
        while (!pool.isEmpty()) {
            int pick = wanderRng.nextInt(pool.size());
            float[] dest = pool.remove(pick);
            List<float[]> path = actor.graph.pathTo(
                actor.tesPos[0], actor.tesPos[1], actor.tesPos[2], dest[0], dest[1], dest[2],
                actor.tesPos[0], actor.tesPos[1], actor.tesPos[2], range);
            if (path.isEmpty() || !pathFits(actor, path, range)) {
                continue;
            }
            if (occupied(actor, path.get(0)[0], path.get(0)[1])) {
                continue;
            }
            actor.destX = path.get(0)[0];
            actor.destY = path.get(0)[1];
            actor.waypoints.clear();
            for (int i = 1; i < path.size(); i++) {
                actor.waypoints.add(path.get(i));
            }
            actor.walking = true;
            actor.onGrid = true;
            actor.destRange = range;
            return true;
        }
        return false;
    }

    private static boolean pathFits(NpcActor actor, List<float[]> path, float range) {
        float range2 = range * range;
        for (float[] p : path) {
            float sdx = p[0] - actor.tesPos[0];
            float sdy = p[1] - actor.tesPos[1];
            if (sdx * sdx + sdy * sdy > range2) {
                return false;
            }
        }
        return true;
    }

    private boolean occupied(NpcActor self, float x, float y) {
        float r2 = WANDER_OCCUPY * WANDER_OCCUPY;
        for (NpcActor other : actors) {
            if (other == self || other.wanderDistance <= 0) {
                continue;
            }
            float dx = other.tesPos[0] - x;
            float dy = other.tesPos[1] - y;
            if (dx * dx + dy * dy <= r2) {
                return true;
            }
            if (other.walking) {
                float ex = other.destX - x;
                float ey = other.destY - y;
                if (ex * ex + ey * ey <= r2) {
                    return true;
                }
            }
        }
        return false;
    }

    private void pickStraightDest(NpcActor actor) {
        float range = wanderRange(actor.wanderDistance);
        if (range <= WANDER_ARRIVE) {
            actor.walking = false;
            return;
        }
        float arrive2 = WANDER_ARRIVE * WANDER_ARRIVE;
        actor.waypoints.clear();
        for (int n = 0; n < 8; n++) {
            float radius = (0.2f + wanderRng.nextFloat() * 0.8f) * range;
            float theta = wanderRng.nextFloat() * ((float) Math.PI * 2f);
            float destX = actor.spawnX + radius * (float) Math.cos(theta);
            float destY = actor.spawnY + radius * (float) Math.sin(theta);
            float dx = destX - actor.tesPos[0];
            float dy = destY - actor.tesPos[1];
            if (dx * dx + dy * dy <= arrive2 || occupied(actor, destX, destY)) {
                continue;
            }
            actor.destX = destX;
            actor.destY = destY;
            actor.destRange = range;
            actor.onGrid = false;
            actor.walking = true;
            followDetour(actor, destX, destY, actor.tesPos[2]);
            return;
        }
        actor.walking = false;
    }

    private void followDetour(NpcActor actor, float destX, float destY, float destZ) {
        List<float[]> path = NavmeshQuery.wanderPath(
            navWorld, actor.tesPos[0], actor.tesPos[1], actor.tesPos[2], destX, destY, destZ);
        if (path.size() < 2) {
            return;
        }
        float arrive2 = WANDER_ARRIVE * WANDER_ARRIVE;
        int i = 0;
        while (i < path.size()) {
            float dx = path.get(i)[0] - actor.tesPos[0];
            float dy = path.get(i)[1] - actor.tesPos[1];
            if (dx * dx + dy * dy > arrive2) {
                break;
            }
            i++;
        }
        if (i >= path.size()) {
            return;
        }
        actor.destX = path.get(i)[0];
        actor.destY = path.get(i)[1];
        actor.waypoints.clear();
        for (int n = i + 1; n < path.size(); n++) {
            actor.waypoints.add(path.get(n));
        }
    }

    /** Remaining signed error after this frame's turn. */
    private static float turnToward(NpcActor actor, float want, float dt) {
        float diff = wrapPi(want - actor.yaw);
        float abs = Math.abs(diff);
        if (abs <= TURN_EPS) {
            actor.yaw = want;
            return 0f;
        }
        float limit = (float) Math.toRadians(DebugVars.wanderTurn) * dt;
        if (abs > limit) {
            actor.yaw += Math.signum(diff) * limit;
            actor.yaw = wrapPi(actor.yaw);
            return wrapPi(want - actor.yaw);
        }
        actor.yaw = want;
        return 0f;
    }

    private static float wrapPi(float a) {
        while (a > (float) Math.PI) {
            a -= (float) (Math.PI * 2);
        }
        while (a < (float) -Math.PI) {
            a += (float) (Math.PI * 2);
        }
        return a;
    }

    private static void stickLand(NpcActor actor) {
        if (!BulletWorld.readyAt(actor.tesPos[0], actor.tesPos[1])) {
            return;
        }
        float y = BulletWorld.hitY(actor.tesPos[0], actor.tesPos[2] + STICK_LIFT, -actor.tesPos[1]);
        if (Float.isNaN(y)) {
            return;
        }
        actor.tesPos[2] = y;
    }

    private void syncWalkAnim(NpcActor actor) {
        if (actor.moving && !actor.playingWalk) {
            if (actor.noWalk) {
                return;
            }
            if (playGroup(actor, "walkforward")) {
                actor.playingWalk = true;
            } else {
                actor.noWalk = true;
            }
        } else if (!actor.walking && actor.playingWalk) {
            playGroup(actor, "idle");
            actor.playingWalk = false;
        }
    }

    private static float wanderScale(NpcActor actor) {
        float s = actor.creature ? DebugVars.creaWanderSpeed : DebugVars.wanderSpeed;
        return s > 0.01f ? s : 0.01f;
    }

    private static float wanderRange(int esmDistance) {
        return capRange(esmDistance, DebugVars.wanderRadius);
    }

    private static float nodeRange(int esmDistance) {
        return capRange(esmDistance, DebugVars.nodeWanderRadius);
    }

    private static float capRange(int esmDistance, float cap) {
        float range = esmDistance;
        if (cap > 0f) {
            range = Math.min(range, cap);
        }
        return Math.max(0f, range);
    }

    private float pause(float min, float extra) {
        float freq = DebugVars.wanderFrequency;
        if (freq < 0.01f) {
            freq = 0.01f;
        }
        return (min + wanderRng.nextFloat() * extra) / freq;
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

    /**
     * Closest NPC or creature within the door use range. Closer than a door,
     * chest, or item wins; a tie leaves those alone. The text is the package list.
     */
    public ActorPick nearestPackages(Vector3 origin, Vector3 direction, EsmFile.LoadedCell cell) {
        if (cell == null) {
            return null;
        }
        NpcActor actor = closestHit(origin, direction, DoorSwing.MAX_ACTIVATE);
        if (actor == null) {
            return null;
        }
        String id = actor.placed.name == null ? "" : actor.placed.name;
        String name = displayName(actor, cell);
        return new ActorPick(origin.dst(aimPoint), AiPackage.format(id, name, actor.packages));
    }

    /**
     * Display name when the look ray hits an NPC or creature and no wall is
     * in front of them. Empty when the crosshair is on the ground or a building.
     */
    public String lookName(Vector3 origin, Vector3 direction, EsmFile.LoadedCell cell) {
        if (cell == null) {
            return "";
        }
        NpcActor actor = closestHit(origin, direction, Float.POSITIVE_INFINITY);
        if (actor == null) {
            return "";
        }
        if (BulletWorld.blockedSegment(origin.x, origin.y, origin.z, aimPoint.x, aimPoint.y, aimPoint.z)) {
            return "";
        }
        String name = displayName(actor, cell);
        if (name.isEmpty()) {
            name = actor.placed.name == null ? "" : actor.placed.name;
        }
        return name;
    }

    private NpcActor closestHit(Vector3 origin, Vector3 direction, float maxDist) {
        pickRay.set(origin, direction);
        NpcActor best = null;
        float bestDist = maxDist;
        for (NpcActor actor : actors) {
            pickBox.inf();
            actor.placed.collectAabb(pickBox);
            if (!pickBox.isValid() || !Intersector.intersectRayBounds(pickRay, pickBox, pickHit)) {
                continue;
            }
            float dist = origin.dst(pickHit);
            if (dist > bestDist) {
                continue;
            }
            best = actor;
            bestDist = dist;
            aimPoint.set(pickHit);
        }
        return best;
    }

    private static String displayName(NpcActor actor, EsmFile.LoadedCell cell) {
        String id = actor.placed.name == null ? "" : actor.placed.name;
        String key = id.toLowerCase(Locale.ROOT);
        if (actor.creature) {
            EsmCreature crea = cell.creatures.get(key);
            return crea == null || crea.name == null ? "" : crea.name;
        }
        EsmNpc npc = cell.npcs.get(key);
        return npc == null || npc.name == null ? "" : npc.name;
    }

    public static final class ActorPick {
        public final float dist;
        public final String text;

        ActorPick(float dist, String text) {
            this.dist = dist;
            this.text = text;
        }
    }

    public static String describe(EsmNpc npc, EsmFile.LoadedCell cell) {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(npc.id)
            .append(" name=").append(npc.name)
            .append(" race=").append(npc.race)
            .append(" female=").append(npc.female())
            .append(" head=").append(npc.head)
            .append(" hair=").append(npc.hair)
            .append(" wander=").append(npc.wanderDistance)
            .append(" allowed=").append(countAllowed(cell, npc.id, npc.wanderDistance, false))
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

    private static int countAllowed(EsmFile.LoadedCell cell, String id, int distance, boolean water) {
        if (cell == null || distance <= 0 || water) {
            return 0;
        }
        for (CellRef ref : cell.refs) {
            if (ref.deleted || !id.equalsIgnoreCase(ref.refId)) {
                continue;
            }
            PathgridGraph graph = PathgridGraph.of(cell, ref.pos);
            return graph.allowed(ref.pos[0], ref.pos[1], ref.pos[2], nodeRange(distance)).size();
        }
        return 0;
    }

    public static String describeCreature(EsmCreature crea, EsmFile.LoadedCell cell) {
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
            .append(" wander=").append(crea.wanderDistance)
            .append(" allowed=").append(countAllowed(cell, crea.id, crea.wanderDistance, crea.pureWater()))
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
        playGroup(actor, "idle");
        actor.playingWalk = false;
    }

    private boolean playGroup(NpcActor actor, String group) {
        return playGroup(actor, group, true);
    }

    private boolean playGroup(NpcActor actor, String group, boolean looping) {
        KfFile chosen = null;
        KfFile.IdleLoop loop = null;
        if (actor.kf != null) {
            loop = actor.kf.play(group, "start", "stop", looping);
            if (loop != null) {
                chosen = actor.kf;
            }
        }
        if (chosen == null) {
            for (int i = actor.sources.size() - 1; i >= 0; i--) {
                KfFile kf = actor.sources.get(i);
                if (kf == actor.kf) {
                    continue;
                }
                loop = kf.play(group, "start", "stop", looping);
                if (loop != null) {
                    chosen = kf;
                    break;
                }
            }
        }
        if (chosen == null) {
            return false;
        }
        boolean hadPose = !actor.bindings.isEmpty();
        if (chosen != actor.kf || actor.bindings.isEmpty()) {
            actor.bindings.clear();
            actor.accumRoot = null;
            for (KfFile.BoneTrack track : chosen.tracks.values()) {
                SceneNode node = actor.boneNodes.get(track.bone.toLowerCase(Locale.ROOT));
                if (node == null) {
                    Gdx.app.log("NpcMannequin", group + ": missing bone " + track.bone);
                    continue;
                }
                BoneBinding bind = new BoneBinding(node, track.controller, track.data);
                bind.rest.set(node.local);
                actor.bindings.add(bind);
            }
            actor.accumRoot = actor.boneNodes.get("bip01");
            if (actor.accumRoot == null) {
                actor.accumRoot = actor.boneNodes.get("root bone");
            }
        }
        if (hadPose) {
            for (BoneBinding bind : actor.bindings) {
                bind.blendFrom.set(bind.node.local);
            }
            actor.blendLeft = ANIM_BLEND;
        } else {
            actor.blendLeft = 0f;
        }
        actor.kf = chosen;
        actor.idle = loop;
        actor.walkClipSpeed = "walkforward".equals(group) ? measureWalkClip(actor) : 0f;
        return true;
    }

    private float measureWalkClip(NpcActor actor) {
        if (actor.idle == null) {
            return 0f;
        }
        float span = actor.idle.loopStop - actor.idle.loopStart;
        if (span <= 0.001f) {
            return 0f;
        }
        for (BoneBinding bind : actor.bindings) {
            if (bind.node != actor.accumRoot || bind.data.translations.empty()) {
                continue;
            }
            bind.data.translations.interpVec3(actor.idle.loopStart, tmp);
            float x0 = tmp.x;
            float y0 = tmp.y;
            bind.data.translations.interpVec3(actor.idle.loopStop, tmp);
            float dx = tmp.x - x0;
            float dy = tmp.y - y0;
            float vel = (float) Math.sqrt(dx * dx + dy * dy) / span;
            return vel > 1f ? vel : 0f;
        }
        return 0f;
    }

    private static float walkClip(NpcActor actor) {
        return actor.walkClipSpeed > 1f ? actor.walkClipSpeed : WALK_CLIP_FALLBACK;
    }

    private static float walkAnimDt(NpcActor actor, float dt) {
        if (!actor.moving || dt < 1e-6f) {
            return dt;
        }
        float rate = actor.moved / (walkClip(actor) * dt);
        if (rate > WALK_ANIM_MAX) {
            rate = WALK_ANIM_MAX;
        }
        return dt * Math.max(0f, rate);
    }

    private void pose(NpcActor actor, float dt) {
        if (actor.idle == null) {
            return;
        }
        float animTime = actor.idle.time;
        float blend = 0f;
        if (actor.blendLeft > 0f) {
            actor.blendLeft = Math.max(0f, actor.blendLeft - dt);
            blend = 1f - actor.blendLeft / ANIM_BLEND;
        }
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
            if (bind.node == actor.accumRoot) {
                tmp.x = 0f;
                tmp.y = 0f;
            }
            if (blend > 0f && blend < 1f) {
                bind.blendFrom.getTranslation(tmpFrom);
                bind.blendFrom.getRotation(tmpFromQ, true);
                tmpFrom.lerp(tmp, blend);
                tmpFromQ.slerp(tmpQ, blend);
                tmp.set(tmpFrom);
                tmpQ.set(tmpFromQ);
            }
            bind.node.local.set(tmp, tmpQ, tmpS);
        }
        actor.boneWorld.clear();
        actor.skeleton.updateWorld(id);
        collectWorlds(actor.skeleton, actor.boneWorld);
        PerfTrace.begin("update.skin");
        for (MeshInstance skin : actor.skins) {
            skin.reskin(actor.boneWorld);
        }
        PerfTrace.end();
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
        SceneNode accumRoot;
        final float[] tesPos = new float[3];
        float spawnX;
        float spawnY;
        float spawnZ;
        float destX;
        float destY;
        float destRange;
        boolean onGrid;
        float yaw;
        float sx = 1f;
        float sy = 1f;
        float sz = 1f;
        int wanderDistance;
        boolean water;
        boolean creature;
        /** Hours left on the front wander. Duration 0 stays 0 and does not end. */
        float hoursLeft;
        float seenSpent;
        boolean spentReady;
        /** idle2–idle9 groups this kf does not have. */
        int badIdles;
        boolean specialIdle;
        /** This stand already rolled. A gesture keeps him still until the clip ends. */
        boolean idleChosen;
        /** This placement's package stack. A copy of the record, front row active. */
        final List<AiPackage> packages = new ArrayList<>();
        String refKey = "";
        float idleLeft;
        boolean walking;
        /** This path is a travel, so it is not clipped to the wander radius. */
        boolean travelWalk;
        /** Already standing on a travel that just repeated. Do not finish every frame. */
        boolean travelSettled;
        PathgridGraph graph = PathgridGraph.NONE;
        final List<float[]> waypoints = new ArrayList<>();
        boolean playingWalk;
        boolean moving;
        boolean noWalk;
        float moved;
        float walkClipSpeed;
        float blendLeft;

        NpcActor(SceneNode placed, SceneNode skeleton, Map<String, SceneNode> boneNodes) {
            this.placed = placed;
            this.skeleton = skeleton;
            this.boneNodes = boneNodes;
        }
    }

    private static final class BoneBinding {
        final SceneNode node;
        final Matrix4 rest = new Matrix4();
        final Matrix4 blendFrom = new Matrix4();
        final NiKeyframeController controller;
        final NiKeyframeData data;

        BoneBinding(SceneNode node, NiKeyframeController controller, NiKeyframeData data) {
            this.node = node;
            this.controller = controller;
            this.data = data;
        }
    }
}

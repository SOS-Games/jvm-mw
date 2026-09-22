package io.github.jvmmw.render;

import io.github.jvmmw.debug.PerfTrace;
import io.github.jvmmw.esm.CellRef;
import io.github.jvmmw.esm.EsmCreature;
import io.github.jvmmw.esm.EsmFile;
import io.github.jvmmw.esm.EsmLevc;
import io.github.jvmmw.esm.EsmNpc;
import io.github.jvmmw.esm.EsmObject;
import io.github.jvmmw.esm.LandRecord;
import io.github.jvmmw.esm.LevelledCreatures;
import io.github.jvmmw.resource.TestData;
import io.github.jvmmw.resource.TexturePaths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Puts one cell on screen: land, water, furniture, doors, chests, pickups,
 * NPCs, and creatures. Wilderness spawn markers are leveled lists — we
 * roll a creature at chargen level and stand it there. Actors with a wander
 * radius shuffle around that spawn along the cell’s pathgrid when it has
 * one. Walking outdoors builds the next grid in the background, then swaps
 * it in. Wanderers in cells that stay loaded keep where they stood; only
 * the new ring starts at spawn. F5 shows the cell’s pathgrid as spheres and lines. F6 shows the
 * Recast walkable carpet for the loaded cells (OpenMW navmesh.db when present).
 * F7 shows orange Bullet dock/kit colliders.
 * Sqlite and Recast run on a worker. Already-fetched tiles stay when the
 * walk grid moves; only the new ring is loaded. After sqlite is in, a
 * second thread rebakes Recast tiles that fail coverage at TES cell edges
 * (the thinner/fatter cracks). Those patches stay too. Random wander dests
 * follow that carpet when Detour has a path; pathgrid NPCs stay on F5.
 *
 * Refs with no mesh are skipped. Invisible markers (prison, divine, temple,
 * north) stay out.
 */
public final class CellSceneBuilder {
    public final StringBuilder log = new StringBuilder();
    public int placed;
    public int skippedUnknown;
    public int skippedEmpty;
    public int skippedActor;
    public int placedNpc;
    public int placedCrea;
    public int placedLevc;
    public int skippedLevcNone;
    public int skippedDeleted;
    public int skippedNif;
    public int placedStat;
    public int navPolys;
    public int navTiles;
    public String navSource = "none";
    public int navPath;
    public int navPatch;
    public String cellName = "";
    public final CellLighting lighting = new CellLighting();
    public final DoorSwing doors = new DoorSwing();
    public final ContainerOpen containers = new ContainerOpen();
    public final ItemTake items = new ItemTake();
    public final CollisionTris recastTris = new CollisionTris();
    public Set<String> takenKeys = new HashSet<>();
    private final LandMesh landMesh = new LandMesh();
    private final WaterMesh waterMesh = new WaterMesh();
    private final PathgridDebug pathgridDebug = new PathgridDebug();
    private final BulletColliderDebug colliderDebug = new BulletColliderDebug();
    private NavmeshDebug navmeshDebug;
    private String navWorld = "";
    private boolean navDebugShown;
    private final ArrayDeque<NavmeshCache.Tile> navPending = new ArrayDeque<>();

    private final NpcMannequin mannequin = new NpcMannequin(TestData.testdataRoot());
    private EsmFile.LoadedCell cell;
    private SceneNode buildingRoot;
    private Map<String, Integer> byRec;
    private int refIndex;
    private int landIndex;
    private int gpu;
    private int colTile;
    private int colObj;
    private boolean colLandDone;
    private boolean bulletLive;
    /** Walk-grid builds must not wipe the live Bullet world until swap. */
    public boolean deferBullet;
    private final List<BulletWorld.Staged> staged = new ArrayList<>();
    private final List<EsmFile.GridTile> colOrder = new ArrayList<>();
    private boolean finished;
    private Random levcRng;
    private final List<PendingLight> pendingLights = new ArrayList<>();
    private final List<CollisionMesh.Pending> pendingCol = new ArrayList<>();
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
        cancelNavmesh();
        navWorld = NavmeshDb.worldspace(cell);
        navmeshDebug = NavmeshDebug.of(navWorld);
        mannequin.setNavWorld(navWorld);
        navPath = 0;
        navPolys = NavmeshCache.polys(navWorld);
        navTiles = NavmeshCache.tiles(navWorld);
        navSource = navTiles > 0 ? "db" : "none";
        placed = skippedUnknown = skippedEmpty = skippedActor = skippedDeleted = skippedNif = placedStat = placedNpc
            = placedCrea = placedLevc = skippedLevcNone = 0;
        levcRng = new Random();
        log.setLength(0);
        byRec = new TreeMap<>();
        buildingRoot = new SceneNode();
        buildingRoot.name = "cell-root:" + cell.name;
        buildingRoot.local.setToRotation(1, 0, 0, -90);
        refIndex = 0;
        landIndex = 0;
        gpu = 0;
        colTile = 0;
        colObj = 0;
        colLandDone = false;
        bulletLive = false;
        disposeStaged();
        colOrder.clear();
        finished = false;
        pendingLights.clear();
        doors.clear();
        containers.clear();
        items.clear();
        pendingCol.clear();
        pathgridDebug.dispose();
        colliderDebug.dispose();
        recastTris.clear();
        lighting.lights.clear();
        lighting.resetTime();
        lighting.exterior = !cell.interior;
        System.arraycopy(cell.ambient, 0, lighting.ambient, 0, 3);
        System.arraycopy(cell.sunlight, 0, lighting.sunDiffuse, 0, 3);
        if (cell.interior) {
            lighting.configureFog(cell.fogColor, cell.fogDensity);
        } else {
            lighting.configureFog(cell.fogColor, 0f);
            lighting.ambient[0] = 0.55f;
            lighting.ambient[1] = 0.58f;
            lighting.ambient[2] = 0.62f;
            lighting.sunDiffuse[0] = 1f;
            lighting.sunDiffuse[1] = 0.98f;
            lighting.sunDiffuse[2] = 0.9f;
        }
        if (!cell.interior) {
            waterMesh.attach(buildingRoot, cell.gridX, cell.gridY);
        }
    }

    /** Place refs until {@code budgetNanos} elapses. Returns true when the cell is done. */
    public boolean step(long budgetNanos) {
        if (finished) {
            return true;
        }
        long start = System.nanoTime();
        if (gpu == 0) {
            if (!cell.interior) {
                while (landIndex < landCount()) {
                    PerfTrace.begin("load.land");
                    LandRecord land = cell.lands.isEmpty() ? cell.land : cell.lands.get(landIndex);
                    List<LandRecord> neighbors = cell.lands.isEmpty() ? List.of(land) : cell.lands;
                    landMesh.attach(buildingRoot, land, cell.landTextures, neighbors);
                    PerfTrace.end();
                    landIndex++;
                    if (System.nanoTime() - start >= budgetNanos) {
                        return false;
                    }
                }
            }
            gpu = 1;
        }
        if (gpu == 1) {
            while (refIndex < cell.refs.size()) {
                PerfTrace.begin("load.place");
                CellRef ref = cell.refs.get(refIndex++);
                PerfTrace.detail(ref.refId);
                place(ref);
                PerfTrace.end();
                if (System.nanoTime() - start >= budgetNanos) {
                    return false;
                }
            }
            gpu = 2;
        }
        if (gpu == 2) {
            PerfTrace.begin("load.prep");
            finishPrep();
            if (!deferBullet) {
                BulletWorld.rebuild();
            }
            PerfTrace.end();
            gpu = 3;
            return false;
        }
        if (gpu == 3) {
            if (deferBullet) {
                if (!fillCurrentCollision(start, budgetNanos, false, true)) {
                    return false;
                }
                finishLog();
                finished = true;
                return true;
            }
            if (!fillCurrentCollision(start, budgetNanos, false, false)) {
                return false;
            }
            bulletLive = true;
            colTile = Math.min(1, colOrder.size());
            colObj = 0;
            colLandDone = false;
            finishLog();
            finished = true;
            return true;
        }
        return true;
    }

    /** After a walk-grid swap: replace the live Bullet world with this cell’s pre-cooked bodies. */
    public void attachLiveCollision() {
        if (bulletLive || cell == null) {
            return;
        }
        BulletWorld.rebuild();
        PerfTrace.begin("load.adopt");
        if (!staged.isEmpty()) {
            for (BulletWorld.Staged body : staged) {
                BulletWorld.adopt(body);
            }
            staged.clear();
            if (cell.interior) {
                BulletWorld.markInteriorReady();
            } else {
                BulletWorld.markReady(cell.gridX, cell.gridY);
            }
        } else {
            fillCurrentCollision(System.nanoTime(), Long.MAX_VALUE, true, false);
        }
        PerfTrace.end();
        bulletLive = true;
        colTile = Math.min(1, colOrder.size());
        colObj = 0;
        colLandDone = false;
    }

    public boolean bulletLive() {
        return bulletLive;
    }

    public SceneNode end() {
        return buildingRoot;
    }

    /** Overlapping NPCs/creatures keep the live TES pose; new ring cells spawn as placed. */
    public void takeWanderFrom(CellSceneBuilder live) {
        if (live == null || live == this) {
            return;
        }
        mannequin.copyWanderFrom(live.mannequin, cell);
    }

    public void update(float dt, Vector3 eye, float hoursPassed) {
        syncDebugOverlays();
        PerfTrace.begin("update.col");
        pumpCollision(4_000_000L);
        PerfTrace.end();
        PerfTrace.begin("update.f7");
        ensureColliderDebug();
        PerfTrace.end();
        PerfTrace.begin("update.nav");
        pumpNavmesh();
        PerfTrace.end();
        PerfTrace.begin("update.npc");
        mannequin.update(dt, hoursPassed, cell);
        PerfTrace.end();
        navPath = NavmeshQuery.lastPath(navWorld);
        waterMesh.follow(eye);
        PerfTrace.begin("update.doors");
        doors.process(dt);
        containers.process(dt);
        PerfTrace.end();
        if (buildingRoot != null) {
            Matrix4 id = new Matrix4();
            PerfTrace.begin("update.xform");
            buildingRoot.updateWorld(id);
            BulletWorld.syncFollowers();
            if (doors.blockOnPlayer(eye)) {
                buildingRoot.updateWorld(id);
                BulletWorld.syncFollowers();
            }
            PerfTrace.end();
        }
    }

    private void startNavmesh() {
        NavmeshCache.request(cell, recastTris);
        navPolys = NavmeshCache.polys(navWorld);
        navTiles = NavmeshCache.tiles(navWorld);
        navSource = "load";
        navPatch = NavmeshCache.patches(navWorld);
    }

    private void pumpNavmesh() {
        if (buildingRoot == null) {
            return;
        }
        boolean show = NavmeshDebug.visible && finished && navmeshDebug != null;
        if (show && !navDebugShown) {
            navmeshDebug.attachTo(buildingRoot);
            navmeshDebug.clearMeshes();
            navPending.clear();
            NavmeshCache.discardReady(navWorld);
            navPending.addAll(NavmeshCache.storedTiles(navWorld));
            navDebugShown = true;
        } else if (!show && navDebugShown && navmeshDebug != null) {
            navmeshDebug.clearMeshes();
            navmeshDebug.detachFrom(buildingRoot);
            navPending.clear();
            navDebugShown = false;
        }
        if (!show) {
            NavmeshCache.discardReady(navWorld);
        } else {
            navmeshDebug.attachTo(buildingRoot);
            NavmeshCache.Tile tile;
            while ((tile = NavmeshCache.poll(navWorld)) != null) {
                navPending.add(tile);
            }
            long start = System.nanoTime();
            while (System.nanoTime() - start < 4_000_000L && !navPending.isEmpty()) {
                navmeshDebug.addTile(navPending.poll());
            }
        }
        navPolys = NavmeshCache.polys(navWorld);
        navTiles = NavmeshCache.tiles(navWorld);
        navSource = NavmeshCache.source(navWorld);
        navPath = NavmeshQuery.lastPath(navWorld);
        navPatch = NavmeshCache.patches(navWorld);
    }

    private void cancelNavmesh() {
        if (navmeshDebug != null) {
            navmeshDebug.detachFrom(buildingRoot);
        }
    }

    private void finishPrep() {
        Matrix4 id = new Matrix4();
        buildingRoot.updateWorld(id);
        PerfTrace.begin("prep.recast");
        recastTris.bake(pendingCol);
        PerfTrace.end();
        if (!deferBullet) {
            BulletWorld.rebuild();
        }
        PerfTrace.begin("prep.lights");
        finishLights();
        PerfTrace.end();
        PerfTrace.begin("prep.nav");
        startNavmesh();
        PerfTrace.end();
        buildColOrder();
        colTile = 0;
        colObj = 0;
        colLandDone = false;
    }

    private void finishLog() {
        log.insert(0, "cell=" + cell.name + " refs=" + cell.refs.size() + " placed=" + placed
            + " npc=" + placedNpc + " crea=" + placedCrea + " levc=" + placedLevc + " levcNone=" + skippedLevcNone
            + " byRec=" + byRec + " empty=" + skippedEmpty
            + " actor=" + skippedActor
            + " unknown=" + skippedUnknown + " deleted=" + skippedDeleted + " nifFail=" + skippedNif
            + " lights=" + lighting.lights.size() + " fog=" + lighting.fogDensity
            + " doors=" + doors.swingCount() + "+" + doors.teleportCount()
            + " cont=" + containers.withOpen() + "/" + containers.containers.size()
            + " take=" + items.takeCount()
            + " nav=" + navPolys + " tiles=" + navTiles + " navSrc=" + navSource
            + (cell.interior ? "" : " land=" + (int) cell.land.minHeight + ".." + (int) cell.land.maxHeight
                + " vtex=" + cell.land.uniqueVtex() + " ltex=" + cell.landTextures.size())
            + '\n');
    }

    private void buildColOrder() {
        colOrder.clear();
        if (cell.interior || cell.tiles.isEmpty()) {
            return;
        }
        EsmFile.GridTile center = null;
        List<EsmFile.GridTile> rest = new ArrayList<>();
        for (EsmFile.GridTile tile : cell.tiles) {
            if (tile.gridX == cell.gridX && tile.gridY == cell.gridY) {
                center = tile;
            } else {
                rest.add(tile);
            }
        }
        if (center != null) {
            colOrder.add(center);
        }
        int cx = cell.gridX;
        int cy = cell.gridY;
        rest.sort((a, b) -> {
            int da = Math.max(Math.abs(a.gridX - cx), Math.abs(a.gridY - cy));
            int db = Math.max(Math.abs(b.gridX - cx), Math.abs(b.gridY - cy));
            return Integer.compare(da, db);
        });
        colOrder.addAll(rest);
    }

    private boolean fillCurrentCollision(long start, long budgetNanos, boolean all, boolean stage) {
        if (cell.interior) {
            while (colObj < pendingCol.size()) {
                addCollisionObject(pendingCol.get(colObj++), stage);
                if (!all && System.nanoTime() - start >= budgetNanos) {
                    return false;
                }
            }
            if (!stage) {
                BulletWorld.markInteriorReady();
            }
            return true;
        }
        if (colOrder.isEmpty()) {
            if (!colLandDone) {
                List<LandRecord> lands = cell.land == null ? List.of()
                    : cell.lands.isEmpty() ? List.of(cell.land) : cell.lands;
                addCollisionLand(lands, stage);
                colLandDone = true;
                if (!all && System.nanoTime() - start >= budgetNanos) {
                    return false;
                }
            }
            while (colObj < pendingCol.size()) {
                addCollisionObject(pendingCol.get(colObj++), stage);
                if (!all && System.nanoTime() - start >= budgetNanos) {
                    return false;
                }
            }
            if (!stage) {
                BulletWorld.markReady(cell.gridX, cell.gridY);
            }
            return true;
        }
        return addTileCollision(colOrder.get(0), start, budgetNanos, all, stage);
    }

    private boolean addTileCollision(EsmFile.GridTile tile, long start, long budgetNanos, boolean all) {
        return addTileCollision(tile, start, budgetNanos, all, false);
    }

    private boolean addTileCollision(EsmFile.GridTile tile, long start, long budgetNanos, boolean all, boolean stage) {
        if (!colLandDone) {
            if (tile.land != null) {
                addCollisionLand(List.of(tile.land), stage);
            }
            colLandDone = true;
            if (!all && System.nanoTime() - start >= budgetNanos) {
                return false;
            }
        }
        while (colObj < pendingCol.size()) {
            CollisionMesh.Pending pnd = pendingCol.get(colObj);
            if (pnd.gridX != tile.gridX || pnd.gridY != tile.gridY) {
                colObj++;
                continue;
            }
            addCollisionObject(pnd, stage);
            colObj++;
            if (!all && System.nanoTime() - start >= budgetNanos) {
                return false;
            }
        }
        if (!stage) {
            BulletWorld.markReady(tile.gridX, tile.gridY);
        }
        return true;
    }

    private void addCollisionLand(List<LandRecord> lands, boolean stage) {
        if (stage) {
            if (lands == null) {
                return;
            }
            for (LandRecord land : lands) {
                BulletWorld.Staged body = BulletWorld.cookLand(land);
                if (body != null) {
                    staged.add(body);
                }
            }
            return;
        }
        BulletWorld.addLand(lands);
    }

    private void addCollisionObject(CollisionMesh.Pending pnd, boolean stage) {
        if (stage) {
            BulletWorld.Staged body = BulletWorld.cookObject(pnd);
            if (body != null) {
                staged.add(body);
            }
            return;
        }
        BulletWorld.addObject(pnd);
    }

    private void pumpCollision(long budgetNanos) {
        if (!finished || !bulletLive || cell == null || cell.interior || colTile >= colOrder.size()) {
            return;
        }
        long start = System.nanoTime();
        while (colTile < colOrder.size()) {
            if (addTileCollision(colOrder.get(colTile), start, budgetNanos, false)) {
                colTile++;
                colObj = 0;
                colLandDone = false;
            }
            if (System.nanoTime() - start >= budgetNanos) {
                return;
            }
        }
    }

    public String activateLooking(Vector3 origin, Vector3 direction) {
        DoorSwing.Hit door = doors.nearest(origin, direction);
        ContainerOpen.Hit cont = containers.nearest(origin, direction);
        ItemTake.Hit item = items.nearest(origin, direction);
        NpcMannequin.ActorPick actor = mannequin.nearestPackages(origin, direction, cell);
        float doorDist = door == null ? Float.POSITIVE_INFINITY : door.dist;
        float contDist = cont == null ? Float.POSITIVE_INFINITY : cont.dist;
        float itemDist = item == null ? Float.POSITIVE_INFINITY : item.dist;
        float actorDist = actor == null ? Float.POSITIVE_INFINITY : actor.dist;
        if (actor != null && actorDist < doorDist && actorDist < contDist && actorDist < itemDist) {
            return actor.text;
        }
        if (door == null && cont == null && item == null) {
            return null;
        }
        if (doorDist <= contDist && doorDist <= itemDist) {
            return doors.activate(door);
        }
        if (contDist <= itemDist) {
            return containers.activate(cont);
        }
        String msg = items.activate(item, lighting);
        if (!item.item.book && item.item.takeKey != null) {
            takenKeys.add(item.item.takeKey);
        }
        return msg;
    }

    /** Name under the crosshair, or empty when it is not on an NPC or creature. */
    public String aimName(Vector3 origin, Vector3 direction) {
        return mannequin.lookName(origin, direction, cell);
    }

    public int refCount() {
        return cell == null ? 0 : cell.refs.size();
    }

    public int refIndex() {
        return refIndex;
    }

    public int landCount() {
        if (cell == null || cell.interior) {
            return 0;
        }
        if (!cell.lands.isEmpty()) {
            return cell.lands.size();
        }
        return cell.land == null ? 0 : 1;
    }

    public int landIndex() {
        return landIndex;
    }

    public String gpuPhase() {
        if (cell == null) {
            return "";
        }
        if (!finished) {
            if (!cell.interior && landIndex < landCount()) {
                return "loading land " + landIndex + "/" + landCount();
            }
            if (gpu <= 1 && refIndex < refCount()) {
                return "loading models " + refIndex + "/" + Math.max(1, refCount());
            }
            return "loading collision";
        }
        if (bulletLive && colTile < colOrder.size()) {
            return "loading collision " + colTile + "/" + colOrder.size();
        }
        return "loading models " + refIndex + "/" + Math.max(1, refCount());
    }

    public float tileProgress(int gx, int gy) {
        if (cell == null || cell.interior || cell.tiles.isEmpty()) {
            return 0f;
        }
        EsmFile.GridTile tile = null;
        int idx = -1;
        for (int i = 0; i < cell.tiles.size(); i++) {
            EsmFile.GridTile candidate = cell.tiles.get(i);
            if (candidate.gridX == gx && candidate.gridY == gy) {
                tile = candidate;
                idx = i;
                break;
            }
        }
        if (tile == null) {
            return 0f;
        }
        if (finished) {
            return 1f;
        }
        if (landIndex <= idx) {
            return 0f;
        }
        if (tile.refs <= 0) {
            return 1f;
        }
        int done = Math.max(0, Math.min(tile.refs, refIndex - tile.refStart));
        return 0.25f + 0.75f * (done / (float) tile.refs);
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
            if (npc != null) {
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
            EsmCreature crea = cell.creatures.get(key);
            if (crea == null) {
                skippedActor++;
                log.append("skip actor=").append(ref.refId).append('\n');
                return;
            }
            try {
                SceneNode inst = mannequin.buildCreature(crea, ref, cell);
                buildingRoot.addChild(inst);
                placed++;
                placedCrea++;
                byRec.merge("CREA", 1, Integer::sum);
            } catch (Exception e) {
                skippedNif++;
                log.append("crea fail ").append(ref.refId).append(" ").append(e.getMessage()).append('\n');
                Gdx.app.error("CellSceneBuilder", "CREA " + ref.refId, e);
            }
            return;
        }
        EsmLevc list = cell.levc.get(key);
        if (list != null) {
            placeLevc(ref, list);
            return;
        }
        EsmObject obj = cell.objects.get(key);
        if (obj == null) {
            skippedUnknown++;
            log.append("skip id=").append(ref.refId).append('\n');
            return;
        }
        if ((EsmObject.isTakeable(obj) || EsmObject.isBook(obj)) && takenKeys.contains(ref.takeKey())) {
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
            String mesh = placeMesh(obj);
            SceneNode inst = instance(mesh);
            EsmTransforms.setLocal(inst.local, ref.pos, ref.rot, ref.scale);
            inst.name = ref.refId;
            buildingRoot.addChild(inst);
            CollisionMesh col = CollisionMesh.intern(mesh);
            if (!col.isEmpty()) {
                int gx = cell.interior ? 0 : LandRecord.cellGrid(ref.pos[0]);
                int gy = cell.interior ? 0 : LandRecord.cellGrid(ref.pos[1]);
                pendingCol.add(new CollisionMesh.Pending(col, inst, gx, gy, livePose(obj)));
            }
            placed++;
            if ("STAT".equals(obj.rec)) {
                placedStat++;
            }
            if ("DOOR".equals(obj.rec)) {
                doors.add(inst, ref);
            }
            if ("CONT".equals(obj.rec)) {
                containers.add(inst, ref.refId, obj.model);
            }
            if (EsmObject.isTakeable(obj) || EsmObject.isBook(obj)) {
                items.add(inst, ref, obj);
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

    private void placeLevc(CellRef ref, EsmLevc list) {
        String id = LevelledCreatures.pickOrRemember(ref, list, EsmLevc.PLAYER_LEVEL, levcRng, cell.levc,
            cell.creatures);
        if (id.isEmpty()) {
            skippedLevcNone++;
            return;
        }
        EsmCreature crea = cell.creatures.get(id.toLowerCase(Locale.ROOT));
        if (crea == null) {
            skippedLevcNone++;
            log.append("levc miss ").append(ref.refId).append(" -> ").append(id).append('\n');
            return;
        }
        try {
            SceneNode inst = mannequin.buildCreature(crea, ref, cell);
            buildingRoot.addChild(inst);
            placed++;
            placedCrea++;
            placedLevc++;
            byRec.merge("LEVC", 1, Integer::sum);
        } catch (Exception e) {
            skippedNif++;
            log.append("levc fail ").append(ref.refId).append(" ").append(e.getMessage()).append('\n');
            Gdx.app.error("CellSceneBuilder", "LEVC " + ref.refId, e);
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
            items.bindLight(pending.node, light);
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

    /** F5 and F7 meshes exist only while those overlays are on. */
    private void syncDebugOverlays() {
        if (buildingRoot == null || cell == null || !finished) {
            return;
        }
        if (PathgridDebug.visible) {
            if (!pathgridDebug.attached()) {
                pathgridDebug.attach(buildingRoot, cell);
            }
        } else if (pathgridDebug.attached()) {
            pathgridDebug.dispose();
        }
        if (!BulletColliderDebug.visible && colliderDebug.attached()) {
            colliderDebug.dispose();
        }
    }

    private void ensureColliderDebug() {
        if (!BulletColliderDebug.visible || colliderDebug.attached() || buildingRoot == null || !finished) {
            return;
        }
        List<LandRecord> lands = cell == null || cell.interior || cell.land == null ? List.of()
            : cell.lands.isEmpty() ? List.of(cell.land) : cell.lands;
        colliderDebug.attach(buildingRoot, pendingCol, lands);
    }

    private void disposeStaged() {
        for (BulletWorld.Staged body : staged) {
            BulletWorld.disposeStaged(body);
        }
        staged.clear();
    }

    private static boolean livePose(EsmObject obj) {
        return "DOOR".equals(obj.rec) || "CONT".equals(obj.rec) || EsmObject.isTakeable(obj);
    }

    public void dispose() {
        cancelNavmesh();
        disposeStaged();
        pathgridDebug.dispose();
        colliderDebug.dispose();
        landMesh.dispose();
        waterMesh.dispose();
        mannequin.dispose();
    }

    private String placeMesh(EsmObject obj) {
        String mesh = TexturePaths.normalizeMeshPath(obj.model);
        if (!"CONT".equals(obj.rec) && !"DOOR".equals(obj.rec)) {
            return mesh;
        }
        String anim = TexturePaths.correctActorModelPath(mesh, TestData::vfsExists);
        if (anim.equals(mesh) || TestData.vfsExists(anim)) {
            return anim;
        }
        return mesh;
    }

    private SceneNode instance(String model) throws Exception {
        String vfs = TexturePaths.normalizeMeshPath(model);
        return NpcMannequin.cloneTree(GpuCache.meshTemplate(vfs));
    }
}

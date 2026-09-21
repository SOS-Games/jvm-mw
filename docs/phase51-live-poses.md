# Phase 51: Live door and take colliders

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwphysics/object.cpp` (`commitPositionChange` sets the collision object’s origin + rotation; scale is `setLocalScaling`, not the world matrix) and `apps/openmw/mwworld/worldimp.cpp` (`rotateDoor`). Do **not** port animated compound child shapes, actor capsules, or `mtphysics`. No other-LLM prompts: door swing is already `EsmTransforms.setLocal`; this slice only keeps Bullet on that node.

This is [physics.md](physics.md) **3**. Player WASD and NPC feet stay on Bullet. Recast stay load-pose. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander / Chair HUD stay. **A swung hide door blocks the camera. A taken world item’s hull leaves Bullet.** Vanilla Census chests have no lid clip. Extra-data lid bones do not get their own hull this phase. Chair still flies.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

Doors and pickups already move in the scene graph. Bullet still holds the load pose, so you walk through an open hide door and bump the empty air where a bottle was.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`. Do not change Recast / Detour / sqlite / F5 / F6 / player WASD / NPC stick.

- **Doors / containers / takeable (non-book) meshes:** cook Bullet tris in **instance-local** space (interned `CollisionMesh`). World transform is rotation + translation from `SceneNode.world`. Uniform scale is `setLocalScaling` on the shape, never in the matrix (trees stay world-space identity). After `doors.process` / `containers.process` and `updateWorld`, copy that pose onto the body and refresh its AABB.
- **Take:** when **E** unparents a world item, remove that node’s Bullet body (dispose shape/mesh). Books still only log.
- **Leave alone:** STAT / flora world-space bodies. Recast snapshot at load. F7 may stay at load pose. Teleport doors do not swing. Actor-actor out.
- Class comments / AGENTS.md: open hide doors block WASD; take removes the hull.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

## Out of scope

- Per-bone chest lid hulls / `animateCollisionShapes`
- Actor vs shacks ([physics.md](physics.md) **4**), actor-actor, swim, `mtphysics`
- Recast / Detour / sqlite / Phase 44 patch
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Cave** (Addamasartus): **E** a hide door (`door_cavern_doors00`). While it is open, WASD into the leaf — it stops you. **E** again to close; you can pass the mouth. The cave **exit** door still teleports and does not swing.

**Town:** pick up a world bottle / ingredient (not a book). The mesh goes; you walk through that spot. Dirt and docks still hold you.

**Cell:** Census wood doors swing and block. `stolen_goods` still has no lid clip.

Chair HUD still flies. F6 / `src=db` / `patch=` unchanged.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Open hide door blocks the camera. Closed, you can walk the gap. `glError=0`.
- Taken item no longer has a hull. Player dirt / docks unchanged.
- Census teleport exits still load, not swing. Recast carpet still appears.

Walking through an **open** hide door is a **fail**. Walking through a closed hide door is **not** a fail (the leaf is out of the way). Walking through an NPC or a swung door’s **visual** that Bullet has not caught up for one frame is **not** a fail. Extra-data chest lids you can still walk through is **not** a fail. Recast still going around a closed door after it opened is **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Object::commitPositionChange` | `BulletWorld` follow `SceneNode.world` | rewrite |
| `World::deleteObject` collision | `BulletWorld.remove` on take | rewrite |

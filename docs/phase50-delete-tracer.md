# Phase 50: Delete the custom tracer

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with Recast gather already in `NavmeshBaker.gatherTesRecast` (land + object tris, same agent numbers). Do **not** port actor capsules, live door poses, or `mtphysics`. No other-LLM prompts: Recast already copies `CollisionMesh` world verts; this slice only moves that copy off `CollisionWorld` and deletes the unused tracer.

This is [physics.md](physics.md) **2.2**. Player WASD and NPC feet stay on Bullet ([phase 48](phase48-bullet-walk.md), [phase 49](phase49-npc-bullet-stick.md)). **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander / Chair HUD stay. **Delete `CollisionWorld`.** Recast still gathers the same shack triangles from interned `CollisionMesh` placements (snapshotted at load so the nav worker does not read live `SceneNode` matrices). Capsule numbers live on `BulletWorld`. Player and NPC feel do not change.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

Nothing still traces with the old triangle loop. Recast was the last caller. Keep the snapshot, drop the class.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`. Do not change Recast config, tile size, Detour, sqlite, F5 / F6 / F7, player WASD, or NPC stick.

- **Pending:** move `CollisionWorld.Pending` onto `CollisionMesh` (interned tris + instance node + TES grid). Bullet, F7, and Recast share that list.
- **Recast:** a small `CollisionTris` snapshot (`bake` / `appendObjectTris` / `clear`) copied on the GL thread at the same moment as today’s `collision.bake`. `NavmeshBaker.gatherTesRecast` / `bakeRuntime` / the patch worker take that snapshot (nullable; headless `navdb` still gathers land only). Same AABB overlap, same world-space verts. No Recast numbers change.
- **Constants:** `EYE_HEIGHT` 96, `HEIGHT` 128, `RADIUS` 30, step 34/62, ground offset 1, gravity 627, margin 0.2 live on `BulletWorld`. `JvmMwApp` dump / spawn eye height reads those.
- **Delete:** `CollisionWorld.java` (triangle loop, `fits`, `move`, `snapSpawn`, `landHeight`, interned-float traces). No leftover dual path.
- Class comments: Recast snapshot is not a physics world. `BulletWorld` is the only walk world. Update AGENTS.md: the old tracer is gone; Recast still uses `CollisionMesh`.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

## Out of scope

- Actor capsules vs World (shacks) ([physics.md](physics.md) **4**)
- Actor-actor, live door / chest transforms, projectiles, swim, `mtphysics`
- Recast / Detour / sqlite / Phase 44 patch logic
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Town** (app default): Census `DODT`. Dirt and docks hold the camera. Fargoth on dirt. F6 / `src=db` / `patch=` still work; Detour wander still goes around shacks. Walk-grid swap does not drop you through land.

**Cell** / **Cave:** floors and roofs unchanged. Idle NPCs / creatures still on kit floors.

Chair HUD still flies. `debugCli help` (and `navdb` if you run it) still runs without Bullet natives.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Town: player and NPC floors match **2.1**. `glError=0`. F6 carpet and Detour wander still appear.
- Census / Cave: ceilings and kit floors unchanged.
- `CollisionWorld` does not exist. `debugCli help` exits 0.

A missing F6 carpet after sqlite, or a Recast patch that no longer fills cell-edge cracks, is a **fail**. Changing player WASD is a **fail**. Walking through a shack is **not** a fail. Clipping a swung door is **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| Recast object input | `CollisionTris` from `CollisionMesh` placements | rewrite |
| `CollisionWorld` triangle tracer | **deleted** | — |

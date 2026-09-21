# Phase 47: Object meshes in the Bullet world

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/nifbullet/bulletnifloader.cpp` (same `RootCollisionNode` / `NC` / `NCC` as `CollisionMesh`) and `apps/openmw/mwphysics/collisiontype.hpp` (`CollisionType_World` `1<<0`). Do **not** port `mtphysics`, `MovementSolver`, live door poses, or actor capsules. No other-LLM prompts: NIF collision choice is already in `CollisionMesh`.

This is [physics.md](physics.md) **1.3**. Player WASD stays on `CollisionWorld` until **1.4**. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / Detour wander / Chair HUD stay. **Every mesh the old tracer already collides with is a World body in Bullet**, using the same interned `CollisionMesh` and live `SceneNode` world matrix. Walk feel must not change.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

Land is in ([phase 46](phase46-bullet-land.md)). This adds docks, kit, and Census furniture so a World ray can see the boards before WASD switches.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`. Do not change Recast / Detour / sqlite / F5 / F6.

- **When:** same moment as `CollisionWorld.bake` / `BulletWorld.addLand` (`CellSceneBuilder` end of `step`). Use the same `pendingCol` list (non-empty `CollisionMesh` + instance node). Interior has no land, but still has kit / furniture bodies. Chair: no world.
- **What:** one static body per pending placement. Group `CollisionType_World`. Skip empty meshes (NC plants). Skip NPCs / creatures (they are not in `pendingCol`). Water is not a body. Doors and chests use the **load-time** pose; swinging collision is later.
- **Space:** NIF-local tris from interned `CollisionMesh`, instance transform = `SceneNode.world` (cell root −90° already in that matrix). Same Y-up GL as land. Intern one Bullet triangle shape per `CollisionMesh` (VFS) if cheap; otherwise one mesh per placement like today’s world-space copy. Dispose instance bodies on `rebuild()`.
- **Probe (not walk):** keep `btFloorY=` HeightMap-only. Add `btHitY=` a down ray along −Y with **World | HeightMap**. Do not feed WASD, spawn, or NPCs.
- **Do not** change `CollisionWorld`, WASD, spawn snap, NPC stick, Recast gather, or live door / chest collision.
- Dump (`n=60`): keep `bullet=` / `onGround` / `floorY` / `ceilY` / `btFloorY=`. Split `land=` (TES tiles, Town **21**) and `world=` (object bodies). `bodies=` is the sum. Add `btHitY=`.
- Update the `BulletWorld` class comment. Update AGENTS.md Dump: `land=` / `world=` / `btHitY=` (dock boards; `none` if nothing under the camera).

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

## Out of scope

- Player WASD / step / spawn on Bullet ([physics.md](physics.md) **1.4**)
- Fixing or extending `CollisionWorld`
- Live door / chest transforms, actor-actor, projectiles, swim, `mtphysics`
- Recast / Detour / sqlite / Phase 44 patch
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Town** (app default). Dirt at Census `DODT`: `land=21`, `world>0`, `btFloorY` and `btHitY` within **8** of `floorY`. Walk onto a **dock**: walk still on boards (old tracer); `btFloorY` may be **lower** (land); `btHitY` within **8** of `floorY` (the boards). Walk-grid swap: `land=21`, `world>0`, walk unchanged.

**Cave** / **Cell**: `land=0`, `world>0`, `btFloorY=none`, `btHitY` within **8** of `floorY`. Roofs still stop you (old tracer). **Chair:** `bullet=0`, flies.

```bat
gradlew.bat lwjgl3:run
```

Headless `debugCli help` still runs (no Bullet natives).

## Pass / fail

- Town dirt: `glError=0`, `land=21`, `world>0`, both rays match `floorY` within 8. Docks and land **feel** like Phase 36.
- Dock: `btHitY` ≈ `floorY`; `btFloorY` below that is **not** a fail.
- Cave / Census: `land=0` `world>0` `btHitY` ≈ `floorY`. Chair `bullet=0`.
- `debugCli help` exits 0. F5 / F6 / `src=db` / `patch=` / Detour wander / DebugVars unchanged.

Walk through a dock or cave roof is a **fail** (old tracer must still be doing the work). `world=0` on Town or Cave is a **fail**. `btHitY=none` on dirt or dock boards is a **fail**. Switching WASD is a **fail**. Clipping a door that has already swung is **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `BulletNifLoader` instance in `btCollisionWorld` | `BulletWorld` World bodies from `CollisionMesh` | rewrite |

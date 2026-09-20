# Phase 36: Stay on land (and ceilings)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwphysics/movementsolver.cpp` (`move`, `traceDown`, slide on hit), `apps/openmw/mwphysics/stepper.cpp` (step up then forward then down), `apps/openmw/mwphysics/constants.hpp` (`sStepSizeDown = 62`, `sGroundOffset = 1`), `components/misc/constants.hpp` (`sStepSizeUp = 34`, `sMaxSlope = 46`), `apps/openmw/mwphysics/collisiontype.hpp` (player hits **World** + **HeightMap**, not actors), `apps/openmw/mwphysics/heightfield.cpp`, and `components/nifbullet/bulletnifloader.cpp` (collision tris: `RootCollisionNode` if present, else rendered geometry; `NC` extra = no collide; empty `RootCollisionNode` / `NCC` still collides with the **camera**). Do **not** port Bullet, `mtphysics`, projectiles, actor-actor, or swimming. No other-LLM prompts: those numbers and the NIF collision choice are in the pin.

## Goal

Same viewer. Phase 35 land mix / Phase 34 cull / water cameras stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load. Grid stays **5×5-minus-corners**.

**The camera walks on floors and cannot pass through ceilings or walls.** Town dirt, docks, and shack floors hold you. Walking under a dock does not put the eye inside the planks. Census rafters and Addamasartus cave roofs stop upward move. Chair HUD (no cell) still flies.

## Why this slice

WASD is still a fly-cam: Space/Ctrl and look-dolly go through docks, hills, and cave ceilings. Next-topics called this “heightfield + simple object bounds.” AABB-only would turn a cave kit piece into one giant box and block the interior, so this slice uses **land heights plus triangle traces**, still without Bullet. Full physics stays in [big-topics.md](big-topics.md).

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- **Player capsule** in the same Y-up world as the camera. Feet at `eye.y - 96` (`EYE_HEIGHT`). Capsule height **128**, radius **30**. The eye stays inside the capsule so a ceiling hit stops the camera before it embeds.
- **Walk:** WASD is horizontal (yaw, not look-pitch). After the move, resolve hits (slide along the wall). Then stick to the floor: trace down up to **62**; if a walkable hit, snap feet to it + **1**. Slope steeper than **46°** is a wall, not a floor. Step up onto docks and stairs up to **34** (up, then forward, then down) like `Stepper`.
- **Ceiling / headroom:** the same traces must hit triangles above the capsule. Space/Ctrl and scroll-dolly cannot push the capsule through a roof, dock underside, or wall. If there is no ceiling (open sky), Space/Ctrl may still change height, then gravity/floor snap applies on the next ground.
- **Gravity:** if no floor within the 62-unit step-down, fall (OpenMW `GravityConst` 8.96 m/s² × `UnitsPerMeter` ≈ **627** units/s²). No fall damage. No jump.
- **Land:** bilinear sample of each loaded `LandRecord` 65×65 (`tesX = eye.x`, `tesY = -eye.z`, `glY = tesZ`). Treat the heightfield as the floor (and as triangles for steep faces). Water plane is **not** a floor — walking into the bay follows the seafloor. Swimming is out of scope.
- **Meshes:** STAT / DOOR / CONT / furniture already in the cell graph. If the NIF has a `RootCollisionNode` with children, use those tris only. If it has none, use rendered tris (`bulletnifloader` autogenerate). Skip `NC` extra data (plants). `NCC` / empty collision node still collide — the player *is* the camera. Skip NPCs, creatures, sky, water, and `skipMeshes` editor markers. Transforms are the live `SceneNode` world matrices (cell root −90° already applied). Intern collision by VFS path like `GpuCache`. Broadphase: only AABBs near the capsule. Doors keep load-time pose (swinging collision is later).
- **Spawn:** on cell load, `traceDown` from the inbound `DODT` / Census spawn so broken door destinations do not start inside the floor.
- **Chair HUD:** no collision world; keep the old fly-cam.
- Rebuild the collision set on interior load and on walk-grid swap. Dump (`n=60`): `onGround`, `floorY`, `ceilY` (or none). HUD hint: WASD walks; you stay on land. Never `ModelBatch`. Class comment on any new type (plain English, per AGENTS).

## Out of scope

- Bullet / JNI, actor-actor, projectiles, ragdoll
- Swimming, water walking, jump groups, sneak
- NPC/creature physics or navmesh
- Updating collision when a door swings or a chest lid opens
- 1st person body / 3rd person mesh
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Town** (app default): Census `DODT`. Walk to the harbor. Feet stay on dirt and **on the docks**, not through the boards. Walk **under** a dock: the camera stays in the air gap, not inside the planks. Hills hold you; you do not sink through land.

**Cell** (Census office): walk the room. The ceiling / rafters stop Space and look-dolly. Walls block WASD.

**Cave** (Addamasartus): the rock roof stops you. Hide doors still swing with **E**; you may still clip a swung door (collision stays at load pose).

Chair HUD unfogged and still flies.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Town: walking stays on land and docks. Camera does not embed in dock tops or undersides. `glError=0`.
- Census and Cave: camera does not pass through ceiling or solid walls.
- Spawn is on the floor, not under it. Chair still flies. Walk-grid / Dump / F4 / land mix / water unchanged.

Flying through a dock or cave roof is a **fail**. Walking on the seafloor in the bay is **not** a fail. Clipping a door that has already swung open is **not** a fail. NPCs you can walk through are **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `MovementSolver::move` / `traceDown` | player capsule traces | rewrite |
| `Stepper` `sStepSizeUp` 34 / `sStepSizeDown` 62 | dock/stair step | rewrite |
| `sMaxSlope` 46 / `sGroundOffset` 1 | walkable floor | same |
| `HeightField` TES3 65×65 | `LandRecord` bilinear + faces | rewrite |
| `BulletNifLoader` RootCollisionNode / NC / NCC | collision tris from NIF | rewrite |

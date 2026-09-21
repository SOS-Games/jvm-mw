# Phase 46: Land in the Bullet world

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwphysics/heightfield.cpp` (`btHeightfieldTerrainShape`, diamond subdivision, verts 65, scale `8192/64`, `CollisionType_HeightMap`), `components/bullethelpers/heightfield.hpp` (`getHeightfieldShift`: cell-centre XY, mid height), and `apps/openmw/mwphysics/collisiontype.hpp`. Do **not** port `mtphysics`, `MovementSolver`, or NIF meshes. No other-LLM prompts: that heightfield constructor is in the pin. Convert into **our Y-up GL** (OpenMW’s Bullet is TES Z-up).

This is [physics.md](physics.md) **1.2**. Player WASD stays on `CollisionWorld` until **1.4**. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / Detour wander / Chair HUD stay. **Each loaded exterior land tile is a HeightMap body in the Phase 45 Bullet world.** Walk feel must not change. Interiors have no land bodies.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

JNI is in ([phase 45](phase45-bullet-wire.md)). This fills land only, so Dump can prove the heightfield sits under Town dirt before docks (objects) or WASD switch.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`. Do not change Recast / Detour / sqlite / F5 / F6.

- **When:** after `CollisionWorld.bake` gets the same `LandRecord` list the tracer already uses (`CellSceneBuilder` end of `step`). `BulletWorld.rebuild()` in `begin()` stays; then add one body per land. Interior / Chair: no land (`bodies=0` / `bullet=0`).
- **What:** one `btHeightfieldTerrainShape` per loaded TES cell (Town **21**, 5×5-minus-corners). 65×65 from `LandRecord.heights`, min/max from that record, diamond subdivision on, local scale 128 in the horizontal axes. Group `CollisionType_HeightMap` (`1<<3`). Keep a copy of the height floats for JNI lifetime. Water plane is **not** a body.
- **Space:** world **Y-up GL**, same as `CollisionWorld.landHeight` (`tesX = glX`, `tesY = -glZ`, height = GL Y). OpenMW’s shift is TES/OSG Z-up — do **not** paste it unrotated. Cell centre in GL is `((gx+0.5)*8192, (minH+maxH)/2, -(gy+0.5)*8192)`. Flip the TES-Y axis onto GL −Z (scale or grid). Prefer `btHeightfieldTerrainShape`. A 65×65 triangle mesh in that same GL space is OK only if the heightfield API will not sit in Y-up; same 21 tiles, still HeightMap.
- **Probe (not walk):** one Bullet ray down from the camera along −Y, HeightMap only. Dump `btFloorY=` (hit Y or `none`). Do not feed WASD, spawn, or NPCs.
- **Do not** change `CollisionWorld`, WASD, spawn snap, NPC stick, or object collision.
- Dump (`n=60`): keep `bullet=` / `onGround` / `floorY` / `ceilY`. `bodies=` is land tiles (Town **21**, Cave **0**, Chair omit with `bullet=0`). Add `btFloorY=`.
- Update the `BulletWorld` class comment (plain English). Update AGENTS.md Dump: `bodies=` is land tiles; `btFloorY=` is the HeightMap ray (`none` indoors).

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

## Out of scope

- Object meshes, convex sweeps, player WASD on Bullet ([physics.md](physics.md) **1.3–1.4**)
- Fixing or extending `CollisionWorld`
- `mtphysics`, actor-actor, projectiles, swim, live door poses
- Recast / Detour / sqlite / Phase 44 patch
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Town** (app default). Census `DODT` on dirt. Walk docks as today (old tracer). F3: `bullet=1` `bodies=21` `btFloorY` within **8** of `floorY` (bilinear vs diamond + `sGroundOffset` 1 is fine). Walk onto a **dock**: `floorY` stays the boards; `btFloorY` may be **lower** (land / seafloor only) — that is the point. Walk far enough for a 5×5 swap: still `bodies=21`, walk unchanged.

**Cave** / **Cell**: `bullet=1` `bodies=0` `btFloorY=none`. Roofs still stop you (old tracer). **Chair:** `bullet=0`, flies.

```bat
gradlew.bat lwjgl3:run
```

Headless `debugCli help` still runs (no Bullet natives).

## Pass / fail

- Town dirt at spawn: `glError=0`, `bodies=21`, `|btFloorY - floorY| < 8`. Docks and land **feel** like Phase 36.
- Dock: walk still on boards; `btFloorY` below `floorY` is **not** a fail.
- Cave / Census: `bodies=0` `btFloorY=none`. Walk swap keeps 21 land bodies. Chair `bullet=0`.
- `debugCli help` exits 0. F5 / F6 / `src=db` / `patch=` / Detour wander / DebugVars unchanged.

Walk through a dock or cave roof is a **fail** (old tracer must still be doing the work). `bodies=0` on Town or `btFloorY=none` on dirt is a **fail**. Switching WASD or adding shack meshes is a **fail**. Matching dock `floorY` with the HeightMap ray is **not** required (no objects yet).

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `HeightField` / `btHeightfieldTerrainShape` | `BulletWorld` land bodies (Y-up GL) | rewrite |

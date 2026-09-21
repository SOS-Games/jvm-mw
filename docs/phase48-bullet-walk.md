# Phase 48: Player WASD on Bullet

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwphysics/movementsolver.cpp` (`move`, `traceDown`, slide), `apps/openmw/mwphysics/stepper.cpp` (up 34, forward, down), `apps/openmw/mwphysics/trace.cpp` (convex sweep / ray), `components/misc/constants.hpp` / `mwphysics/constants.hpp` (`sStepSizeUp` 34, `sStepSizeDown` 62, `sMaxSlope` 46, `sGroundOffset` 1, gravity), and `apps/openmw/mwphysics/collisiontype.hpp` (player hits **World** + **HeightMap**, not actors). Do **not** port `mtphysics`, actor capsules, or live door poses. No other-LLM prompts: those numbers and the stepper order are in the pin and in today’s `CollisionWorld.move`.

This is [physics.md](physics.md) **1.4**. Land and kit are already in Bullet ([phase 46](phase46-bullet-land.md), [phase 47](phase47-bullet-objects.md)). **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander stay. **Player WASD, step, slide, gravity, ceilings, and spawn snap go through Bullet convex sweeps.** Feel must match Phase 36 (dirt, docks, undersides, Census rafters, Addamasartus roof). NPCs stay on `CollisionWorld`. Chair HUD still flies.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

The Bullet world already has land and kit. This is the switch. After it is **working**, freeze `CollisionWorld` (no fixes) and later delete it in (2).

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`. Do not change Recast / Detour / sqlite / F5 / F6 / F7.

- **Callers:** `JvmMwApp` walk (`collision.move`) and spawn (`snapSpawn`) use `BulletWorld`. Chair (no cell) still `eye.add`. Do not call `CollisionWorld.move` for the camera.
- **Capsule:** same numbers as `CollisionWorld` (eye 96, feet-to-head **128**, radius **30**, Y-up GL). Bullet’s `btCapsuleShape` cylinder height is not the full 128 — match today’s feet and eye so a ceiling hit stops the camera before it embeds. The player is **not** a rigid body and is **not** added to the collision world (no self-hit). Kinematic `convexSweepTest` / ray, filter **World | HeightMap** only.
- **Walk:** same order as `CollisionWorld.move`: split long WASD into short slides, wall slide, step up **34** then forward then down, stick to a walkable hit within **62** + ground offset **1**, slope steeper than **46°** is a wall. Space/Ctrl / dolly cannot embed in a roof, dock underside, or wall. Gravity **627** when no floor in the step-down. No jump, no fall damage. Water plane is not a floor.
- **Spawn:** Bullet `traceDown` from inbound `DODT` / Census spawn (interior vs exterior distances like today’s `snapSpawn`).
- **Dump (`n=60`):** `onGround` / `floorY` / `ceilY` from the Bullet walk (not `CollisionWorld`). Keep `bullet=` / `land=` / `world=` / `btFloorY=` / `btHitY=`. After the switch, dirt and dock `floorY` should match `btHitY` within **8**.
- **Leave alone:** `CollisionWorld.bake` / NPC `stickLand` / Recast `appendObjectTris`. Do not fix or extend that tracer. Doors stay at load pose.
- Class comment on the move helper (plain English). Update AGENTS.md: WASD is Bullet; NPCs still the old tracer; Dump `onGround` / `floorY` / `ceilY` are Bullet.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

## Out of scope

- Deleting `CollisionWorld` or porting NPC stick ([physics.md](physics.md) **2**)
- Live door / chest transforms, actor-actor, projectiles, swim, `mtphysics`
- Recast / Detour / sqlite / Phase 44 patch
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Town** (app default): Census `DODT`. Dirt and **docks** hold you. Walk **under** a dock: air gap, not inside the planks. Hills hold; bay follows seafloor. F7 still yellow land / orange kit.

**Cell** (Census): rafters stop Space and look-dolly. Walls block WASD.

**Cave** (Addamasartus): roof stops you. Swung hide doors may still clip.

Chair HUD still flies. Walk-grid swap does not drop you through land.

```bat
gradlew.bat lwjgl3:run
```

Headless `debugCli help` still runs (no Bullet natives).

## Pass / fail

- Town: walk on dirt and docks. No embed in dock tops or undersides. `glError=0`. `onGround=true` on dirt; `floorY` ≈ `btHitY`.
- Census and Cave: no pass through ceiling or solid walls. Spawn on the floor.
- Chair flies. F5 / F6 / F7 / `src=db` / `patch=` / Detour wander / DebugVars unchanged. NPCs still clip shacks.

Flying through a dock or cave roof is a **fail**. Walking on the seafloor is **not** a fail. Clipping a swung door is **not** a fail. Walking through an NPC is **not** a fail. Using `CollisionWorld.move` for the camera is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `MovementSolver::move` / `traceDown` | `BulletWorld` player capsule sweeps | rewrite |
| `Stepper` | Bullet step up / forward / down | rewrite |

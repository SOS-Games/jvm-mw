# Phase 49: NPC floor on Bullet

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwphysics/trace.cpp` (`findGround` / down ray) and `apps/openmw/mwphysics/collisiontype.hpp` (World + HeightMap). Do **not** port actor capsules as bodies, `MovementSolver` for NPCs, live door poses, or `mtphysics`. No other-LLM prompts: this slice only replaces today’s bilinear `landHeight` with the Bullet down hit the player already uses.

This is [physics.md](physics.md) **2.1**. Player WASD stays on Bullet ([phase 48](phase48-bullet-walk.md)). `CollisionWorld` stays for Recast gather until **2.2**. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander / Chair HUD stay. **NPC and creature feet snap TES Z from a Bullet World|HeightMap down hit**, not `CollisionWorld.landHeight`. Pathgrid / Detour XY wander does not change. The player feel does not change.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

Wanderers still sit on the TES heightfield. Docks, Census boards, and cave rock are already in Bullet; bilinear land puts people through those floors or leaves interiors at spawn Z. Full actor capsules vs shacks are later ([physics.md](physics.md) **4**). Deleting `CollisionWorld` is **2.2**.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`. Do not change Recast / Detour / sqlite / F5 / F6 / F7 / player WASD.

- **Callers:** `NpcMannequin.stickLand` (wander, spawn, walk-grid copy). Stop using `CollisionWorld.landHeight` for actors. `wander` can drop the `CollisionWorld` argument if nothing else needs it.
- **Hit:** same `BulletWorld.hitY` the dump already uses (World | HeightMap, −Y). GL: `x = tesX`, `z = -tesY`. Start the ray at `tesZ + 64` so a dock or cave floor is in range and a tree canopy is not. Snap `tesPos[2]` to that Y. No ground offset (today’s stick is exact land height).
- **Miss / not ready:** if `hitY` is NaN or `BulletWorld.readyAt` is false, keep the last `tesPos[2]` (spawn Z on first place). Do not fall back to bilinear land.
- **Not a capsule:** actors are still not Bullet bodies. XY is still pathgrid / Detour / straight line. They may still walk through shacks. Freeze wander on an unready cell stays.
- **Leave alone:** `CollisionWorld.bake` / `appendObjectTris` / `move`. Do not delete that class. Do not fix the old tracer. Doors stay at load pose. Player `BulletWorld.move` / spawn snap / Dump `onGround` / `floorY` / `ceilY` unchanged.
- Class comments: `NpcMannequin` feet are Bullet; `CollisionWorld` is Recast gather only. Update AGENTS.md: NPCs sit on Bullet land/docks/floors; they still clip shacks.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

## Out of scope

- Deleting `CollisionWorld` or changing Recast gather ([physics.md](physics.md) **2.2**)
- Actor capsules vs World (shacks) ([physics.md](physics.md) **4**)
- Actor-actor, live door / chest transforms, projectiles, swim, `mtphysics`
- Recast / Detour / sqlite / Phase 44 patch
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Town** (app default): Census `DODT`. Fargoth / harbor walkers stand on dirt, not buried or hovering. If a wanderer steps onto a **dock**, feet on the boards (`btHitY`), not the seafloor under them.

**Cell** (Census): Sellus Gravius and the other office NPCs stand on the floorboards, not spawn Z in the air or through the planks.

**Cave** (Addamasartus): standing creatures sit on the rock floor.

Chair HUD still flies. Player dirt / docks / ceilings unchanged. Walk-grid copy keeps wanderers where they stood, then resticks with Bullet.

```bat
gradlew.bat lwjgl3:run
```

Headless `debugCli help` still runs (no Bullet natives).

## Pass / fail

- Town: NPCs on dirt. A dock walker on boards is a **pass**; still on seafloor under the dock is a **fail**. `glError=0`.
- Census / Cave: idle NPCs / creatures on the kit floor, not floating at ESM Z.
- Player walk, Chair fly, F5 / F6 / F7 / `src=db` / `patch=` / DebugVars unchanged. They may still walk through shacks.

Using `CollisionWorld.landHeight` for actors is a **fail**. Changing player WASD is a **fail**. Walking through a shack is **not** a fail. Clipping a swung door is **not** a fail. Deleting `CollisionWorld` this phase is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `ActorTracer::findGround` | `NpcMannequin.stickLand` via `BulletWorld.hitY` | rewrite |

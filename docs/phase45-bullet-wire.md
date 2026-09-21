# Phase 45: Wire `gdx-bullet` (empty world)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwphysics/physicssystem.cpp` (`btDefaultCollisionConfiguration`, `btCollisionDispatcher`, `btDbvtBroadphase`, `btCollisionWorld`, `setForceUpdateAllAabbs(false)`). Do **not** port `mtphysics`, `MovementSolver`, heightfields, or NIF meshes. No other-LLM prompts: that constructor is in the pin.

This is [physics.md](physics.md) **1.1**. Player WASD stays on `CollisionWorld` until **1.4**. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / Detour wander / Chair HUD stay. **JNI Bullet is loaded, and one empty `btCollisionWorld` exists whenever a cell is loaded.** Walk feel must not change.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

[physics.md](physics.md) **(1)** is several specs. This one only proves natives + a collision world we can fill later. Putting land or WASD in the same phase would mix JNI bring-up with walk regressions.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`.

- **Library:** `gdx-bullet` at libGDX **1.14.0** (`gdxVersion`). Java API on `core` (`api`). Desktop natives on `lwjgl3` (`gdx-bullet-platform:$gdxVersion:natives-desktop`), same pattern as `gdx` / `gdx-platform`. No other physics engine. Do not vendor Bullet C++.
- **Init:** `Bullet.init()` once on the GL thread in the viewer (`JvmMwApp.create`). `:core:debugCli` must **not** init Bullet or construct the world (headless has no natives).
- **World:** one `btCollisionWorld` (not a dynamics / rigid-body world) with default configuration, dispatcher, and `btDbvtBroadphase`. `setForceUpdateAllAabbs(false)` like OpenMW. No collision objects yet (`bodies=0`). Space is the same Y-up GL as `CollisionWorld` (not TES Z-up).
- **Lifetime:** create when a cell graph exists (Town, Cave, Census, …). Dispose natives on Chair HUD (no cell), on app dispose, and rebuild at the same moments as `CollisionWorld.clear()` (interior load and walk-grid swap). Empty recreate is enough; the hook must exist so **1.2** can fill land there. GL thread only — Recast / sqlite workers do not touch Bullet.
- **Do not** change `CollisionWorld`, WASD, spawn snap, NPC stick, Recast, Detour, or F5 / F6.
- Dump (`n=60`): add `bullet=1` when the JNI world exists, `bullet=0` on Chair. `bodies=0` this slice. Keep `onGround` / `floorY` / `ceilY` from `CollisionWorld`.
- Class comment on the new wrapper (plain English, per AGENTS). Update AGENTS.md Dump line: `bullet=` is the JNI world (`0` on Chair).

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

## Out of scope

- Heightfields, object meshes, convex sweeps, player WASD on Bullet ([physics.md](physics.md) **1.2–1.4**)
- Fixing or extending `CollisionWorld`
- `mtphysics`, actor-actor, projectiles, swim, live door poses
- Recast / Detour / sqlite / Phase 44 patch
- Chair collision
- Keep overlapping tiles, moons, sunglare, weather

## Test cell

**Town** (app default). Walk dirt and docks as today. F3: `bullet=1` `bodies=0`. **Cell** / **Cave** keep `bullet=1`. **Chair** HUD: `bullet=0`, still flies. Walk far enough for a 5×5 swap: still `bullet=1`, walk unchanged. Fargoth / crabs / F5 / F6 unchanged.

```bat
gradlew.bat lwjgl3:run
```

Headless `debugCli help` still runs (no Bullet natives).

## Pass / fail

- Town starts. `glError=0`. `bullet=1` `bodies=0`. Docks and land feel like Phase 36.
- Chair: `bullet=0`, fly-cam. Interior load / walk swap does not crash (dispose / recreate).
- `debugCli help` exits 0 without loading Bullet natives.
- F5 / F6 / `src=db` / `patch=` / Detour wander / DebugVars unchanged.

Walk through a dock or cave roof is a **fail** (old tracer must still be doing the work). Missing natives / UnsatisfiedLinkError on `lwjgl3:run` is a **fail**. `debugCli` pulling Bullet natives is a **fail**. Adding land or switching WASD in this phase is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `btCollisionWorld` + dispatcher / dbvt | gdx-bullet wrapper (empty) | rewrite |

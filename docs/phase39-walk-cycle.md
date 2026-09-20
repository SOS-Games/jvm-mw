# Phase 39: Walk cycle

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/character.cpp` (`movementStateToAnimGroup(CharState_WalkForward)` → `"walkforward"`, `refreshMovementAnims` plays `"start"`/`"stop"` with `loopfallback`), and `apps/openmw/mwrender/animation.cpp` (`play` / `hasAnimation`). Phase 9 already samples the same `.kf` tracks; this slice only changes which text-key group is looping. No other-LLM prompts: the group name and `KfFile.play` are in the pin and already used for idle / chests.

## Goal

Same viewer. Town / Cave / Zain / wander stay. **When an actor is sliding (Phase 38 `walking`), play `walkforward`. When they stop, play `idle` again.**

Census clerks never leave idle. Fargoth and beach vermin look like they are walking, not moonwalking the idle pose. They still clip shacks. No blend, no run, no player body.

HUD buttons unchanged (including **Zain**). Chair HUD unfogged.

## Why this slice

Phase 38 moves them. The bones stay on `idle`, so it looks like a slide. `xbase_anim.kf` and creature kfs already have `walkforward` (`kf meshes/xbase_anim.kf`, `kf meshes/r/xcavemudcrab.kf`). Play that group on the same tracks.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- When wander `walking` becomes true, `play("walkforward", "start", "stop", true)` on anim sources **last-inserted first** (same search as idle). Prefer the kf that is already bound if it has the group. Time starts at the group’s start (or loop start if those keys exist). Keep sampling those tracks; do not load a second kf.
- When `walking` becomes false, switch back to `idle` the same way. Distance-0 actors never switch.
- If `walkforward` is missing, stay on `idle` (still slide). Do not fall back to `runforward`, `walk`, or weapon suffixes (`walkforward1h`, …).
- Hard cut. No OpenMW blend masks, no idle+walk layers, no anim-velocity scale (`getVelocity` / 154). Speed 1, same as idle.
- Same CPU reskin path. Never `ModelBatch`. Update the `NpcMannequin` class comment (they walk when they wander). No new type unless one is actually added.
- Debug CLI: no new command. `kf` already prints `group=walkforward`. `npc` / `crea` keep `wander=`.

```bat
gradlew.bat :core:debugCli --args="kf meshes/xbase_anim.kf"
gradlew.bat :core:debugCli --args="kf meshes/r/xcavemudcrab.kf"
gradlew.bat lwjgl3:run
```

## Out of scope

- Run, sneak, swimwalk, turnleft/right, jump, combat, `idle2`–`idle9`
- Weapon-type suffixes, two-layer blend, lip / talk
- Matching foot speed to 80 TES u/s
- Player mesh / 1st person
- Pathgrid, navmesh, actor collision, sounds

## Test cell

**Town** (app default). Watch Fargoth and the Bitter Coast crabs: legs (or crab gait) cycle while they move; idle pose when they pause.

**Cell** (Census): Sellus and the clerks stay on idle. They must not start a walk in place.

**Cave** / **Zain** / **Nix**: kit and spawn unchanged. A vermin with wander and `walkforward` should gait; one without the group may still slide.

Chair HUD still flies.

## Pass / fail

- Town wanderers with `walkforward` in their kf use that loop while moving and idle when stopped. `glError=0`.
- Census distance-0 NPCs stay idle. Wander, land stick, fog, water, **Zain** halls, Chair unchanged.

Moonwalking after this slice on an actor whose `kf` dump lists `walkforward` is a **fail**. A creature whose kf has no `walkforward` still sliding on idle is **not** a fail. A hard cut when they start/stop is **not** a fail. Feet not matching 80 u/s is **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `movementStateToAnimGroup(WalkForward)` | `KfFile.play("walkforward", …)` | rewrite |

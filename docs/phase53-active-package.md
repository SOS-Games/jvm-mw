# Phase 53: One active AI package

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/aisequence.cpp`: `fill` copies the ESM list onto the actor, the front package is active, and when `execute` returns true a repeating package is reset and cloned onto the back before the active one is erased. No other-LLM prompt: that order is the pin’s `execute`. This slice does not make any package return true.

This is [ai-packages.md](ai-packages.md) **2**. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander / Chair HUD stay. **Each placed NPC and creature keeps their own copy of the package list. The front row is the one that runs. A front wander uses today’s walk with that row’s distance. Any other front kind stands.**

HUD buttons unchanged. DebugVars wander knobs stay. `wander=` on the CLI stays the first `AI_W` distance on the record.

## Why this slice

Phase 52 stored every package and still walked the first wander, even when travel or follow was in front. The active package has to be the front row before duration, travel, or follow can end one and start the next.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`.

- On each placement, copy the record’s package list. Two refs of the same NPC do not share a stack. A 5×5 rebuild keeps that copy with the pose. Loading a cell fresh (a door, a HUD button) starts again from the ESM list.
- The front row is active.
  - Wander: walk with that row’s distance, same pathgrid / Detour / straight line as today. Distance 0 stays.
  - Travel, follow, escort, activate, or an empty list: stand, the same as distance 0. Do not walk a later wander.
- When a package finishes: drop the front row. If its repeat flag is set, put a copy on the back (same fields, its own idle chances). Then the new front decides the walk. Nothing in this slice finishes a package. Duration hours, arriving, and using an object do not run.
- **E** and `actor` / `npc` / `crea` print one new first line, then the same package lines as phase 52:

```
active=W
packages n=1 id=<id> name=<name>
W dist=...
```

`active=` is `W`, `T`, `F`, `E`, `A`, or `none` when the list is empty. **E** prints the placement’s list. The CLI prints the record’s list. They match until something finishes.

- `debugCli` checks the finish rule in memory before the command and prints nothing about it. A repeating travel in front of a repeating wander, after one finish, is wander then a new travel. A second finish is travel then wander. Turning repeat off drops that row. An empty list stays empty. A wrong result throws.

In the whole ESM, three records would change how they walk, and none of them stand in Town, Census, Cave, Guild, Nix, or Zain:

- `malsa ules` — front travel, `wander=256`, Ald-ruhn, Venim Manor Right Wing. She stands.
- `dreynis nothro` and `vala herennius` — front follow, `wander=512`, exterior grid `(-8, 2)`. They stand.

Everyone else is wander-first, or already distance 0. Town looks the same.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat :core:debugCli --args="actor fargoth"
gradlew.bat :core:debugCli --args="actor malsa ules"
gradlew.bat lwjgl3:run
```

Class comments in plain English. AGENTS.md: the front package is what they walk; **E** starts with `active=`. NAME_MAP row for `AiSequence::fill` / the front package.

## Out of scope

- Duration hours ending a wander, idle2–9 playback, the time-of-day byte ([ai-packages.md](ai-packages.md) **3**)
- Walking to a travel point, following, escorting, using the named object (**4–7**)
- `AIDT`, dialogue, script commands, saving the stack
- Changing wander speed, path choice, or DebugVars

## Pass / fail

- `actor fargoth` prints `active=W` and a `W` line whose `dist` matches `wander=`. In Town he still wanders the pathgrid. **E** on him logs that same `active=W` header. **E** on a closer door still swings or teleports.
- `actor malsa ules` prints `active=T` and `wander=256`. `actor dreynis nothro` prints `active=F`. They are not on a HUD button.
- Census clerks and the Seyda Neen wanderers look as they do today. Tarhiel and the two chargen guards still stand (`active=none`).
- `glError=0`. `debugCli help` still exits 0 (the finish check passed).

Fargoth standing is a **fail**. Malsa (or anyone whose first package is not wander) walking a later wander radius is a **fail**. A wander ending because an hour passed is a **fail** (that is slice 3). `wander=` changing for a distance-0 clerk is a **fail**.

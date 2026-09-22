# Phase 54: Wander duration and standing idles

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/aiwander.cpp`: `mRemainingDuration` drops by game hours (`duration * timescale / 3600`), `isPackageCompleted` is `mDuration && mRemainingDuration <= 0`, and on completion the remaining hours reset to the full duration before the package returns true (so a repeat copy starts full). `getRandomIdle` uses GMST `fIdleChanceMultiplier` (0.75) then the highest roll that lands inside each idle chance. Idle index 0 is idle2. `mTimeOfDay` is stored and unused. No other-LLM prompt.

This is [ai-packages.md](ai-packages.md) **3**. The clock is the Clear hour, not OpenMW’s 30× timescale. There is no day counter here. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander / Chair HUD stay. **A front wander ends after its duration in Clear hours. 0 does not end. While that wander stands, idle2–idle9 can play. Walking still plays walkforward.**

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

The list can drop a finished package, but nothing ever finishes, and standing actors only play the plain idle. Duration is what makes a wander get out of the way. The eight chances are what a standing clerk actually does.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`.

- Only the front package, and only when it is wander. Travel, follow, escort, and activate still stand and do not spend hours.
- Duration is a count of Clear hours moved **forward**:
  - **Play** (1 hour per second, including across midnight)
  - **]** (and Shift+] )
  - Dragging the hour slider to a **later** hour
- **[** and dragging the slider **earlier** do not spend hours and do not end a package.
- Duration 0 does not end, no matter how far the clock moves.
- When the hours run out, drop the front row. If it repeats, the copy on the back starts at the full duration again. Leftover hours in that same jump apply to the next wander. A lone repeating wander (Fargoth, `dur=5`) keeps the same distance and keeps walking. The path restarts.
- One log line when that happens: `package done id=<id> active=<W|T|F|E|A|none>`.
- While a front wander is standing, roll idle2–idle9 once, the way `getRandomIdle` does. `fIdleChanceMultiplier` is **0.75** (the vanilla GMST; this viewer does not parse GMSTs). Play that group once when the kf has it, then walk. Do not chain another gesture when it ends. A missing group is skipped from then on for that package. Someone with distance 0 stays put and waits a few seconds of the plain idle before the next roll. Walking, and the moment they start walking, still plays `walkforward`.
- The time-of-day byte stays on the package and still does nothing.
- A 5×5 rebuild keeps the hours already spent. A fresh cell load starts the duration over.
- `debugCli` checks the hour rule in memory and prints nothing about it: duration 0 does not end; 4 hours of a 5 hour wander leaves 1; a 12 hour jump ends a repeating 5 hour wander twice and leaves 3.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat :core:debugCli --args="actor fargoth"
gradlew.bat lwjgl3:run
```

Class comments in plain English. AGENTS.md: Clear hours end a front wander; standing idle2–idle9; time of day unused. NAME_MAP row for `AiWander` duration and `getRandomIdle`.

## Out of scope

- Walking to a travel point, following, escorting, using the named object ([ai-packages.md](ai-packages.md) **4–7**)
- Greeting, `idle3` as a wait, combat idles
- Branching on the time-of-day byte
- OpenMW’s 30× timescale, or a day/month counter
- Changing wander speed, path choice, or DebugVars

## Pass / fail

- `actor fargoth` still prints `active=W`, `wander=512`, `dur=5`, `hour=0`, and the idle chances. In Town he still wanders. Between walks he may play idle2 (chance 60). His walk cycle stays `walkforward`.
- A Census clerk with distance 0 stays put and may gesture. **Play** for about 5 seconds, or drag the hour 5 hours later: the log shows `package done id=fargoth active=W` (and the same for other `dur=5` wanderers). He is still wandering afterward.
- Dragging the slider earlier does not log `package done`.
- `glError=0`. `debugCli help` still exits 0.

Fargoth standing forever after the log line is a **fail** (the repeat copy should put wander back). A distance-0 clerk walking off because the clock moved is a **fail**. `walkforward` replaced by idle2 while they are mid-stride is a **fail**. A package ending because the time-of-day byte changed is a **fail**. Malsa Ules walking is a **fail** (her front package is still travel).

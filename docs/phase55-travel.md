# Phase 55: Travel to a point

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/aitravel.cpp`: `execute` walks to the package xyz, and returns true when the actor is there. A point whose 3D distance is greater than 7168 makes an ESM travel return false, so the package stays and they do not walk. Repeat still goes on the back when a package does finish (`AiSequence`). No other-LLM prompt.

This is [ai-packages.md](ai-packages.md) **4**. The polyline is the one wander already walks (pathgrid, else the F6 carpet, else a straight line). Pulling that into its own type stays [nav-paths.md](nav-paths.md) **5**. Arrival is the existing 8-unit ground check, not OpenMW’s 2 second give-up timer. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander / Chair HUD stay. **A front travel walks to its point when that point is within 7168, then the package ends. A farther point leaves them standing.**

**Manor** loads the right wing. Other HUD buttons stay. DebugVars wander knobs stay.

## Why this slice

A travel in front used to stand forever, including when the point was in the same room. The next package (often a short wander) never got a turn.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`.

- Only the front package, and only when it is travel. Follow, escort, and activate still stand. Wander is unchanged.
- Measure 3D distance from where they stand to the package xyz. Farther than 7168: do not walk, and do not end the package.
- Otherwise walk there with the same step, turn, and `walkforward` as wander. Pathgrid first (the whole cell graph, not the wander radius). If that has no path, Detour on the carpet. If that has no path, a straight line. Feet still sit on Bullet.
- Reaching the point (within 8 on the ground) drops the front row. If it repeats, the copy goes on the back and starts full. One log line: `package done id=<id> active=<W|T|F|E|A|none>`. The tag is whatever is in front after the drop.
- If that new front is another travel and they are already standing on it, they stay. They do not log `package done` every frame.
- A 5×5 rebuild keeps the path they were already walking. A fresh cell load starts from the ESM list.
- `debugCli` checks the 7168 rule in memory and prints nothing about it: exactly 7168 is in range; 7169 is not; Malsa Ules’s first point is in range; Tanusea Veloth’s arena point is not.

Seyda Neen, Census, Cave, Guild, Nix, and Zain have no actor whose front package is travel. Town looks the same. **Manor** loads Ald-ruhn, Venim Manor Right Wing at the inbound door. Malsa Ules stands about 812 away from `1375, 470, -340`, then a wander of distance 256 for 1 Clear hour, then the next travel. Tanusea Veloth in Vivec, Arena Pit is about 88027 away and stays put.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat :core:debugCli --args="actor fargoth"
gradlew.bat :core:debugCli --args="actor malsa ules"
gradlew.bat :core:debugCli --args="actor tanusea veloth"
gradlew.bat lwjgl3:run
```

Class comments in plain English. AGENTS.md: a front travel within 7168 walks, then the package ends; farther than that they stay. NAME_MAP row for `AiTravel`.

## Out of scope

- Follow, escort, activate ([ai-packages.md](ai-packages.md) **5–7**)
- Opening doors on the way, greeting, combat
- OpenMW’s 2 second “close enough” timer
- A separate `PathFinder` type ([nav-paths.md](nav-paths.md) **5**)
- Changing wander speed, path choice, or DebugVars

## Pass / fail

- `actor fargoth` still prints `active=W` and `wander=512`. In Town he still wanders. **E** on him still logs that list.
- `actor malsa ules` prints `active=T`, then `T x=1375.0 y=470.0 z=-340.0 rep=1`, then a wander. `actor tanusea veloth` prints `active=T` and one travel whose point is far from the arena.
- `debugCli help` still exits 0 (the range check passed).
- Town wanderers look as they do today. A travel actor does not walk off toward another city.
- `glError=0`.

Fargoth standing is a **fail**. Malsa walking her wander radius before she has reached the travel point is a **fail**. Tanusea setting off across the map is a **fail**. A travel ending because the clock moved, while she is still on the way, is a **fail**.

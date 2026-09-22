# Phase 56: Follow another actor

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/aifollow.cpp`: the package walks toward the target and stands once close. Duration hours end it. This slice uses a fixed 256 stand-off (one body plus a step; OpenMW stacks half-extents and lands near that for a single follower). A missing target ends the package. A `CNDT` cell name pauses it outside that cell. The three floats are stored and do not end the follow. No other-LLM prompt.

This is [ai-packages.md](ai-packages.md) **5**. The walk is the same polyline as travel. No run clip. **Working.**

## Goal

Same viewer. Town / Cave / Zain / Manor / F5 / F6 / F7 / Detour wander / Chair HUD stay. **A front follow walks toward the named actor when farther than 256, and stands when closer.**

**Club** loads Balmora, Council Club. Other HUD buttons stay. DebugVars wander knobs stay.

## Why this slice

A follow in front used to stand forever, even when the person they follow is in the same room.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`.

- Only the front package, and only when it is follow. Escort and activate still stand. Wander and travel stay as they are.
- Find the target id among actors in the loaded cell, ignoring case. Not this actor. The closest match wins. No match: drop the front row and log `package done`. If the new front is that same follow and the target is still missing, stay put. Do not log every frame.
- Farther than 256 on the ground: walk there with the travel polyline (pathgrid, else the carpet, else a straight line) and `walkforward`. The path updates when the target has moved. Within 256: stand, and turn to face them. They do not play idle2–idle9 for this package.
- Duration hours are Clear hours, same as a wander. 0 does not end. A `CNDT` cell name: while the loaded cell is not that name, stand and do not spend hours. An empty name does not pause.
- The xyz on the package is not a stop. Repeat still goes on the back.
- A 5×5 rebuild keeps the path. A fresh cell load starts from the ESM list.
- `debugCli` checks the pause and the hour rule in memory and prints nothing about it: an empty cell name does not pause; the same name does not pause; a different name does; duration 0 does not end; 24 hours ends a 24 hour follow.

Seyda Neen has no front follow. **Club** does. Vadusa Sathryon stands (`dist=0`). Marasa Aren starts about 159 away, so she stays. Sovor Trandel starts about 301 away, so he walks to her and stops. Both follow her with duration 0.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat :core:debugCli --args="actor fargoth"
gradlew.bat :core:debugCli --args="actor sovor trandel"
gradlew.bat :core:debugCli --args="actor marasa aren"
gradlew.bat lwjgl3:run
```

Class comments in plain English. AGENTS.md: a front follow walks when farther than 256 and stands when closer. NAME_MAP row for `AiFollow`.

## Out of scope

- Escort and activate ([ai-packages.md](ai-packages.md) **6–7**)
- Ending the follow because they reached the package xyz
- Line of sight before the follow starts, or a run clip past 450
- Following the camera when the id is `player`
- Greeting, combat, opening doors

## Pass / fail

- `actor fargoth` still prints `active=W`. In Town he still wanders.
- `actor sovor trandel` and `actor marasa aren` print `active=F` and `id=vadusa sathryon`. `actor vadusa sathryon` prints `active=W` and `dist=0`.
- **Club**: Sovor walks toward Vadusa and stops near her. Marasa stays where she starts. Vadusa stays put. `walkforward` plays while Sovor is moving.
- `debugCli help` still exits 0. `glError=0`.

Fargoth standing is a **fail**. Sovor wandering off on his later package before he has reached Vadusa is a **fail**. Marasa walking a wander radius is a **fail**. A follow ending because the clock moved, when its duration is 0, is a **fail**.

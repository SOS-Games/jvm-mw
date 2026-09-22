# Phase 57: Escort

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/aiescort.cpp`: while the follower is close the leader walks to the point, and when the follower lags the leader stops and plays `idle3`. The first lag is past 450. After that they wait until the follower is within 250, then walk again and the 450 limit returns. A destination of the max float is no place, and the package ends once the follower is close. No other-LLM prompt.

This is [ai-packages.md](ai-packages.md) **6**. The walk is the same polyline as travel. **R** is the way to see it. **Working.**

## Goal

Same viewer. Town / Cave / Zain / Manor / Club / F5 / F6 / F7 stay. **A front escort leads the follower to the point while they stay close, and waits when they fall behind.**

**R** uses the same crosshair pick as **E** (192 units). That NPC gets an escort of `player` in front. The point is 2048 units from them along the way you are looking. Duration 0, so the clock does not end it. Their old list stays behind it.

## Why this slice

Escort in front used to stand. There is no HUD cell whose front package is escort, so the key is how you try it.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`.

- Only the front package, and only when it is escort. Activate still stands. Wander, travel, and follow stay as they are.
- The follower is the placed actor with that id. The id `player` is your feet (the camera, one body-height down). Another id that is not loaded: they wait. They do not end the package for that.
- Within range: walk to xyz with the travel polyline and `walkforward`. Reaching the point drops the front row and logs `package done`. A max float on x or y is no destination: once you are in range the package ends. A repeating copy that is already finished does not log every frame.
- Farther than 450: stop and play `idle3` when that group exists, otherwise the plain idle. They start walking again once you are within 250.
- Duration hours are Clear hours. 0 does not end. A `CNDT` cell name: outside that cell they stand and do not spend hours.
- `debugCli` checks the nowhere rule in memory and prints nothing about it: the max float is no dest; a normal point is a dest.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat lwjgl3:run
```

In Town, look at Fargoth and press **R**. He walks the way you were looking. Stay near him and he keeps going. Stop and let him get ahead, and he waits. Catch up and he sets off again. When he reaches the point the log shows `package done` and he wanders again.

Class comments in plain English. AGENTS.md: escort leads while you are close and waits when you lag; **R** starts one. NAME_MAP row for `AiEscort`.

## Out of scope

- Activate ([ai-packages.md](ai-packages.md) **7**)
- Opening doors, greeting, combat
- A run clip

## Pass / fail

- `actor fargoth` still prints `active=W`. **E** on him still logs packages. **R** on him logs `escort id=fargoth dest=...` and he walks that way while you follow.
- If you lag, he stops. He does not keep walking off without you. `walkforward` plays while he is moving. `idle3` (or the plain idle) plays while he waits.
- `debugCli help` still exits 0. `glError=0`.

Fargoth ignoring **R** is a **fail**. Him walking away while you stand still past 450 is a **fail**. **E** on a door no longer working is a **fail**.

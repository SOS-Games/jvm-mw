# Phase 52: Parse AI packages and print them

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/aipackage.cpp` (`AIPackageList::add`: file order, `CNDT` names the previous package’s cell) and the composite layouts in that file (`AIWander`, `AITravel`, `AITarget`, `AIActivate`). No other-LLM prompt: those layouts are the pin’s `decompose` functions. This slice only stores and prints them.

This is [ai-packages.md](ai-packages.md) **1**. Do not run a package. Wander stays the first `AI_W` distance. **Working.**

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / F7 / Detour wander / Chair HUD stay. **Each NPC and creature keeps their full package list. E on one logs it. `actor` prints the same lines.** They still wander exactly as they do now.

HUD buttons unchanged. DebugVars wander knobs stay.

## Why this slice

Wander reads one distance and skips the rest of the list. Before anyone can travel or follow, we have to store every package and prove the bytes on a real actor. Looking at them in Town means **E**, not only a CLI.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. Do not commit Bethesda assets or `navmesh.db`.

- On `NPC_` and `CREA`, keep every package subrecord in file order. `AIDT` stays skipped.
  - `AI_W` — int16 distance, int16 duration (hours), uint8 time of day, 8 uint8 idle chances (`idle2`–`idle9`), uint8 repeat.
  - `AI_T` — float x, y, z, uint8 repeat, 3 bytes pad.
  - `AI_F` and `AI_E` — float x, y, z, int16 duration, 32-byte id, uint8 repeat, 1 byte pad. Id is the bytes up to the first NUL.
  - `AI_A` — 32-byte id, uint8 repeat. Same NUL trim.
  - `CNDT` — string. It is the cell name on the previous package. No previous package: log and skip. Do not fail the NPC or creature.
- A short subrecord: skip that package, log, keep the rest of the record. Do not throw the ESM load.
- `wanderDistance` is still the **first** `AI_W` distance, clamped so a negative is 0. Later wander rows stay on the list only. No `AI_W` still means distance 0.
- Do not walk, idle, travel, follow, escort, or use an object because of this list. Pathgrid / Detour / `walkforward` stay.
- **E** (same 192 range as doors): nearest placed NPC or creature. They win only when closer than the door, chest, and takeable. Tie keeps today’s order (door, then chest, then item). Log the package lines and do nothing else. No dialogue, no pose change.
- Debug CLI:
  - `actor <id>` — NPC_ or CREA, id or display name, case-insensitive. NPC wins if both match. Print the existing `npc` or `crea` description, then the package lines.
  - `npc` and `crea` append the same package lines after the description they already print.
- One header, then one line per package, same text in the log and the CLI:

```
packages n=2 id=<id> name=<name>
W dist=<d> dur=<hours> hour=<byte> idle=<8 ints comma-separated> rep=<0|1>
T x=<x> y=<y> z=<z> rep=<0|1>
F id=<target> x=<x> y=<y> z=<z> dur=<hours> cell=<name> rep=<0|1>
E id=<target> x=<x> y=<y> z=<z> dur=<hours> cell=<name> rep=<0|1>
A id=<object> rep=<0|1>
```

`cell=` is omitted when the name is empty. `n=0` and no further lines when the list is empty. Coords print as the stored floats, not snapped.

Class comment on the package type (plain English, per AGENTS). Update AGENTS.md: **E** on an actor logs packages; `actor` is the CLI. NAME_MAP row for `ESM::AIPackageList` → the stored list.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="help"
gradlew.bat :core:debugCli --args="actor fargoth"
gradlew.bat :core:debugCli --args="actor nix-hound"
gradlew.bat :core:debugCli --args="npc sellus gravius"
gradlew.bat lwjgl3:run
```

## Out of scope

- Which package is active, duration ending, idle2–9 playback ([ai-packages.md](ai-packages.md) **2–3**)
- Travel, follow, escort, using the named object (**4–7**)
- `AIDT` hello / fight / flee / alarm, dialogue
- Script commands that push a package
- Changing wander speed, path choice, or DebugVars

## Pass / fail

- `actor fargoth` prints `wander=` and a `W` line whose `dist` is that same number. `actor nix-hound` prints a package header. `npc sellus gravius` still prints race/head/hair and now a package header.
- Town: **E** on Fargoth logs those same `W` lines. He still wanders the pathgrid. **E** on a closer door still swings or teleports. Census clerks still stand or wander as they do today.
- `glError=0`. `debugCli help` mentions `actor`.

A travel or follow NPC who sets off for the package point is a **fail** (wrong slice). **E** opening dialogue or moving the actor is a **fail**. `wander=` changing for a distance-0 clerk is a **fail**. A door you are touching dumping packages instead of opening is a **fail**. An empty list printing `packages n=0` is **not** a fail. Time-of-day doing nothing is **not** a fail.

# AGENTS

Instructions for AI coding agents working in **jvm-mw**.

## Project

Unofficial GPLv3 Java port of OpenMW **0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`). Path B: libGDX window/input/Scene2D only — never `ModelBatch`. Package `io.github.jvmmw`.

- **Do not** touch `D:\morrowind_mods`.
- **Do not** port OpenMW tools.
- **Do not** commit Bethesda assets (`testdata/`, ESM/BSA).
- **Do not** commit unless the user asks.

Data path: gitignored `local.properties` `jvmmw.data=...` (or `JVMMW_DATA` / `-Djvmmw.data`).

```bat
gradlew.bat compileJava
gradlew.bat lwjgl3:run
```

## Debug CLI (headless)

Run from the repo root. Uses the same ESM parser as the viewer; no OpenGL.

```bat
gradlew.bat :core:debugCli --args="help"
gradlew.bat :core:debugCli --args="nif meshes/d/door_cavern_doors00.nif"
gradlew.bat :core:debugCli --args="cell Addamasartus"
gradlew.bat :core:debugCli --args="interiors cave"
gradlew.bat :core:debugCli --args="spawn Addamasartus"
gradlew.bat :core:debugCli --args="npc sellus gravius"
```

| Command | Use when |
| --- | --- |
| `nif` | Door/chunk looks offset — dump node tree and local transforms |
| `cell` | Fog, inbound spawn, door DODT/DNAM for one interior |
| `interiors` | Pick a cell large enough to see fog (`fogStart = 7168 * (1 - density)`) |
| `spawn` | Confirm the exterior-door arrival point |
| `npc` | Race/head/hair/skeleton/equipped parts for one `NPC_` |

In the viewer, **F3** logs camera TES3 position + fog and writes `build/debug-snapshot.txt`.

## Placement / fog gotchas

- Cell root is **one** −90° X (Z-up → Y-up). Instance NIFs `build(false)`.
- Root `NiNode` (record 0, not `bip01`) is identity, matching OpenMW `NiNode::read`.
- `NiTransform.toMatrix` copies the NIF 3x3 into libGDX (`GL(row,col) = mValues[row][col] * scale`). OpenMW’s `toMatrix` transpose is OSG-only; do not apply it twice. Kit pieces like `in_moldcave_doorway00` have a local +90° X; transposing that opens wall seams.
- `NiSkinData` transforms use packed order (rotation, translation, scale), not `NiAVObject` (translation, rotation, scale). Wrong order flattens skinned parts onto the ground.
- Interior spawn is the **inbound door DODT** (where the player arrives), not the cell AABB center.
- Census office is too small for fog at density 0.75 (`fogStart` ≈ 1792). Use a long interior (Addamasartus density 1.0, **Cave** button).
- Walk-in HUD: **Cell** = Census office, **Cave** = Addamasartus.
- NPCs are mannequins on `base_anim` / `_female` / `kna` (yaw-only, race scale). Not `NPC_.MODL`.

Core: `core/src/main/java/io/github/jvmmw/`. NAME_MAP: [docs/NAME_MAP.md](docs/NAME_MAP.md).

# AGENTS

Instructions for AI coding agents working in **jvm-mw**.

## Project

Unofficial GPLv3 Java port of OpenMW **0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`). Path B: libGDX window/input/Scene2D only — never `ModelBatch`. Package `io.github.jvmmw`.

- OpenMW source is local: `D:\coding\morrowind\openmw` at pin `f4bec41444214a7903bebd178389ca22ca13f646`. **Read that tree.** Do not fetch OpenMW from GitHub.
- You **may read** `D:\morrowind_mods` (modlist, meshes, `.kf`). **Do not** edit, move, or write anything there.
- **Do not** port OpenMW tools.
- **Do not** commit Bethesda assets (`testdata/`, ESM/BSA).
- **Do not** commit mid-implementation.
- Once the user says a phase is **working**, **commit and push that phase before writing the next spec.** Do not start the next spec while the previous working phase is uncommitted. After a working phase, `proceed` means commit first, then spec the next slice.
- Other-LLM claim prompts live in the phase spec (`docs/phaseN-*.md`). Point at that file; **do not paste the prompts into chat.**

Data path: gitignored `local.properties` `jvmmw.data=...` (or `JVMMW_DATA` / `-Djvmmw.data`). Extra data folders: `jvmmw.data.extra=...` (`;` separated; or `JVMMW_DATA_EXTRA` / `-Djvmmw.data.extra`).

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
gradlew.bat :core:debugCli --args="crea nix-hound"
gradlew.bat :core:debugCli --args="kf meshes/xbase_anim.kf"
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

| Command | Use when |
| --- | --- |
| `nif` | Door/chunk looks offset — dump node tree and local transforms |
| `cell` | Fog, inbound spawn, door DODT/DNAM for one interior |
| `interiors` | Pick a cell large enough to see fog (`fogStart = 7168 * (1 - density)`) |
| `spawn` | Confirm the exterior-door arrival point |
| `npc` | Race/head/hair/skeleton/equipped parts for one `NPC_` |
| `crea` | Model, x-path, flags, scale for one `CREA` |
| `kf` | Idle groups / bone tracks from a `.kf` |
| `exterior` | 3×3 around a grid: nine `grid=` lines, then center spawn/doors |

In the viewer, **F3** logs camera TES3 position + fog and writes `build/debug-snapshot.txt`.

## Placement / fog gotchas

- Cell root is **one** −90° X (Z-up → Y-up). Instance NIFs `build(false)`.
- Root `NiNode` (record 0, not `bip01`) is identity, matching OpenMW `NiNode::read`.
- `NiTransform.toMatrix` copies the NIF 3x3 into libGDX (`GL(row,col) = mValues[row][col] * scale`). OpenMW’s `toMatrix` transpose is OSG-only; do not apply it twice. Kit pieces like `in_moldcave_doorway00` have a local +90° X; transposing that opens wall seams.
- `NiSkinData` transforms use packed order (rotation, translation, scale), not `NiAVObject` (translation, rotation, scale). Wrong order flattens skinned parts onto the ground.
- Interior spawn is the **inbound door DODT** (where the player arrives), not the cell AABB center.
- Census office is too small for fog at density 0.75 (`fogStart` ≈ 1792). Use a long interior (Addamasartus density 1.0, **Cave** button).
- Walk-in HUD: **Cell** = Census office, **Cave** = Addamasartus, **Nix** = Punsabanit, **Guild** = Wolverine Hall Mage's Guild (door into the hall), **Town** = Seyda Neen exterior `(-2, -9)` (3×3 blended land; Census door `DODT`). Falling off **outside** the 3×3 is expected.
- **E** opens/closes a non-teleport door, loads a named interior dest, loads an empty-`DNAM` dest as a 3×3 around that exterior grid, plays a chest `containeropen` / `containerclose` if those kf groups exist, or takes a world item (mesh unparents; no inventory). Books log only (`ActionRead` GUI skipped). Fixture lights without Carry stay.
- NPCs are mannequins on `base_anim` / `_female` / `kna` (yaw-only, race scale). Not `NPC_.MODL`. ESM placement is a parent of `Bip01`; idle `.kf` overwrites bone locals then re-skins.
- Creatures use `CREA.MODL` (x-prefix if the kf exists), not body parts. Skip drawables named `tri bip`. Scale is ref `XSCL` times `CREA.XSCL`.

Core: `core/src/main/java/io/github/jvmmw/`. NAME_MAP: [docs/NAME_MAP.md](docs/NAME_MAP.md).

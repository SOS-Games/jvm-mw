# AGENTS

Instructions for AI coding agents working in **jvm-mw**.

## Project

Unofficial GPLv3 Java port of OpenMW **0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`). libGDX window/input/Scene2D only — never `ModelBatch`. Package `io.github.jvmmw`.

- OpenMW source is local: `D:\coding\morrowind\openmw` at pin `f4bec41444214a7903bebd178389ca22ca13f646`. **Read that tree.** Do not fetch OpenMW from GitHub.
- You **may read** `D:\morrowind_mods` (modlist, meshes, `.kf`). **Do not** edit, move, or write anything there.
- **Do not** port OpenMW tools.
- **Do not** commit Bethesda assets (`testdata/`, ESM/BSA).
- **Do not** commit mid-implementation.
- Once the user says a phase is **working**, **commit and push that phase before writing the next spec.** Do not start the next spec while the previous working phase is uncommitted. After a working phase, `proceed` means commit first, then spec the next slice.
- Other-LLM claim prompts live in the phase spec (`docs/phaseN-*.md`). Point at that file; **do not paste the prompts into chat.** They are for uncertainty, not ceremony: if confidence in OpenMW’s behavior is already high, skip them. If only one corner is shaky, include **that** prompt. Do not invent three claims when zero or one would do. Any prompts that *are* in the spec must still **hold** before Java.
- Possible next **small** slices live in [docs/next-topics.md](docs/next-topics.md). Spec one only after the user picks it (or says **proceed** naming it). Large holes live in [docs/big-topics.md](docs/big-topics.md). Do not spec those as a small phase.

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
            gradlew.bat :core:debugCli --args="levc ex_bittercoast_lev+0"
            gradlew.bat :core:debugCli --args="kf meshes/xbase_anim.kf"
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

| Command | Use when |
| --- | --- |
| `nif` | Door/chunk looks offset — dump node tree and local transforms |
| `cell` | Fog, inbound spawn, door DODT/DNAM. Kit lines include `gl=` world AABB. Then `seam meet` / `seam gap` / `seam island` — whether cave hulls actually touch (not just ESM placement) |
| `interiors` | Pick a cell large enough to see fog (`fogStart = 7168 * (1 - density)`) |
| `spawn` | Confirm the exterior-door arrival point |
| `npc` | Race/head/hair/skeleton/equipped parts, `wander=` |
| `crea` | Model, x-path, flags, scale, `wander=` |
| `levc` | Creature leveled list: chance-none, flags, `level id` rows |
| `kf` | Idle groups / bone tracks from a `.kf` |
| `exterior` | 5×5 minus corners around a grid: 21 `grid=` lines, then center spawn/doors / `water=-1`. `crea=` hardcoded, `levc=` / `levcNone=` a dry roll at level 1 |

In the viewer, **F3** or HUD **Dump** copies the live snapshot to the clipboard and writes `build/debug-snapshot.txt`. Wait until overlay `n=60` before treating fps as settled. **F4** toggles the top-right fps overlay. Gradle Ctrl+C does not save a dump.

## Placement / fog gotchas

- Cell root is **one** −90° X (Z-up → Y-up). Instance NIFs `build(false)`.
- Root `NiNode` (record 0, not `bip01`) is identity, matching OpenMW `NiNode::read`.
- `NiTransform.toMatrix` copies the NIF 3x3 into libGDX (`GL(row,col) = mValues[row][col] * scale`). OpenMW’s `toMatrix` transpose is OSG-only; do not apply it twice. Kit pieces like `in_moldcave_doorway00` have a local +90° X; transposing that opens wall seams. Addamasartus and Zainsipilu both fail if this 3x3 is swapped.
- ESM eulers are yaw then pitch then roll around `(0,0,-1)` / `(0,-1,0)` / `(-1,0,0)`. libGDX quat mul is Hamilton, not OSG: `QX*QY*QZ`. Copying OSG’s `QZ*QY*QX` token order is a 180° yaw on pieces that combine 180° X with a heading (Zainsipilu floor/ceiling halls). Yaw-only caves (Addamasartus) look the same either way.
- `NiSkinData` transforms use packed order (rotation, translation, scale), not `NiAVObject` (translation, rotation, scale). Wrong order flattens skinned parts onto the ground.
- Interior spawn is the **inbound door DODT** (where the player arrives), not the cell AABB center.
- Census office is too small for fog at density 0.75 (`fogStart` ≈ 1792). Use a long interior (Addamasartus density 1.0, **Cave** button).
- Walk-in HUD: **Cell** = Census office, **Cave** = Addamasartus, **Nix** = Punsabanit, **Guild** = Wolverine Hall Mage's Guild (door into the hall), **Town** = Seyda Neen exterior `(-2, -9)` (5×5-minus-corners land with linear 17×17 mix + shader water at −1 with refraction and underwater fog + Clear-day atmosphere, clouds, and midday sun; Census door `DODT`), **Zain** = Zainsipilu. WASD walks on land/docks; ceilings and dock undersides stop the camera. Walking recenters that grid on the camera cell in the background (no freeze). A small 5×5-minus-corners bar grid (bottom-right) fills per tile while a walk load is in flight, then reads `swap` for a beat. Top-right fps overlay (F4) shows frame ms, draws, culled, tex, nif, and the fattest section. Meshes outside the camera frustum are not submitted. Meshes smaller than 2 pixels are skipped on the main view. Objects farther than 7168 are skipped unless they are large (trees, shacks). Land and water stay. DDS and static NIF GPU templates intern by VFS path. Water RTTs skip NPC/creature reflections and meshes smaller than 20 pixels on the 512 map. HUD **slider / [ ] / Play** scrubs the Clear hour (stars at night).
- **E** opens/closes a non-teleport door, loads a named interior dest, loads an empty-`DNAM` dest as a 5×5-minus-corners around that exterior grid, plays a chest `containeropen` / `containerclose` if those kf groups exist, or takes a world item (mesh unparents; no inventory). Books log only (`ActionRead` GUI skipped). Fixture lights without Carry stay.
- NPCs are mannequins on `base_anim` / `_female` / `kna` (yaw-only, race scale). Not `NPC_.MODL`. ESM placement is a parent of `Bip01`; idle `.kf` overwrites bone locals then re-skins. First `AI_W` distance > 0 slides them around spawn (80 units/s); while they move the same kf plays `walkforward`, then a short blend back to `idle`. Distance 0 stays. Walk clips translate `Bip01` / `root bone` in XY — zero those so the loop does not yank them back. Test knobs live in `debug.DebugVars`: `wanderSpeed` (default 2), `wanderTurn` (90°/s; OpenMW is 900), `wanderRadius` (0.3 of AI_W), `wanderRadiusMax` (256), `wanderFrequency` (3).
- Creatures use `CREA.MODL` (x-prefix if the kf exists), not body parts. Skip drawables named `tri bip`. Scale is ref `XSCL` times `CREA.XSCL`. Wilderness spawn markers are `LEVC`: roll at player level 1 (`AllLevels` is bit 0, not the item-list bit). Empty rolls are chance-none or entries above level 1. Walk-grid rebuilds keep the last pick for each marker. LEVC uses the picked `CREA` wander.

## Class comments

Large classes get a short block immediately above `public class` — not per-line, not on tiny NIF property stubs. Write for a person learning the viewer: what this type does, where it sits, and the gotchas that bite. Plain English. Names the player sees are fine (`.nif`, `E`, Town). Do **not** write “Maps to OpenMW::Foo” or “Rewrite of Bar” — that lives in [docs/NAME_MAP.md](docs/NAME_MAP.md). Skip `{@link}` / `{@code}` unless a type name would be ambiguous.

Core: `core/src/main/java/io/github/jvmmw/`. NAME_MAP: [docs/NAME_MAP.md](docs/NAME_MAP.md).

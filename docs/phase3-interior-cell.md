# Phase 3: one interior cell (STAT only)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/loadcell.cpp`, `cellref.cpp`, `loadstat.cpp`, `components/misc/convert.hpp` (`makeOsgQuat`), `components/misc/resourcehelpers.cpp` (`correctMeshPath`). Other-LLM checks of those three claims **held** (2026-09-18).

## Goal

Same Path B viewer. Load **one named interior** from vanilla `Morrowind.esm` and draw its **STAT** references with Phase 2 materials. The room should look like OpenMW’s statics in that cell, not a single mesh on an empty backdrop.

Phase 1–2 HUD meshes still load from their buttons.

## Why not the full world

OpenMW’s `MWWorld::Store` / plugin override / moved-ref tracker is a later phase. One ESM, one interior, STAT only is enough to prove: TES3 record stream, cell-ref placement, mesh path, and that our NIF Z-up → GL Y-up is applied **once**.

## In scope

TES3 `Morrowind.esm` only (no `.esp`, no Tribunal/Bloodmoon, no ESM4).

- Record header: 16 bytes (`NAME` 4, size 4, unused 4, flags 4). Subrecord: 8 bytes (`NAME` 4, size 4) then payload.
- Index **STAT** (`NAME` id + `MODL` path) while scanning.
- Find one interior **CELL** by name (`DATA` flags bit0 = interior). Skip `LAND` / `PGRD`.
- Refs are **inside that CELL record** after the header (`NAME` / `DATA` / `AMBI` / `NAM0` / …). Seek back like OpenMW’s saved context; do not look for a child `GRUP`.
- Each ref: `FRMR` (4-byte index, `wideRefNum == false`) + `NAME` (object id) + optional subs + `DATA` (6 floats: pos xyz, rot xyz **radians**). Skip `NAM0` among refs (temp-ref marker). Skip `DELE`.
- Place only refs whose `NAME` is a **STAT** we indexed. Log and skip DOOR / NPC_ / CREA / LIGH / CONT / ACTI / MISC / … (even if they have a model).
- Mesh path: `MODL` like `f\Furn_De_Chair_01.NIF` → `TexturePaths.normalizeMeshPath` (`meshes/` + backslash→slash). Extract from BSA into `testdata/` like Phase 1.
- Scale: `XSCL` if present, else 1.
- Rotation (statics, **first load**, not inverse-order, not actor):

  ```
  Quat(rot[2], (0,0,-1)) * Quat(rot[1], (0,-1,0)) * Quat(rot[0], (-1,0,0))
  ```

  Same as `Misc::Convert::makeOsgQuat`. Then translation by `pos`.
- **One** Morrowind Z-up → GL Y-up on the **cell root** (`-90°` around X). Instance NIFs with **no second** `-90°` (today `NifSceneBuilder` puts that on every file root — gate it).
- Reuse Phase 2 flatten / two-pass draw. Collision `RootCollisionNode` still hidden.
- HUD: a **Cell** button next to the mesh buttons. Status: cell name, STAT placed, STAT skipped (unknown id / missing nif), `glError`.
- NAME_MAP rows below when the types exist.

## Coordinate trap

OpenMW/OSG is Z-up. This viewer is Y-up. If the cell parent **and** each NIF both apply `-90°` around X, every object is wrong.

| Node | Transform |
| --- | --- |
| Cell root | `-90°` around X (Z-up → Y-up) |
| Each STAT instance | ESM `makeOsgQuat` + `pos` + uniform scale |
| NIF contents | as authored (Z-up), **no** extra axis convert |

## Out of scope

Plugin override, Tribunal/Bloodmoon, moved refs, actors, doors, containers, lights, activators, water, pathgrid, terrain, fog from `AMBI` (log AMBI, still use Phase 2’s directional light), Lua, Bullet, MyGUI, skinning, `.kf`.

Do not invent a VFS merge. `Morrowind.esm` + `Morrowind.bsa` only.

## Test cell

All from the user’s Data Files. Not committed.

| Cell `NAME` | Why |
| --- | --- |
| `Seyda Neen, Census and Excise Office` | Small interior, many STATs, famous layout |
| Fallback: `Imperial Prison Ship` | Tiny if the office is too heavy or the name mismatches |

If the exact string differs in `Morrowind.esm`, dump interior `CELL` names that contain `Census` / `Prison Ship` and pick the match. Do not hard-fail the whole app on a missing cell — show the error on the HUD.

## Pass / fail

- Office (or ship) is recognizably that room: desks, walls, furniture sit on the floor, not 90° on their side or exploded.
- Object count in the log matches STAT refs in that cell (skipped types listed, not drawn).
- Chair / shack / tree / banner / dwrv HUD buttons still work (those paths still add `-90°` when **not** parented to a cell).
- `glGetError() == 0`. Collision still hidden. Scene2D still clickable.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::ESMReader` | `esm.EsmReader` | same |
| `ESM::Cell` | `esm.Cell` | same |
| `ESM::CellRef` | `esm.CellRef` | same |
| `ESM::Static` | `esm.Static` | same |
| `Misc::Convert::makeOsgQuat` | cell instance rotation | rewrite |
| `Misc::ResourceHelpers::correctMeshPath` | `TexturePaths.normalizeMeshPath` (already exists; **use** it) | same |
| `MWWorld::Scene` insert | `JvmMwApp` / a small `CellSceneBuilder` | rewrite |

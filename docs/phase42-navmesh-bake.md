# Phase 42: Bake Recast navmesh (debug draw)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `files/settings-default.cfg` `[Navigator]` and `[Game] default actor pathfind half extents`, `components/detournavigator/settingsutils.hpp` (`toNavMeshCoordinates` / tile size), `components/detournavigator/recastparams.hpp` (`getAgentHeight` / `getAgentRadius`), `components/detournavigator/makenavmesh.cpp` (`makeRecastParams` / rasterize / filters / polymesh), `components/sceneutil/navmesh.cpp` (debug polys). Recast numbers and the default agent are in the pin. No other-LLM prompts.

This is the **first split** of [nav-paths.md](nav-paths.md) **6**. Bake and show walkable polys. Do not pathfind on them. Do not change wander.

## Goal

Same viewer. Town / Cave / Zain / pathgrid wander stay. **The loaded cells have a Recast navmesh in memory, F6 shows walkable polys on the ground (Town’s 5×5, not only the spawn cell), and Dump prints a poly count.** Actors still follow the F5 pathgrid (or random dests).

HUD buttons unchanged (including **Zain**). Chair HUD unfogged.

## Why this slice

Phase 40 put the authored graph on screen. Detour is OpenMW’s extra walkable surface from collision. Before anyone can `buildPathByNavMesh`, Town has to show a carpet that goes around shacks, not through them.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Recast/Detour via **recast4j** on `core` (Maven) as **fallback**. Prefer OpenMW `navmesh.db` (umo / `openmw-navmeshtool`) from `Documents\My Games\OpenMW\navmesh.db`, or `jvmmw.navmesh`. Do not commit the db. No JNI, no Bullet, no second physics engine. Never `ModelBatch`.
- Load tiles for the **loaded cells**:
  - Interior: all tiles whose worldspace is the lowercased cell name.
  - Exterior: `sys::default` tiles whose Recast tile XY overlap the loaded **5×5-minus-corners** (`LoadedCell.tiles`), not only the center cell.
- OpenMW stores Recast verts as scaled TES `(x, height, y)`. Unscale and keep that TES xyz under the cell root (the −90° X is the same as land). Do **not** apply the OSG YZ swap a second time.
- If the db is missing or that cell has no tiles, bake Recast from land + collision as before (one default AABB agent, OpenMW Recast numbers).
- Overlay: filled walkable polys a little above the surface, a color that is not F5 blue/cyan (green is fine). Parent under the cell root. `debugDraw` like pathgrid. **On at load. F6 hides.** Water cameras skip it. F5 still only toggles pathgrid; do not hide navmesh when pathgrid is off.
- Rebuild on interior load and on walk-grid swap. Sqlite and Recast run on a **worker**. Keep already-loaded Recast tiles when the 5×5 moves; only fetch the new ring. Overlay GPU upload is one Recast tile per frame. The cell must walk without waiting.
- Dump (`n=60`): `nav=P tiles=T src=db` (or `src=bake`). `src=load` while the worker is still going. `nav=0` when none.
- Debug CLI `navdb` reads OpenMW `navmesh.db` and writes `build/navdb-*.png` (cell-edge coverage). Runtime bake still needs the viewer.
- Class comment on the baker and the overlay (plain English, per AGENTS). Update AGENTS.md: F6 navmesh, wander still pathgrid.

```bat
gradlew.bat compileJava
gradlew.bat lwjgl3:run
```

## Out of scope

- `buildPathByNavMesh`, Detour queries, wander / Travel using the mesh ([nav-paths.md](nav-paths.md) leftover of 6)
- Writing our own `navmeshdb` / porting `openmw-navmeshtool`
- OpenMW-style tile cache / wait-until-min-distance as the 5×5 moves
- Water as a swim surface, off-mesh pathgrid links, per-actor agent sizes
- Actor collision, occupied/hidden dest, shared `PathFinder`
- Chair HUD navmesh (no collision world)
- Changing DebugVars wander knobs

## Test cell

**Town** (app default, center `(-2, -9)`): green (or similar) polys sit on dirt, docks, and streets. They stop at shack walls instead of filling the Census office from the road. Fargoth still walks the F5 graph. **F6** hides the carpet. **F5** still hides only spheres/edges.

**Cave** (Addamasartus): polys follow the tunnel floor, not a slab through the rock.

**Cell** (Census office): a floor carpet inside the room. Walls stay holes.

Chair HUD unfogged and still flies.

## Pass / fail

- Town center `nav>0` and `src=db` in Dump when OpenMW `navmesh.db` is present (wait until the carpet is up; `src=load` for a moment is OK). Overlay matches the streets, not piled at the origin. `glError=0`.
- A hitch on Town from sqlite or Recast on the GL thread is a **fail** (`src=bake` on Town is a fail). Missing interior tiles falling back to bake is **not** a fail.
- Cave shows floor polys down the tunnel.
- F6 hides navmesh. F5 still toggles pathgrid only. Wander still matches Phase 41.
- Chair still flies.

`nav=0` on Town is a **fail**. A carpet that goes through shack walls at street level is a **fail**. Overlay in TES space (not under the cell-root −90°) is a **fail**. Actors walking the Recast mesh instead of the pathgrid is a **fail** (wrong slice). Town overlay that drops already-loaded cells when the walk grid moves is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `DetourNavigator` bake (`makenavmesh`) | Recast tiles from land + `CollisionWorld` | rewrite |
| `[Navigator]` Recast defaults | same numbers | same |
| `toNavMeshCoordinates` scale (not the OSG YZ swap) | scale GL verts | rewrite |
| `getPathfindingAgentBounds` exterior default | one AABB agent | rewrite |
| `SceneUtil` navmesh debug draw | overlay + **F6** | rewrite |

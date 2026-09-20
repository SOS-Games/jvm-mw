# Phase 28: Walk-recenter exterior grid

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwworld/scene.cpp` (`playerMoved` / `getNewGridCenter` / `requestChangeCellGrid` / `changeCellGrid`), `components/esm/util.hpp` (`positionToExteriorCellLocation` / `indexToPosition`), `components/misc/constants.hpp` (`CellSizeInUnits` 8192, `CellGridRadius` 1). No other-LLM prompts: those paths are read from the pin.

## Goal

Same Path B viewer. Phase 27 clock stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Walking an exterior recenters the 3×3** on the camera’s cell, the way OpenMW does while you move. Ground, buildings, and water keep up. The eye does not snap back to Census `DODT`. Interiors still load one cell. Moons stay out (delayed-features).

## Why this slice

Phase 18–27 keep a frozen 3×3 around the dest grid. Census `DODT` sits on the south edge of `(-2, -9)`; a few hundred units is fine, then you fall into void. OpenMW already has the rule: when the player leaves the current grid far enough, `changeCellGrid` unloads the far strip and loads the new one. That is the missing walk.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- After each exterior camera move (not interiors, not while the loader is up), TES3 XY from the eye (`x`, `-z`) as today. `getNewGridCenter`:
  - Cell centre = `(gridX * 8192 + 4096, gridY * 8192 + 4096)` (`indexToPosition(..., true)`).
  - Chebyshev distance `max(|cx - x|, |cy - y|)`.
  - Stay on the current center while that distance `<= 8192/2 + 1024` (`mCellLoadingThreshold` **1024**).
  - Else new center is `positionToExteriorCellLocation` = `floor(x/8192)`, `floor(y/8192)` (`LandRecord.cellGrid`).
- If the new center differs from `loadedCell.gridX/Y`, load a 3×3 around it with the existing `loadExterior` path (radius 1). **Keep the eye and look** — do not call `frameCellCamera` / `placeEye` / spawn. Keep `ClearCycle.hour`. Loader overlay stays while refs step. At most one pending recenter; ignore further crosses until that load finishes, then check again.
- Water plane recenters with the new 3×3 (same `WaterMesh.attach` on the dest grid). Sky is camera-relative; leave it.
- Taken world items: keep a set of taken ref ids on the app and skip those on rebuild so a grid shift does not put the bottle back.
- HUD status / F3 use the new center name and `grid=(x,y)`. Same-cell **Town** / empty-`DNAM` still only `placeEye` (Phase 17).
- Interiors unchanged. Chair HUD unfogged.

## Out of scope

- Incremental keep of overlapping GPU tiles (full 3×3 rebuild + loader hitch is OK)
- Background `CellPreloader` / predicted-position preload
- ESM offset index / keep-the-file-open cache
- Open door / chest pose across a rebuild
- Physics, navmesh, actor AI
- ESM4 larger grid
- Moons (delayed-features), weather types, sunglare
- Walk-into-void interior reset (`lowestPoint - 90`)
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town**: Census `DODT`. Walk **south** across the `(-2, -9)` south line into `(-2, -10)`: land, shacks, and water continue; the camera does not jump. Keep walking south or along the coast; the HUD grid updates and you do not fall off a 3×3 cliff. **[ ]** hour still works after a recenter.

**Cell** / **Cave** / **Guild**: no grid streaming.

```bat
gradlew.bat lwjgl3:run
```

F3 writes `grid=` for the current center.

## Pass / fail

- Walking south from Census keeps ground and buildings; HUD `grid=` changes; eye does not teleport to spawn.
- Interiors unchanged. Chair HUD unfogged. `glError=0`.

Still falling into void one cell from Town is a **fail**. Snap-to-spawn on recenter is a **fail**. Moons or weather this phase is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Scene::playerMoved` | exterior walk → maybe recenter | rewrite |
| `Scene::getNewGridCenter` | Chebyshev + 1024 hysteresis | rewrite |
| `Scene::changeCellGrid` | 3×3 around camera cell, keep eye | rewrite |

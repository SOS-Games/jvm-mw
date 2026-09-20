# Phase 29: Background walk-grid load

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwworld/scene.cpp` (`preloadCells` / `preloadExteriorGrid` / `requestChangeCellGrid`), `apps/openmw/mwworld/cellpreloader.cpp` (`CellPreloader::preload` on the work queue). No other-LLM prompts: those paths are read from the pin.

## Goal

Same viewer. Phase 28 walk-recenter stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Crossing an exterior cell keeps you walking.** The old grid stays on screen; look and WASD stay live. The new 5×5-minus-corners is built off the hitch and swapped in when ready. HUD **Town** / **Cell** / door teleports still use the blocking loader. Moons stay out.

## Why this slice

Phase 28 recenters, then freezes: `loadExterior` parses the whole ESM on the GL thread and the overlay sets `isLoading()`. OpenMW’s walk path is `requestChangeCellGrid` plus a **worker** (`CellPreloader` / `WorkQueue`). You do not stare at a dim screen every 8192 units.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Exterior load is a **5×5 with the four corners cut** (`CELL_GRID_RADIUS` 2, skip `|dx|==2 && |dy|==2`): 21 cells of land/refs, water plane still 5×5 so the cut corners stay wet.
- Walk-recenter only (Phase 28 `maybeRecenterGrid` / `keepEye`). Do **not** call `requestLoad`’s blocking overlay for that path. `isLoading()` stays false so mouse look is not unlocked/frozen. WASD keeps moving. Status may show `loading grid=(x,y) parse|land|refs`. A small bottom-right 5×5-minus-corners bar grid fills per tile (yellow parse pulse, orange land, blue refs, green done) so the swap is obvious.
- Keep the current `root` / `cellBuilder` until the new grid `end()`s. Then dispose the old scene and swap. Camera stays (`keepEye`). Hour stays.
- Parse `EsmFile.loadExterior` on a **worker thread**. GPU work (`CellSceneBuilder` begin/step/end, textures, VAOs) stays on the GL thread in the existing step budget. Never `ModelBatch`.
- One in-flight walk load. If you cross again before it finishes, remember the latest target and start that after the swap (Phase 28 pending rule).
- Taken-item set still applies to the new builder. Interiors and HUD mesh/cell buttons unchanged (blocking loader OK).

## Out of scope

- Incremental keep of overlapping GPU tiles (still a full rebuild, just not blocking)
- Predicted outer-ring `preloadExteriorGrid` / `mPreloadDistance`
- ESM record-offset index
- Open door / chest pose across a swap
- Physics, navmesh
- Moons, weather, sunglare
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town**: Census `DODT`. Walk **south** across `(-2, -9)` into `(-2, -10)`: you keep looking and walking; the world does not go black. After a short wait the new land/shacks appear and the HUD `grid=` updates. **Cell** / **Cave** still show the loader.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Walk-recenter does not freeze look or cover the old grid with the dim loader.
- HUD **Cell** still uses the loader. Chair HUD unfogged. `glError=0`.

A walk hitch that locks the mouse or blanks the harbor is a **fail**. Dropping the blocking loader on **Cell** / **Town** is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Scene::requestChangeCellGrid` | walk load without overlay | rewrite |
| `CellPreloader` work queue | worker `loadExterior` + GL step | rewrite |

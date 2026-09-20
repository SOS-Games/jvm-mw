# Phase 18: Exterior 3×3 cell grid

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwworld/scene.cpp` (`changeCellGrid` / `iterateOverCellsAround` / `loadCell`), `components/misc/constants.hpp` (`CellGridRadius`), `apps/openmw/mwworld/worldmodel.cpp` (`getExterior`), `apps/openmw/mwrender/renderingmanager.cpp` (`addCell` / `removeCell`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 17 empty-`DNAM` exits stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged.

**An exterior load places a 3×3 of cells around the dest grid: grey `LAND` plus refs for each.** Census door `DODT` is near the south edge of `(-2, -9)`; walking south must still have ground. Do not recenter the grid as the camera walks (OpenMW would; that is a later slice).

## Why this slice

Phase 16/17 load **one** cell. OpenMW `changeCellGrid` uses `CellGridRadius` **1**, so TES3 active exteriors are a square of `(2 * 1 + 1)²` = **9** cells (`iterateOverCellsAround` from `center ± 1`). The Census exit sits close to `y = -9`’s south border; a few steps fall into `(-2, -10)`. That neighbor’s land and shacks are the hole.

Still skip `LTEX`, water, sky, and streaming a new 3×3 when you cross a cell line.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- When loading an exterior (HUD **Town** or empty-`DNAM` **E**), center = dest grid (`floor(DODT.x/8192)`, `floor(DODT.y/8192)` or Town `(-2, -9)`).
- Load every `(x, y)` with `|x - cx| <= 1` and `|y - cy| <= 1` (nine cells). Same Phase 16 `LAND` decode per grid; missing land → height **-2048**. Place that cell’s refs with `CellSceneBuilder` (one cell-root −90° X; TES3 world XY on land and instances).
- Same dest grid as the loaded 3×3 center: do not rebuild; only `placeEye` (Phase 17 same-cell rule).
- Interiors unchanged (one cell). Fog off on exteriors. Grey land, no `LTEX`.
- Debug CLI `exterior -2 -9`: print the nine grids (`grid= x y name= refs= land=`), then the center cell’s doors as today.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- Recenter / unload when the camera crosses into a neighbor (OpenMW `changeCellGrid` while walking)
- `LTEX` / `VTEX` dirt textures
- Water, sky, weather
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** or **Cell** → nord exit **E**: stand at Census `DODT`, walk **south**. Grey land continues; neighbor buildings (south of the office) stay in view. Walk **north/east/west** a bit; still land inside the 3×3.

**Cell** / **Cave** / **Guild** interiors unchanged. Chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- From the Census door `DODT`, walking south a few hundred units still shows grey ground (not a cliff into void).
- Neighbor cell buildings are placed (not only the center cell’s refs).
- Interior **Cell** / **Cave** / **Guild** unchanged. Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 (far from Town) is **expected**, not a fail. Untextured grey land is not a fail. Only one land tile after Town/exit load is a **fail**. Recenter-while-walking this phase is a **fail**. Dirt textures this phase is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Grid size.** TES3 `changeCellGrid` sets `halfGridSize` to `CellGridRadius` (**1**). `iterateOverCellsAround(cellX, cellY, range)` calls `f(x,y)` for every `x,y` from `cell ± range` inclusive. `CellGridRadius` comment: active grid is a square with side `(2 * CellGridRadius + 1)`.
2. **Which cells load.** `changeCellGrid` `getExterior` + `loadCell` for each `(x,y)` from that loop that is not already active. `WorldModel::getExterior(location)` loads that exterior store.
3. **Per-cell terrain.** `Scene::loadCell` for an exterior: heightfield from `Land` `DATA_VHGT`, or TES3 default height **-2048** if no data; then `insertCell` and `RenderingManager::addCell`. `addCell` if exterior: `enableTerrain` and `Terrain::loadCell(getGridX(), getGridY())`. Interiors skip that pair.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Constants::CellGridRadius` | 3×3 around dest grid | same |
| `Scene::iterateOverCellsAround` | load nine `loadExterior` cells | rewrite |
| `Scene::changeCellGrid` | one-shot 3×3, no walk recenter | rewrite |
| `RenderingManager::addCell` | one grey `LAND` mesh per grid | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: For TES3, Scene::changeCellGrid sets halfGridSize to
Constants::CellGridRadius. CellGridRadius is 1, and the active
grid is a square with side (2 * CellGridRadius + 1).
iterateOverCellsAround(cellX, cellY, range) invokes f(x, y) for
every integer x from cellX-range to cellX+range and every y from
cellY-range to cellY+range (inclusive).

From components/misc/constants.hpp:
    // Size of active cell grid in cells (it is a square with the
    // (2 * CellGridRadius + 1) cells side)
    CellGridRadius = 1;

From apps/openmw/mwworld/scene.cpp changeCellGrid:
    halfGridSize = isEsm4Ext(worldspace)
        ? ESM4CellGridRadius : Constants::CellGridRadius;

From scene.cpp iterateOverCellsAround:
    for x = cellX - range .. cellX + range
        for y = cellY - range .. cellY + range
            f(x, y);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: changeCellGrid collects cells from iterateOverCellsAround
the player cell with mHalfGridSize. For each (x, y) not already
in mActiveCells it getExterior(ExteriorCellLocation(x, y,
worldspace)) and loadCell that store. WorldModel::getExterior
loads the exterior CellStore for that location (forceLoad true
by default).

From apps/openmw/mwworld/scene.cpp changeCellGrid:
    iterateOverCellsAround(playerCellX, playerCellY, mHalfGridSize,
        [&](int x, int y) {
            location = ExteriorCellLocation(x, y, worldspace);
            if already in mActiveCells return;
            refsToLoad += getExterior(location).count();
            cellsPositionsToLoad.push (x, y);
        });
    for each (x, y) in cellsPositionsToLoad:
        if not in mActiveCells:
            cell = getExterior({x, y, worldspace});
            loadCell(cell, ...);

From apps/openmw/mwworld/worldmodel.cpp:
    getExterior(location, forceLoad = true):
        getOrCreateExterior(...);
        if forceLoad && not loaded: cellStore->load();
        return *cellStore;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Scene::loadCell, when the cell is exterior, builds a
heightfield from Land DATA_VHGT if present; if there is no data
and the worldspace is not ESM4, it uses a verts*verts field of
ESM::Land::DEFAULT_HEIGHT (-2048). Then insertCell, then
RenderingManager::addCell. addCell: if the cell is exterior,
enableTerrain(true, worldspace) and
Terrain::loadCell(getGridX(), getGridY()). That pair is skipped
when the cell is not exterior.

From apps/openmw/mwworld/scene.cpp loadCell (exterior):
    land = getLand(cellIndex);
    data = land ? land->getData(DATA_VHGT) : nullptr;
    if (data) addHeightField(data->getHeights(), ...);
    else if (!isEsm4Ext)
        addHeightField(vector of verts*verts DEFAULT_HEIGHT, ...);
    insertCell(...);
    mRendering.addCell(&cell);

From components/esm3/loadland.hpp:
    DEFAULT_HEIGHT = -2048;

From apps/openmw/mwrender/renderingmanager.cpp addCell:
    if (store->getCell()->isExterior()) {
        enableTerrain(true, store->getCell()->getWorldSpace());
        mTerrain->loadCell(store->getCell()->getGridX(),
            store->getCell()->getGridY());
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

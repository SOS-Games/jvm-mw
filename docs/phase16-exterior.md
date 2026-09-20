# Phase 16: One exterior cell (grey land)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/loadcell.hpp` (`isExterior` / `DATAstruct`), `apps/openmw/mwworld/cellref.cpp` (`getDestCell`), `components/esm/util.hpp` (`positionToExteriorCellLocation`), `components/misc/constants.hpp` (`CellSizeInUnits` / `CellGridRadius`), `components/esm3/loadland.hpp` / `loadland.cpp` / `landrecorddata.hpp` (`LAND` / `VHGT`), `apps/openmw/mwrender/renderingmanager.cpp` (`addCell`), `apps/openmw/mwworld/scene.cpp` (`changeToExteriorCell` / `changeCellGrid`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 15 take stays. HUD **Cell** / **Cave** / **Nix** / **Guild** unchanged.

**HUD Town loads one exterior grid cell: grey `LAND` heightfield plus that cell’s placed objects.** Camera at the Census office door’s world `DODT`. Empty-`DNAM` exits stay shut.

## Why this slice

Interiors work. Census / cave **exits** still no-op because empty `DNAM` is an exterior (`getDestCell` → `positionToExteriorCellLocation` from `DODT` xy). The Census door `DODT` is about `(-11056, -72286, 240)`, which is grid **(-2, -9)** (`floor(x/8192)`, `floor(y/8192)`). That cell is near its south edge.

Smallest visible outdoors: **that one cell**. Objects without land float in a void. 3×3, dirt textures, water, sky, and walking the exit are later holes. OpenMW would load a `CellGridRadius` (1) 3×3; this port still loads **one** cell.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Load exterior `CELL` by **grid**, not by name (several cells are named Seyda Neen). Keep `DATA` `mX`/`mY` (this port currently discards those two `i32`s). `isExterior` is `!(flags & Interior)` (`Interior = 0x01`).
- Parse the matching `LAND` (`INTV` grid). `VHGT`: float offset plus signed-byte deltas, 65×65, scale **8**. Missing land → height **-2048**. Skip `VTEX` / `LTEX` this slice. `VCLR` optional (grey is enough).
- One `MeshInstance` (never `ModelBatch`). TES3 XY on the heightfield (`cellX * 8192 + col * (8192/64)`, same for Y/row, Z = height), then the existing cell-root **−90° X**.
- Place that cell’s refs with the current `CellSceneBuilder` (doors / chests / take / NPCs / creatures).
- Exterior fog **off** (no `AMBI` fog). Interiors keep Phase 6 fog. Bright sun so grey land reads.
- Spawn: Census office exit `DODT` (not the cell AABB center, not an inbound interior spawn).
- HUD **Town** next to **Guild**. Window title Phase 16.
- Debug CLI `exterior -2 -9`: name, grid, land min/max height, ref counts.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- Empty-`DNAM` teleport (Census / cave exits still log and stay)
- `changeCellGrid` 3×3 (`CellGridRadius`)
- `LTEX` / `VTEX` dirt textures
- Water, sky, weather, `NAM5` map color
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town**: exterior grid `(-2, -9)`. Grey ground under Seyda Neen buildings; Census office exterior in view from the door `DODT`.

**Cell** / **Cave** / **Nix** / **Guild**: interiors unchanged. Hide doors, chests, take, Guild load doors still work.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- **Town** shows grey ground and Seyda Neen buildings (Census office exterior). Camera is at the Census door world `DODT`.
- Interior **Cell** / **Cave** / **Guild** unchanged (fog, lids, take, load doors).
- Chair HUD unfogged. `glError=0`.

Walking off the south edge of the cell is **expected**, not a fail. Untextured grey land is not a fail. Census empty-`DNAM` still shut is not a fail. Floating buildings with no heightfield is a **fail**. Loading nine cells or `LTEX` this phase is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Exterior identity.** `Cell::isExterior` is `!(mData.mFlags & Interior)`. `DATAstruct` is flags plus grid `mX`,`mY`. Empty-`DNAM` `getDestCell` uses `positionToExteriorCellLocation(DODT x, y)`. That is `floor(x / cellSize)`, `floor(y / cellSize)`; TES3 default worldspace `cellSize` is `CellSizeInUnits` **8192**.
2. **LAND.** `INTV` is grid `mX`,`mY`. 65 verts per side, cell size 8192, height scale 8, missing land **-2048**. `VHGT` is a float offset plus signed-byte deltas; decode row then column, multiply by 8.
3. **When terrain loads.** `RenderingManager::addCell`: if the cell is exterior, `enableTerrain` and `Terrain::loadCell(gridX, gridY)` — interiors skip that. `changeToExteriorCell` takes the dest cell’s grid and `changeCellGrid`. TES3 `changeCellGrid` uses `CellGridRadius` **1** as half-grid (cells from `center ± 1`).

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::Cell::isExterior` | `LoadedCell.interior` from `DATA` flags | same |
| `ESM::Cell::getGridX/Y` | `LoadedCell.gridX/Y` | same |
| `positionToExteriorCellLocation` | `floor(x/8192)`, `floor(y/8192)` | same |
| `ESM::Land` / `VHGT` | land record + heightfield mesh | rewrite |
| `RenderingManager::addCell` terrain | grey `LAND` `MeshInstance` | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Cell::isExterior is !(mData.mFlags & Interior). DATAstruct
holds mFlags, mX, mY. getDestCell for ESM3: if mDestCell is not
empty, that string is the dest id; else positionToExteriorCellLocation
on DODT pos[0]/pos[1]. positionToExteriorCellLocation is
floor(x/cellSize), floor(y/cellSize). For the default TES3
worldspace, getCellSize is CellSizeInUnits (8192).

From components/esm3/loadcell.hpp:
    Interior = 0x01;
    struct DATAstruct { int32_t mFlags; int32_t mX, mY; };
    bool isExterior() const { return !(mData.mFlags & Interior); }
    int32_t getGridX() const { return mData.mX; }
    int32_t getGridY() const { return mData.mY; }

From apps/openmw/mwworld/cellref.cpp getDestCell (ESM3):
    if (!ref.mDestCell.empty())
        return ESM::RefId::stringRefId(ref.mDestCell);
    else {
        cellPos = positionToExteriorCellLocation(ref.mDoorDest.pos[0],
            ref.mDoorDest.pos[1]);
        return ESM::RefId::esm3ExteriorCell(cellPos.mX, cellPos.mY);
    }

From components/esm/util.hpp:
    getCellSize: isEsm4Ext ? ESM4CellSizeInUnits : CellSizeInUnits;
    positionToExteriorCellLocation(x, y, worldspace):
        cellSize = getCellSize(worldspace);
        return { floor(x / cellSize), floor(y / cellSize), worldspace };

From components/misc/constants.hpp:
    CellSizeInUnits = 8192;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Land::load reads INTV as grid mX, mY. LAND_SIZE is 65 verts
per side, REAL_SIZE is CellSizeInUnits (8192), sHeightScale is 8,
DEFAULT_HEIGHT is -2048. VHGT is float mHeightOffset plus signed
bytes mHeightData[65*65]. loadLandRecordData decodes row y then
column x: rowOffset accumulates the first of each row, colOffset
accumulates the rest of the row; each height is that offset times 8.

From components/esm3/loadland.hpp:
    DEFAULT_HEIGHT = -2048;
    LAND_SIZE = LandRecordData::sLandSize;
    REAL_SIZE = Constants::CellSizeInUnits;
    sHeightScale = 8;

From components/esm3/landrecorddata.hpp:
    sLandSize = 65;
    sLandNumVerts = sLandSize * sLandSize;

From components/esm3/loadland.cpp Land::load:
    INTV: getHT(mX, mY);

From loadland.cpp VHGT and loadLandRecordData:
    struct VHGT {
        float mHeightOffset;
        int8_t mHeightData[sLandNumVerts];
    };
    rowOffset = vhgt.mHeightOffset;
    for y in 0 .. sLandSize-1:
        rowOffset += mHeightData[y * sLandSize];
        heights[y * sLandSize] = rowOffset * sHeightScale;
        colOffset = rowOffset;
        for x in 1 .. sLandSize-1:
            colOffset += mHeightData[y * sLandSize + x];
            heights[x + y * sLandSize] = colOffset * sHeightScale;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: RenderingManager::addCell, if the cell is exterior, calls
enableTerrain(true, worldspace) and Terrain::loadCell(getGridX(),
getGridY()). That pair is skipped when the cell is not exterior.
Scene::changeToExteriorCell looks up the dest cell, reads
getGridX/getGridY, and changeCellGrid. For TES3, changeCellGrid
sets halfGridSize to CellGridRadius (1) and iterateOverCellsAround
the player cell with that range (center ± 1).

From apps/openmw/mwrender/renderingmanager.cpp addCell:
    if (store->getCell()->isExterior()) {
        enableTerrain(true, store->getCell()->getWorldSpace());
        mTerrain->loadCell(store->getCell()->getGridX(),
            store->getCell()->getGridY());
    }

From apps/openmw/mwworld/scene.cpp changeToExteriorCell:
    current = getCell(extCellId);
    cellIndex = (current.getGridX(), current.getGridY());
    changeCellGrid(position, ExteriorCellLocation(cellIndex), ...);

From scene.cpp changeCellGrid (TES3):
    halfGridSize = Constants::CellGridRadius;
    iterateOverCellsAround(playerCellX, playerCellY, mHalfGridSize, ...);
    then loadCell each (x, y) not already active.

From scene.cpp iterateOverCellsAround:
    for x = cellX - range .. cellX + range
        for y = cellY - range .. cellY + range
            f(x, y);

From components/misc/constants.hpp:
    CellGridRadius = 1;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

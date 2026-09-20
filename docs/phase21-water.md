# Phase 21: Exterior simple water

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwworld/cell.cpp` (TES3 exterior water height), `components/esm3/loadcell.hpp` (`hasWater` / `HasWater`), `apps/openmw/mwworld/scene.cpp` (enable + height), `apps/openmw/mwrender/water.cpp` (`changeCell` / `setHeight` / simple material), `components/sceneutil/waterutil.cpp` (`createWaterGeometry` / `createSimpleWaterStateSet`), `files/openmw.cfg` water fallbacks. Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 20 blendmaps stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged.

**Town (and other loaded exteriors) get OpenMW simple water: one blended plane at TES3 height −1, covering the 3×3, with the vanilla `textures/water/water##.dds` flip.** No shader reflections, refraction, ripples, underwater fog, or sky. The 3×3 still does not recenter as you walk.

## Why this slice

Seyda Neen harbor is dry land because this port never draws water. Lily pads sit on the waterline. OpenMW always enables water on TES3 exteriors and pins the plane at **−1** (it does **not** use that cell’s `WHGT` outdoors). The cheap path is shader-off simple water: a large XY quad, blend, no cull, **no depth write**, Morrowind’s 32-frame water strip.

Smallest visible water: that plane on the existing 3×3. Skip RTT water, interior `HasWater` cells, and walk-recenter.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Same 3×3 load as Phase 18–20. Same cell-root **−90° X**. Water verts are TES3 XY with Z = water height (a child of that root, like land).
- Exterior water is **always on**. Height is **−1** (`MWWorld::Cell` TES3 constructor). Do not read `WHGT` for this height.
- One mesh (never `ModelBatch`) large enough to cover the loaded 3×3. OpenMW’s mesh is `CellSizeInUnits * 150` with 40 segments and 900 UV repeats; a smaller patch is fine if UV density stays about **6 repeats per cell** (`900 / 150`). Center XY on the dest cell like `getSceneNodeCoordinates` (`grid * 8192 + 4096`).
- Simple water state: `GL_BLEND` on, cull off, depth test on, **depth write off**, material alpha **0.75** (`Water_World_Alpha`). Draw in the existing alpha pass (after terrain and opaque/alpha-test flora) so pads and beaches already in the depth buffer stay on top.
- Bind `textures/water/water00.dds` … `water31.dds` (`Water_SurfaceTexture` + two-digit frame, `Water_SurfaceFrameCount` 32). Flip at **12** fps (`Water_SurfaceFPS`). Missing frames: use whatever exists (at least `water00.dds`). `TexturePaths.correctTexturePath` + VFS/`DdsTexture`.
- Interiors: **no** water this slice (even if `DATA` has `HasWater`). Fog still off on exteriors.
- Debug CLI `exterior -2 -9`: keep today’s `grid=` / `vtex=` / `layers=` / `ltex=` lines; add `water=-1` on the center `cell=` line.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- Shader water (`Settings::water().mShader`): reflection / refraction RTT, `water_nm`, rain ripples
- Interior water (`HasWater` + `WHGT` / `INTV`)
- Underwater fog, swimming, water sound
- Sky, weather, sun on water
- Recenter / unload when the camera crosses a cell line
- Physics water / water walking
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** or **Cell** → nord exit **E**: stand at Census `DODT`. Harbor / lily-pad water is a translucent tiled plane, not dry dirt. Land above −1 (Census yard, docks) stays dry. Walk south a bit; water still covers the neighbor tiles.

**Cell** / **Cave** / **Guild** interiors unchanged (no water mesh). Chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town harbor shows water (translucent, tiled). Lily pads sit on it, not in a dry basin.
- Beaches / ground **above** the plane stay dry. Neighbor tiles in the 3×3 also have water.
- Interior **Cell** / **Cave** / **Guild** unchanged. Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Sky, reflections, or underwater fog this phase is a **fail**. Town still a dry dirt “ocean” is a **fail**. Interior water this phase is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **When / how high.** TES3 exteriors always have water at height **−1**. `Cell::hasWater` is `(flags & HasWater) || isExterior()`. Scene enables water if `hasWater() || isExterior()`. Exterior `getWaterLevel` returns that **−1**.
2. **Mesh / pose.** `createWaterGeometry(CellSizeInUnits * 150, 40, 900)`. Exterior `changeCell` places XY at cell center; `setHeight` writes Z.
3. **Simple material.** Shader-off path uses `Water_World_Alpha` (0.75): blend on, cull off, depth write off. Frames `textures/water/{SurfaceTexture}{00..n-1}.dds`, 32 frames at 12 fps.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `MWWorld::Cell` TES3 exterior water | always on, height −1 | same |
| `SceneUtil::createWaterGeometry` | water mesh on cell root | rewrite |
| `Water::createSimpleWaterStateSet` | blend, no cull, no depth write, water## flip | rewrite |
| `Water_World_Alpha` / `Water_Surface*` | 0.75 / `water` / 32 / 12 | same |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: TES3 Cell::hasWater is (mData.mFlags & HasWater) != 0 OR
isExterior(). HasWater is 0x02. MWWorld::Cell's TES3 constructor, when
isExterior(), sets mWaterHeight = -1.f and mHasWater = true (it does
not keep ESM WHGT for exteriors). Scene::insertCell enables water when
cellVariant.hasWater() || cell.isExterior(), and if enabled calls
setWaterHeight(cell.getWaterLevel()). CellStore::getWaterLevel for an
exterior returns getCell()->getWaterHeight() (that -1).

From components/esm3/loadcell.hpp:
    HasWater = 0x02,
    bool isExterior() const { return !(mData.mFlags & Interior); }
    bool hasWater() const { return ((mData.mFlags & HasWater) != 0) || isExterior(); }

From apps/openmw/mwworld/cell.cpp Cell(const ESM::Cell& cell):
    mHasWater(cell.mData.mFlags & ESM::Cell::HasWater)
    mWaterHeight(cell.mWater)
    if (isExterior())
    {
        mWaterHeight = -1.f;
        mHasWater = true;
    }

From apps/openmw/mwworld/scene.cpp:
    bool waterEnabled = cellVariant.hasWater() || cell.isExterior();
    float waterLevel = cell.getWaterLevel();
    mRendering.setWaterEnabled(waterEnabled);
    if (waterEnabled)
        mRendering.setWaterHeight(waterLevel);

From apps/openmw/mwworld/cellstore.cpp getWaterLevel:
    if (isExterior())
        return getCell()->getWaterHeight();
    return mWaterLevel;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Water constructs geometry with
SceneUtil::createWaterGeometry(Constants::CellSizeInUnits * 150, 40, 900).
createWaterGeometry(size, segments, textureRepeats) builds a size×size
XY quad centered on origin, Z = 0, subdivided into segments×segments
cells (some drivers dislike huge triangles). UV step is
textureRepeats/segments. Exterior changeCell sets the water node XY to
getSceneNodeCoordinates(gridX, gridY) =
(gridX * CellSizeInUnits + CellSizeInUnits/2,
 gridY * CellSizeInUnits + CellSizeInUnits/2, mTop).
setHeight(height) stores mTop and writes the node's Z to height.

From apps/openmw/mwrender/water.cpp Water::Water:
    mWaterGeom = SceneUtil::createWaterGeometry(Constants::CellSizeInUnits * 150, 40, 900);

From components/sceneutil/waterutil.cpp createWaterGeometry:
    // some drivers don't like huge triangles, so we do some subdivisons
    step = size / segments;
    texCoordStep = textureRepeats / segments;
    verts at z = 0; xy from -size/2 to +size/2.

From apps/openmw/mwrender/water.cpp:
    changeCell exterior:
        mWaterNode->setPosition(getSceneNodeCoordinates(gridX, gridY));
    setHeight(height):
        mTop = height;
        pos = mWaterNode->getPosition(); pos.z() = height; setPosition(pos);
    getSceneNodeCoordinates(gridX, gridY):
        return (gridX * CellSizeInUnits + CellSizeInUnits/2,
                gridY * CellSizeInUnits + CellSizeInUnits/2, mTop);

From components/misc/constants.hpp:
    CellSizeInUnits = 8192;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: If Settings::water().mShader is false, Water::updateWaterMaterial
calls createSimpleWaterStateSet(mWaterGeom, Water_World_Alpha).
SceneUtil::createSimpleWaterStateSet: GL_BLEND on, GL_CULL_FACE off,
depth write mask false. Water::createSimpleWaterStateSet then loads
frameCount textures named
textures/water/{Water_SurfaceTexture}{frame:02d}.dds, clamps
frameCount to 0..320, and flips at Water_SurfaceFPS. openmw.cfg
fallbacks: Water_World_Alpha 0.75, Water_SurfaceTexture water,
Water_SurfaceFrameCount 32, Water_SurfaceFPS 12.

From apps/openmw/mwrender/water.cpp updateWaterMaterial:
    if (Settings::water().mShader) { reflection/refraction/ripples; createShaderWaterStateSet; }
    else
        createSimpleWaterStateSet(mWaterGeom, Fallback::Map::getFloat("Water_World_Alpha"));

From components/sceneutil/waterutil.cpp createSimpleWaterStateSet:
    Material diffuse alpha = alpha;
    GL_BLEND ON;
    GL_CULL_FACE OFF;
    Depth writeMask false;

From apps/openmw/mwrender/water.cpp createSimpleWaterStateSet:
    frameCount = clamp(Water_SurfaceFrameCount, 0, 320);
    texture = Water_SurfaceTexture;
    texname << "textures/water/" << texture << setw(2) << setfill('0') << i << ".dds";
    fps = Water_SurfaceFPS;
    FlipController(0, 1.f / fps, textures);

From files/openmw.cfg:
    fallback=Water_World_Alpha,0.75
    fallback=Water_SurfaceFPS,12
    fallback=Water_SurfaceTexture,water
    fallback=Water_SurfaceFrameCount,32

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

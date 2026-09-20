# Phase 20: Exterior land blendmaps

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esmterrain/storage.cpp` (`getBlendmaps` TES3), `components/esmterrain/gridsampling.hpp` (`getBlendmapSize` / `sampleBlendmaps`), `components/terrain/chunkmanager.cpp` (`createPasses` / `getTextureTileCount`), `components/terrain/material.cpp` (`createPasses` / `BlendmapTexMat` / blend funcs), `files/shaders/compatibility/terrain.frag`. Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 19 `VTEX`/`LTEX` stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged.

**Land layers mix at square edges the OpenMW TES3 way: per-layer blendmaps (2× nearest) and alpha passes.** Hard 4×4 seams from Phase 19 go away. The 3×3 still does not recenter as you walk. No water or sky.

## Why this slice

Phase 19 binds one texture per `VTEX` square. Adjacent dirt/sand/grass meet on a hard line. OpenMW `Storage::getBlendmaps` (TES3) samples `VTEX` into a grid of size `LAND_TEXTURE_SIZE * chunkSize + 1`, then **doubles** it with nearest-neighbor 2×2 writes of 255, one `GL_ALPHA` image per layer. `createPasses` draws each layer with that blendmap as fragment alpha (`terrain.frag`). One layer → no blendmaps.

Smallest mix: do that for each of the nine loaded cells (`chunkSize` **1**). Skip ESM4 blendmaps, water, sky, and walk-recenter.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Same 3×3 load as Phase 18/19. Same `LTEX` / `VTEX` / `_land_default.dds`.
- Per land tile, `chunkSize = 1`: `blendmapSize = 16 * 1 + 1` (**17**). Sample this cell’s 16×16 `VTEX` plus the neighbor’s first row/col for the extra line (Phase 18 already loaded those lands). Unique texture ids → layers (`getTextureName` as Phase 19).
- Each layer: `GL_ALPHA` image **34×34** (`17 * 2`). For each sample, write four 255s at `(2x, 2y)…(2x+1, 2y+1)`. If only one layer, skip blendmaps (draw that texture alone).
- One mesh **per layer** covering the whole 65×65 heightfield (not the Phase 19 per-square split). Diffuse UV tiles **16** times across the cell (`getTextureTileCount(1) = 16`). Blendmap UV: scale `16/17` about the center, then the vanilla nudge `(1/(16*4), -1/(16*4))`.
- Draw layers with blendmaps: first `SRC_ALPHA, ZERO` + depth `LEQUAL`; later `SRC_ALPHA, ONE` + depth `EQUAL`. Fragment alpha *= blendmap `.a`. Never `ModelBatch`.
- Interiors unchanged. Fog still off on exteriors. No `VCLR`. No layer normal/specular maps.
- Debug CLI `exterior -2 -9`: keep today’s `grid=` / `vtex=` / `ltex=` lines; add `layers=` unique-texture count per tile (same as vtex-resolved layers).

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- ESM4 land blendmaps
- Recenter / unload when the camera crosses a cell line
- `VCLR` vertex colors
- Water, sky, weather
- Terrain LOD / composite maps / normal maps
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** or **Cell** → nord exit **E**: stand at Census `DODT`. Dirt/sand/grass still show, but square edges mix instead of a hard grid. Walk south a bit; neighbor land also mixed.

**Cell** / **Cave** / **Guild** interiors unchanged. Chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town / Census-exit land still textured, and Phase 19 hard seams between texture squares are gone (or clearly mixed).
- Neighbor tiles in the 3×3 also mix.
- Interior **Cell** / **Cave** / **Guild** unchanged. Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Water or sky this phase is a **fail**. Land back to a grey/white slab is a **fail**. Hard unblended squares still as Phase 19 is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Blendmap images.** TES3 `getBlendmaps` uses `blendmapSize = getBlendmapSize(chunkSize, LAND_TEXTURE_SIZE)` and `getBlendmapSize(size, textureSize) = textureSize * size + 1`. It upscales **2×** nearest (`imageScaleFactor = 2`, image side `blendmapSize * 2`). Each unique `UniqueTextureId` gets a `GL_ALPHA` image, zeros, then four 255s at the 2×2 for that sample. If there is only one blendmap, it clears the list.
2. **Tile count / UV.** TES3 `getTextureTileCount(chunkSize) = LAND_TEXTURE_SIZE * chunkSize` (16 for size 1). `createPasses` passes that as `blendmapScale` and `layerTileSize`. Non-ESM4 blendmap texmat: scale `s/(s+1)` about the center, then translate `(1/s/4, -1/s/4)`.
3. **Passes.** `createPasses` if blendmaps nonempty: `GL_BLEND` on. First layer `SRC_ALPHA, ZERO` and depth `LEQUAL`; later layers `SRC_ALPHA, ONE` and depth `EQUAL`. `terrain.frag`: `gl_FragData[0].a *= texture2D(blendMap, blendMapUV).a`.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Storage::getBlendmaps` TES3 | per-cell 17→34 alpha layers | rewrite |
| `getBlendmapSize` | `16 * 1 + 1` | same |
| `Terrain::createPasses` | land layer meshes + blend | rewrite |
| `terrain.frag` `@blendMap` | fragment alpha *= blendmap | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: TES3 Storage::getBlendmaps sets blendmapSize =
getBlendmapSize(chunkSize, LAND_TEXTURE_SIZE). getBlendmapSize(size,
textureSize) is textureSize * size + 1. It upscales 2x with nearest
neighbor (imageScaleFactor = 2, blendmapImageSize = blendmapSize *
imageScaleFactor) to look like Vanilla. For each unique
UniqueTextureId it allocates a GL_ALPHA image of that side, memset 0,
and writes 255 into the 2x2 at (2x,2y). If blendmaps.size() == 1 it
clears blendmaps (no need to blend).

From components/esmterrain/gridsampling.hpp:
    getBlendmapSize(size, textureSize):
        return textureSize * size + 1;

From components/esmterrain/storage.cpp getBlendmaps (not ESM4):
    blendmapSize = getBlendmapSize(chunkSize, ESM::Land::LAND_TEXTURE_SIZE);
    // We need to upscale the blendmap 2x with nearest neighbor sampling
    // to look like Vanilla
    imageScaleFactor = 2;
    blendmapImageSize = blendmapSize * imageScaleFactor;
    ...
    allocateImage(blendmapImageSize, blendmapImageSize, 1, GL_ALPHA, ...);
    memset 0;
    data[((realY+0)*blendmapImageSize + realX+0)] = 255;
    data[((realY+1)*blendmapImageSize + realX+0)] = 255;
    data[((realY+0)*blendmapImageSize + realX+1)] = 255;
    data[((realY+1)*blendmapImageSize + realX+1)] = 255;
    ...
    if (blendmaps.size() == 1)
        blendmaps.clear(); // If a single texture fills the whole terrain,
                           // there is no need to blend

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: TES3 getTextureTileCount(chunkSize) returns
LAND_TEXTURE_SIZE * chunkSize. ChunkManager::createPasses passes
that integer as blendmapScale and as layerTileSize into
Terrain::createPasses. For non-ESM4, blendmap texture matrix:
scale = blendmapScale / (blendmapScale + 1) about the center, then
translate (1.0 / blendmapScale / 4.0, -1.0 / blendmapScale / 4.0)
because Vanilla doubles the blendmap.

From components/esmterrain/storage.cpp:
    getTextureTileCount(chunkSize, worldspace):
        if ESM4: 2 * sQuadTexturePerSide * chunkSize;
        else: LAND_TEXTURE_SIZE * chunkSize;

From components/terrain/chunkmanager.cpp createPasses:
    tileCount = mStorage->getTextureTileCount(chunkSize, mWorldspace);
    return Terrain::createPasses(..., blendmapTextures, tileCount,
        (float) tileCount, ...);

From components/terrain/material.cpp BlendmapTexMat:
    scale = (blendmapScale / (blendmapScale + 1.f));
    translate 0.5,0.5; scale; translate -0.5,-0.5;
    // We need to nudge the blendmap to look like vanilla.
    // This causes visible seams unless the blendmap's resolution is
    // doubled, but Vanilla also doubles the blendmap, apparently.
    translate (1.0 / blendmapScale / 4.0f, -1.0 / blendmapScale / 4.0f);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Terrain::createPasses, when blendmaps is not empty, enables
GL_BLEND. The first layer uses BlendFunc SRC_ALPHA, ZERO and depth
LEQUAL. Later layers use BlendFunc SRC_ALPHA, ONE and depth EQUAL.
terrain.frag when @blendMap: fragment alpha *=
texture2D(blendMap, blendMapUV).a.

From components/terrain/material.cpp createPasses:
    if (!blendmaps.empty()) {
        GL_BLEND ON;
        if firstLayer:
            BlendFuncFirst = SRC_ALPHA, ZERO;
            LequalDepth;
        else:
            BlendFunc = SRC_ALPHA, ONE;
            EqualDepth;
        bind blendmap on texture unit 1;
    }

From files/shaders/compatibility/terrain.frag:
    #if @blendMap
        blendMapUV = (gl_TextureMatrix[1] * vec4(uv,0,1)).xy;
        gl_FragData[0].a *= texture2D(blendMap, blendMapUV).a;
    #endif

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

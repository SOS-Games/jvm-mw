# Phase 19: Exterior land textures (`LTEX` / `VTEX`)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/landrecorddata.hpp` (`mTextures` / `sLandTextureSize`), `components/esm3/loadland.cpp` (`VTEX` / `transposeTextureData`), `components/esm3/loadltex.hpp` / `loadltex.cpp` (`LandTexture`), `apps/openmw/mwworld/store.cpp` (`Store<LandTexture>::search` / `load`), `components/esmterrain/storage.cpp` (`getTextureIdAt` / `getTextureName`), `apps/openmw/mwrender/terrainstorage.cpp` (`getLandTexture`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same Path B viewer. Phase 18 3×3 grey land stays as the heightfield. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged.

**Each exterior land tile uses that cell’s `VTEX` indices and the ESM `LTEX` palette so Seyda Neen ground is dirt/sand/grass, not a white/grey slab.** No blendmaps. The 3×3 still does not recenter as you walk.

## Why this slice

Phase 18 placed nine heightfields. They are one shared white texel. OpenMW paints land from `LAND` `VTEX` (16×16 `uint16` per cell) plus `LTEX` (`INTV` index + `DATA` path). Index **0** is `_land_default.dds`; any other index looks up `LTEX` at `index - 1`.

Smallest visible paint: bind those textures on the existing 3×3. Skip OpenMW blendmaps (hard seams between squares). Skip streaming a new 3×3, water, and sky.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Same exterior load as Phase 18 (Town or empty-`DNAM` **E**): 3×3 around dest grid, one cell-root −90° X.
- Parse `LTEX` in that ESM pass: `NAME`, `INTV` (palette index), `DATA` (texture path). Lookup by index (this port’s only plugin is `Morrowind.esm`).
- Parse `LAND` `VTEX` when present: 256 little-endian `uint16`, then OpenMW `transposeTextureData` (4×4 blocks of 4×4). Missing `VTEX` → all zeros (default texture). Keep Phase 16 `VHGT`.
- Each `VTEX` square covers 4×4 height quads (`64 / 16`). UV 0–1 across that square. `VTEX` 0 or a missing `LTEX` → `_land_default.dds`. Else `getLandTexture(vtex - 1)` then existing `TexturePaths.correctTexturePath` + VFS/`DdsTexture` (never `ModelBatch`).
- One Path B `MeshGpu` per distinct texture **per land tile** (or per square, if simpler). Shared textures across tiles are fine.
- Interiors unchanged. Fog still off on exteriors. No `VCLR`. No blendmaps.
- Debug CLI `exterior -2 -9`: keep the nine `grid=` lines; add `vtex=` unique-index count (and `land=` as today).

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- OpenMW terrain blendmaps / layer mixing
- Recenter / unload when the camera crosses a cell line
- `VCLR` vertex colors
- Water, sky, weather
- Plugins (extra `LTEX` palettes)
- Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** or **Cell** → nord exit **E**: stand at Census `DODT`. Ground around the office is dirt/sand/grass (not uniform grey). Walk **south** a bit; neighbor land is also textured.

**Cell** / **Cave** / **Guild** interiors unchanged. Chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town / Census-exit land shows Morrowind ground textures (not a white or grey slab).
- Neighbor tiles in the 3×3 are textured too.
- Interior **Cell** / **Cave** / **Guild** unchanged. Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Hard seams between texture squares are **expected**, not a fail. Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Blendmaps, water, or sky this phase is a **fail**. Still-grey land after Town load is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **VTEX indices.** `LAND` has 16 textures per side (256 indices). `mTextures` is those `uint16`s. Index **0** is the default texture; to look up an `LTEX`, subtract **1**. `getTextureIdAt`: no land or no `DATA_VTEX` or `tex == 0` → `{0, 0}`; else `{tex, land plugin}`.
2. **File order.** `loadLandRecordData` reads `VTEX` as 256 `uint16`, then `transposeTextureData` into `mTextures`. The transpose is four nested 4-loops: `out[(y1 * 4 + y2) * 16 + (x1 * 4 + x2)] = in[readPos++]` with `y1,x1,y2,x2` each `0..3`.
3. **Path.** `Storage::getTextureName`: if `id.first == 0` use `"_land_default.dds"`; else `getLandTexture(id.first - 1, id.second)` and that string, or default if missing. Then `correctTexturePath`. `LandTexture::load` has `NAME` id, `INTV` `mIndex`, `DATA` `mTexture`. `Store<LandTexture>::search(index, plugin)` returns that `DATA` string.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::LandTexture` / `LTEX` | `LandTexture` (`INTV` + `DATA`) | same |
| `LandRecordData::mTextures` | `LandRecord.textures` after transpose | rewrite |
| `transposeTextureData` | decode `VTEX` | same |
| `Storage::getTextureName` | `LandMesh` bind per `VTEX` square | rewrite |
| `_land_default.dds` | `VTEX` 0 / missing `LTEX` | same |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: LAND has 16 textures per side (256 indices). LandRecordData
mTextures is a 2D array of uint16 texture indices. An index can be
used to look up a LandTexture, but you must subtract 1 from the
index first. An index of 0 indicates the default texture.
getTextureIdAt(land, x, y): if land is null or getData(DATA_VTEX) is
null, return {0, 0}. Else tex = textures[y * LAND_TEXTURE_SIZE + x];
if tex == 0 return {0, 0}; else return {tex, land->getPlugin()}.

From components/esm3/landrecorddata.hpp:
    sLandTextureSize = 16;
    sLandNumTextures = sLandTextureSize * sLandTextureSize;
    // 2D array of texture indices. An index can be used to look up an LandTexture,
    // but to do so you must subtract 1 from the index first!
    // An index of 0 indicates the default texture.
    std::array<std::uint16_t, sLandNumTextures> mTextures;

From components/esm3/loadland.hpp:
    LAND_TEXTURE_SIZE = LandRecordData::sLandTextureSize;
    DATA_VTEX = 16;

From components/esmterrain/storage.cpp getTextureIdAt:
    if (land == nullptr) return { 0, 0 };
    data = land->getData(ESM::Land::DATA_VTEX);
    if (data == nullptr) return { 0, 0 };
    tex = data->getTextures()[y * LAND_TEXTURE_SIZE + x];
    if (tex == 0) return { 0, 0 }; // vtex 0 is always the base texture
    return { tex, land->getPlugin() };

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: loadLandRecordData, when the next sub is VTEX, reads 256
uint16 values into a temporary vtex array, then
transposeTextureData(vtex, data.mTextures.data()).
transposeTextureData copies in[] to out[] with readPos starting at 0:
for y1 in 0..3, for x1 in 0..3, for y2 in 0..3, for x2 in 0..3:
out[(y1 * 4 + y2) * 16 + (x1 * 4 + x2)] = in[readPos++].

From components/esm3/loadland.cpp loadLandRecordData:
    if (reader.isNextSub("VTEX")) {
        uint16_t vtex[LandRecordData::sLandNumTextures];
        if (condLoad(..., DATA_VTEX, vtex))
            transposeTextureData(vtex, data.mTextures.data());
    }

From loadland.cpp transposeTextureData:
    readPos = 0;
    for y1 = 0 .. 3
        for x1 = 0 .. 3
            for y2 = 0 .. 3
                for x2 = 0 .. 3
                    out[(y1 * 4 + y2) * 16 + (x1 * 4 + x2)] = in[readPos++];

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Storage::getTextureName starts at "_land_default.dds". If
id.first != 0 it calls getLandTexture(id.first - 1, id.second)
(vtex ids are +1 compared to ltex ids) and uses that string when
found, else keeps the default. Then correctTexturePath.
LandTexture::load reads NAME (mId), INTV (mIndex), DATA (mTexture).
Store<LandTexture>::load stores mTexture by mId and maps
(plugin, mIndex) -> mId. search(index, plugin) returns that string.

From components/esmterrain/storage.cpp getTextureName:
    texture = "_land_default.dds";
    if (id.first != 0) {
        // NB: All vtex ids are +1 compared to the ltex ids
        ltex = getLandTexture(id.first - 1, id.second);
        if (ltex) texture = *ltex;
        else log warning and keep default;
    }
    return correctTexturePath(texture, *mVFS);

From apps/openmw/mwrender/terrainstorage.cpp:
    getLandTexture(index, plugin):
        return esmStore.get<ESM::LandTexture>().search(index, plugin);

From components/esm3/loadltex.cpp LandTexture::load:
    NAME -> mId; INTV -> mIndex; DATA -> mTexture.

From apps/openmw/mwworld/store.cpp Store<LandTexture>:
    load: mStatic[lt.mId] = lt.mTexture;
          mMappings.emplace({plugin, lt.mIndex}, lt.mId);
    search(index, plugin): mapping = mMappings[{plugin, index}];
        return &mStatic[mapping];

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

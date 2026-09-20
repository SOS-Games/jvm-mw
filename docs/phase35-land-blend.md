# Phase 35: Softer ground blends

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with the TES3 blendmap **grid** already in `LandMesh` (`blendmapSize = 16 * 1 + 1`). Do **not** keep OpenMW’s 2× nearest upsample (`storage.cpp` `imageScaleFactor = 2` “to look like Vanilla”). That is what makes Town dirt/grass look like tiles. Soft mix is **linear sample of the 17×17 layer masks**. No other-LLM prompts: the 17-sample grid and the 2×2 writes are already in `LandMesh`; this slice only changes how those samples become texels.

## Goal

Same viewer. Phase 34 small-feature / viewing distance / water cameras stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load. Grid stays **5×5-minus-corners**.

**Town dirt and grass mix across square edges** instead of a 2× nearest checker. Layer list, heightfield, and blend passes stay.

## Why this slice

Phase 20 matched OpenMW TES3 `getBlendmaps`: 17 VTEX samples, then a **34×34** image with four 255s per sample. GPU filter is already `GL_LINEAR`, but each VTEX square is a 2×2 brick of solid alpha, so the mix is a thin seam between blocks. Town ground still reads as a grid.

Vanilla/OpenMW want that look. This viewer does not.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Keep `BLENDMAP_SIZE = 17`, neighbor row/col, unique-texture layers, first `SRC_ALPHA, ZERO` + `LEQUAL`, later `SRC_ALPHA, ONE` + `EQUAL`. One mesh per layer. Skip blendmaps when there is only one layer.
- **Stop 2× nearest.** `IMAGE_SCALE = 1`. Each layer image is **17×17**. Write one 255 at `(x, y)` for that sample, not a 2×2. Upload stays `GL_R8` + `GL_LINEAR` + clamp.
- **Blendmap UV:** drop the vanilla 2× nudge `(1/16/4, -1/16/4)`. Map cell UV so texel centers sit on the 17 samples: `buv = v_uv * (16.0/17.0) + 0.5/17.0`. Diffuse UV tile count **16** unchanged.
- `bindUnit` must not force nearest on the blendmap. Interiors unchanged. No `VCLR`. No layer normal maps. Never `ModelBatch`.
- Chair HUD unfogged. Dump / F4 / frustum / intern / water RTT unchanged.

## Out of scope

- ESM4 blendmaps
- Merging land draws into one submit
- Keep overlapping tiles
- Terrain LOD / composite maps
- Weather fog, moons, sunglare
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Look at the dirt/grass around the Census office and the path to the harbor. Square edges should **fade**, not brick. Neighbor tiles in the 5×5 also mix. **Cell** / **Cave** still load; chair unfogged.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Town land still textured. Dirt/grass/sand edges are a soft mix, not 2× nearest blocks. `glError=0`.
- Neighbor cells in the 5×5 also mix. No grey/white slab.
- Chair HUD unfogged. Walk-grid / Dump / F4 / intern / water / small-feature unchanged.

Hard unblended squares as Phase 20 is a **fail**. Missing land textures is a **fail**. Changing grid radius or skipping water is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| TES3 `getBlendmaps` 17 samples, **no** 2× nearest | `LandMesh` 17×17 `GL_LINEAR` layer masks | rewrite |
| `BlendmapTexMat` 2× nudge | half-texel `16/17 + 0.5/17` | rewrite |

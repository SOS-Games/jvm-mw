# Phase 2: NIF materials (flatten + layers)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/nifosg/nifloader.cpp` (`collectDrawableProperties`, `applyDrawableProperties`, `handleTextureProperty`) and `files/shaders/compatibility/objects.frag`. Not with nif.xml folklore.

## Goal

Same Path B viewer. Static NIFs should pick up **inherited** NetImmerse properties and Morrowind texture layers so a glow weapon and a vertex-colored mesh match OpenMW / NifSkope, not just “a textured mesh.”

Phase 1 still passes (chair, shack, tree).

## Why flatten

Path B has no OSG `StateSet` inheritance. OpenMW puts some properties on the node graph (textures, z-buffer, stencil, wireframe) and **re-collects** others onto each drawable:

- Walk **parent chain first**, then the `NiAVObject` itself.
- Collect: `NiMaterialProperty`, `NiVertexColorProperty`, `NiSpecularProperty`, `NiAlphaProperty`.
- Apply in that list order so a **child of the same type wins**.

`NiTexturingProperty` is **not** merged per slot. A child `NiTexturingProperty` **clears** parent textures and binds its own (`handleTextureProperty` → `clearBoundTextures`). If the child has none, it keeps the nearest ancestor’s texturing.

Java: one flattened `MaterialState` on each `MeshGpu` / `MeshInstance` at load. No runtime parent walk per frame.

## In scope

Morrowind NIF **4.0.0.2** only.

- Flatten material / vertex-color / specular / alpha onto leaves.
- Flatten texturing: last `NiTexturingProperty` on the path wins entirely.
- Texture stages **0, 1, 2, 4** (base, dark, detail, glow). Bind wrap from `mClamp` (`wrapT = bit 0`, `wrapS = bit 1` — that order is OpenMW).
- Shader (GLSL 330, still no `ModelBatch`):
  - `diffuse = texture(base)` (white if missing)
  - `diffuse *= texture(dark)` including alpha, if dark enabled
  - `diffuse.rgb *= texture(detail).rgb * 2.0` if detail enabled
  - multiply lighting from material + vertex-color mode
  - `diffuse.rgb += texture(glow).rgb` **after** lighting if glow enabled
- Vertex colors uploaded; `colorMode` matches OpenMW `vertexcolors.glsl` / `applyDrawableProperties`.
- Full `NiAlphaProperty` bitfield (not just blend/test flags).
- `NiZBufferProperty`: depth test = `flags & 1`, depth write = `flags & 2`. **Ignore** the stored test function (OpenMW does).
- `NiStencilProperty` **draw mode only**: `Both` → no cull; else back-face cull. Do not implement the stencil buffer.
- Morrowind specular **off** (`mVersion <= VER_MW`): specular rgb 0, shininess 0, even if `NiSpecularProperty` is on.
- Two draws: non-blend (opaque + alpha test) then blend. `NoSorter` still blends but stays in tree order with depth write on.
- HUD: add the new test meshes next to Chair / Shack / Tree.
- NAME_MAP rows below when the types exist.

## Vertex color (do not swap)

From `NiVertexColorProperty` (`property.hpp`):

| `mVertexMode` | Meaning |
| --- | --- |
| `0` `VertMode_SrcIgnore` | Ignore vertex color (`colorMode` none) |
| `1` `VertMode_SrcEmissive` | Vertex color is **emissive** |
| `2` `VertMode_SrcAmbDif` | Then use `mLightingMode` |

If mode is `2`:

| `mLightingMode` | Meaning |
| --- | --- |
| `0` `LightMode_Emissive` | Zero material diffuse rgb and ambient; `colorMode` none |
| `1` `LightMode_EmiAmbDif` | Vertex color is **ambient+diffuse** |

If the mesh has **no** vertex color array, OpenMW pretends the missing color is white for that mode, then turns `colorMode` off. Copy that.

Material `mAlpha` multiplies texture alpha (`getDiffuseColor().a` in `objects.frag`).

Lighting (Phase 2 is still the simple directional light, not OpenMW’s full light list):

```
lighting = ambient * ambientLight + diffuse * ndl + emissive
lit = tex.rgb * lighting
```

then add glow map. `ambientLight` can stay a constant (~0.35) like Phase 1. Emissive is part of the lighting multiply (`objects.frag`), not added after, or a 1,1,1 material washes the mesh to white.

## Alpha bitfield

`NiAlphaProperty.mFlags` (`property.hpp`):

| Bits | Meaning |
| --- | --- |
| `0x0001` | blend on |
| `>>1 & 0xF` | src blend |
| `>>5 & 0xF` | dest blend |
| `0x0200` | alpha test on |
| `>>10 & 0x7` | test function |
| `0x2000` | **NoSorter** — blend but do not put in transparent sort bin |

Blend modes 0–10: `ONE, ZERO, SRC_COLOR, ONE_MINUS_SRC_COLOR, DST_COLOR, ONE_MINUS_DST_COLOR, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, DST_ALPHA, ONE_MINUS_DST_ALPHA, SRC_ALPHA_SATURATE`. Unknown → `SRC_ALPHA`.

OpenMW quirk: dest `DST_ALPHA` is rewritten to `GL_ONE` (D3D8 vs GL). Do the same.

Test modes 0–7: `ALWAYS, LESS, EQUAL, LEQUAL, GREATER, NOTEQUAL, GEQUAL, NEVER`. Unknown → `LEQUAL`. Threshold is `mThreshold / 255`. Default MW cutout is usually `GREATER`.

Keep two-sided (no cull) when alpha test or blend is on.

## Texture layers (shader order)

Same order as `objects.frag`:

1. Sample base (or white).
2. Multiply dark (rgba).
3. Alpha test using the current alpha.
4. Multiply detail rgb by 2.
5. Lighting using material + vertex color.
6. Add glow rgb.

Skip bump (5), gloss (3), decal (6) sampling. If those slots are enabled, log once; do not throw.

`ApplyMode`: MW is almost always `Modulate` (2). If not 2, log and still modulate. Hilight2/parallax is not Phase 2.

## Out of scope

Skinning, `.kf`, particles, UV/material/alpha **controllers**, `NiFlipController`, bump/env/decal/gloss sampling, BS shader properties, fog, real stencil buffer, ESM, Lua, Bullet, MyGUI.

## Test meshes

All from `Morrowind.bsa`. Not committed. Confirm slots in NifSkope (or a Java dump of enabled texture indices + vertex-color mode) before calling a test a pass.

| Mesh | Why |
| --- | --- |
| `meshes/f/furn_de_chair_01.nif` | Phase 1 regression (baked vertex colors) |
| `meshes/x/ex_de_shack_01.nif` | Phase 1 regression |
| `meshes/f/flora_bc_tree_01.nif` | Leaf cards: blend + two-sided (this NIF is not alpha-test cutout) |
| `meshes/f/furn_6th_banner.nif` | **Dark** map (slot 1) + emissive nails. Vanilla `Morrowind.bsa` has **no** glow (slot 4) or detail (slot 2) maps |
| `meshes/w/w_dagger_glass.nif` | HUD extra; base-only (not glow) |
| `meshes/i/in_dwrv_corr1_00.nif` | Non-white vertex colors, no `NiVertexColorProperty` → default amb+diff |

If a candidate has unknown records, add the record type or pick the next vanilla static of the same kind. Do not skip flatten to make it load.

## Pass / fail

- Chair / shack / tree still look right; tree leaf cards still read as foliage.
- Banner nails keep jewel/iron color (emissive is a lighting multiply, not `+ white`).
- Vertex-colored mesh: painted lighting matches OpenMW/NifSkope, not a uniform tint from ignoring colors.
- A `NiTriShape` with **no** own alpha/material still receives the parent’s (inheritance).
- Child `NiTexturingProperty` does **not** keep parent glow/dark if the child omitted those slots (full replace).
- `glGetError() == 0`. Collision still hidden.
- Scene2D HUD still clickable after the GL reset.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `Nif::NiVertexColorProperty` | `nif.NiVertexColorProperty` | same |
| `Nif::NiMaterialProperty` | `nif.NiMaterialProperty` (already parsed; **use** it) | same |
| `Nif::NiAlphaProperty` | already parsed; full bitfield | same |
| `Nif::NiZBufferProperty` | stop stubbing; flatten depth flags | same |
| `Nif::NiStencilProperty` | draw-mode cull only | same (partial) |
| `NifOsg::collectDrawableProperties` / `applyDrawableProperties` | `NifSceneBuilder` flatten | rewrite |
| `objects.frag` layers | `ForwardRenderer` GLSL | rewrite |

# Phase 25: Day clouds

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/sky.cpp` (`SkyManager::create` cloud meshes / `onFrame` UV scroll / `setWeather` cloud texture and colour), `apps/openmw/mwrender/skyutil.cpp` (`CloudUpdater` / `ModVertexAlphaVisitor` Clouds), `files/shaders/compatibility/sky.vert` / `sky.frag` (`paintClouds`), `files/shaders/lib/sky/passes.glsl` (`PASS_CLOUDS`), `files/settings-default.cfg` `[Models] skyclouds`, `files/openmw.cfg` Clear cloud/fog fallbacks. No other-LLM prompts: those paths are read from the pin.

## Goal

Same Path B viewer. Phase 24 atmosphere stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Exteriors get OpenMW’s day cloud dome** (`meshes/sky_clouds_01.nif`) over the atmosphere: Clear `Tx_Sky_Clear.dds`, UV scroll, horizon fade, drawn into the water reflection. Looking up from the docks shows drifting clouds on the blue dome. Sun, moons, stars, weather swaps, and rain stay out.

## Why this slice

Atmosphere alone is a flat blue. OpenMW instances `skyclouds` in the same early bin (`RenderBin_Sky`, camera-relative, depth write off) after the atmosphere. That is one nif + one weather texture + a texcoord scroll + vertex alpha. Weather blend (`mNextCloudMesh`) and storm yaw are extra.

Smallest clouds: freeze **Clear day**, one mesh, scroll it. Skip the sun.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. **Do not commit** `sky_clouds_01.nif` or `Tx_Sky_Clear.dds` (Bethesda); load through VFS/`testdata/` like other meshes.

- Load `Settings::models().mSkyclouds` default **`meshes/sky_clouds_01.nif`**. Same camera-relative root as the atmosphere (zero view translation, nif −90° X / `build(true)`). Add it under the early sky, **after** the atmosphere. Never `ModelBatch`.
- Same draw state as Phase 24 sky: blend on, **depth write off**, fog **disabled** (OpenMW `GL_FOG OFF` on the early bin), clip plane off. Still draw into the **reflection** RTT; skip refraction; hide on interiors.
- Override the nif’s texture with Clear **`Weather_Clear_Cloud_Texture`** `Tx_Sky_Clear.dds` via `correctTexturePath` (VFS `textures/tx_sky_clear.dds`). Wrap **REPEAT**. `CloudUpdater` does this every apply; the nif does not ship that weather texture.
- Shader `pass == PASS_CLOUDS` (**2**). `paintClouds`: sample diffuse, `color.a *= passColor.a * opacity`, `color.xyz = clamp(tex.xyz * emission, 0, 1)`, then `mix(fogColor, color, passColor.a)` so the rim eases into fog. Opacity **1** (no next-cloud blend).
- Emission is **not** the sky colour. `setWeather` uses `weather.mFogColor + (0.13, 0.13, 0.13, 0)`. Freeze Clear day fog **`Weather_Clear_Fog_Day_Color` 206,227,255** / 255, then add 0.13. The mix fog colour is that same fog rgb (OpenMW still writes `gl_Fog.color` even with `GL_FOG` off).
- `ModVertexAlphaVisitor` Clouds on the 65-vert cylinder (`debugCli` dump: `Tri sky_clouds_01` verts=65 tris=112). Per-vertex colour `(0,0,0,alpha)`:
  - `i >= 49 && i <= 64` → **0** (bottom row)
  - `i >= 33 && i <= 48` → **0.25098** (second row, 64/255)
  - else → **1**
- UV scroll: `sky.vert` uses `(gl_TextureMatrix[0] * uv)` for clouds. `CloudUpdater::setTextureCoord` is `translate(0, timer, 0)` so **add timer to `uv.y`**. `onFrame`: `timer += dt * mCloudSpeed / 400`, wrap at **4**. Freeze `Weather_Clear_Cloud_Speed` **1.25**. `Weather_Timescale_Clouds` is **0** — do not scale by game time.
- One cloud mesh only. Do **not** instance `mNextCloudMesh`, do not rotate for storms (`Weather::defaultDirection` is identity vs Clear `mStormDirection`).

```bat
gradlew.bat :core:debugCli --args="nif meshes/sky_clouds_01.nif"
```

## Out of scope

- Sun / sunglare / sunflash
- Moons, night stars (`sky_night_01/02`)
- Weather changes, next-cloud blend, storm yaw, ash/blight
- Rain / snow / sky RTT postfx
- Interior sky
- Walk-recenter
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Look up: blue atmosphere **plus** scrolling Clear clouds, faded at the horizon. Harbor water **reflects those clouds**. Beaches stay dry.

**Ctrl** underwater: still murky. **Cell** / **Cave** / **Guild**: no sky, chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="nif meshes/sky_clouds_01.nif"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town sky has drifting Clear clouds over the Phase 24 dome, faded at the rim.
- Water reflection shows clouds + land. Interiors have no sky.
- Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Sun, moons, stars, or weather swaps this phase is a **fail**. Atmosphere-only (no clouds) this phase is a **fail**.

## Other-LLM claims

None. Pin excerpts above are enough.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `SkyManager::create` clouds | Path B cloud nif on sky root | rewrite |
| `CloudUpdater` | cloud tex / emission / UV timer | rewrite |
| `sky.frag` `paintClouds` | sky pass 2, tex × emission, fog mix | rewrite |
| `ModVertexAlphaVisitor` Clouds | 65-vert row alphas | rewrite |

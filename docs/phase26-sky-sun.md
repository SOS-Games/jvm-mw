# Phase 26: Day sun disc

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/skyutil.cpp` (`CelestialBody` / `Sun` / `createTexturedQuad` / `SunUpdater`), `apps/openmw/mwrender/sky.cpp` (`SkyManager::create` sun / `setWeather` disc colour), `apps/openmw/mwrender/renderingmanager.cpp` (`setSunDirection`), `apps/openmw/mwworld/weather.cpp` (orbit `sunDir`), `files/shaders/compatibility/sky.frag` (`paintSun`), `files/shaders/lib/sky/passes.glsl` (`PASS_SUN`), `apps/openmw/mwrender/vismask.hpp` (`Mask_Sun`), `apps/openmw/mwrender/water.cpp` (`Reflection::calcNodeMask`). No other-LLM prompts: those paths are read from the pin.

## Goal

Same Path B viewer. Phase 25 clouds stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Exteriors get OpenMW’s day sun disc** (`textures/tx_sun_05.dds` on a camera-relative quad). Looking up from the docks shows a sun in the Clear-day sky, with clouds able to pass over it. Sunglare, sunflash, occlusion queries, moons, stars, and a clock stay out.

## Why this slice

Clouds without a sun still look like a painted lid. OpenMW’s disc is one textured quad at distance 1000, not a nif. Glare/flash are extra nodes, bins, and queries. Smallest sun: freeze **Clear midday**, draw that disc in the early sky, skip glare.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. **Do not commit** `tx_sun_05.dds` (Bethesda); load through VFS/`testdata/` like other textures.

- `CelestialBody` quad: verts `(-0.5,-0.5,0)` … `(0.5,-0.5,0)`, UVs `(0,0),(0,1),(1,1),(1,0)` (OpenMW “Y-down” comment). Scale **450**. Distance **1000**. Never `ModelBatch`.
- Texture **`textures/tx_sun_05.dds`**, wrap **CLAMP_TO_EDGE**. Shader `pass == PASS_SUN` (**4**): `paintSun` is `texture2D` then `color.a *= gl_FrontMaterial.diffuse.a`. Freeze midday: disc rgb **(1,1,1)**, alpha **1** (`mSunDiscColor` before sunset lerp; `Weather_Clear_Glare_View` **1**).
- Place it in the same camera-relative TES3 space as the atmosphere (zero view translation, then −90° X to GL). OpenMW `WeatherManager` midday orbit 0 → `sunDir (-400*orbit, 75, -100)` = `(0, 75, -100)`. `RenderingManager::setSunDirection` does `position = -direction` then **`position.z = 400 - abs(position.x)`**, so the disc direction is TES3 **`(0, -75, 400)`**. `Sun::setDirection` normalizes that, puts the transform at `dir * 1000`, and `makeRotate((0,0,1), dir)` so the quad faces along it. Cull off (sky blend, no back-face surprise).
- Draw in the early sky bin **after atmosphere, before clouds** (OpenMW `create()` order: atmosphere, sun, moons, clouds). Blend on, **depth write off**, fog off, clip off — same as Phase 24/25.
- **Do not** draw the disc into the reflection RTT. `Reflection::calcNodeMask` is `Mask_Scene | Mask_Sky | Mask_Lighting | extraMask`. The sun node is `Mask_Sun`, not that sky bit. Water already has its own sun specular. Skip refraction; hide on interiors.
- Do **not** retarget land lighting this slice (OpenMW `match sunlight to sun` default **false**).

## Out of scope

- Sunglare fullscreen quad / `RenderBin_SunGlare`
- Sunflash / occlusion queries / `PASS_SUNFLASH_QUERY`
- Time of day, sunrise/sunset disc colour, `setGlareTimeOfDayFade`
- Matching land `sunDir` to the disc (`match sunlight to sun`)
- Moons, night stars
- Weather changes, rain
- Interior sky
- Walk-recenter
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Look up: Clear sun disc in the sky (high, slightly south of zenith in TES3). Clouds can overlap it. Harbor water does **not** need a reflected disc. Beaches stay dry.

**Ctrl** underwater: still murky. **Cell** / **Cave** / **Guild**: no sun, chair HUD unfogged.

```bat
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town sky shows a sun disc over the Phase 25 clouds/atmosphere.
- Interiors have no sun. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Sunglare, moons, stars, or a clock this phase is a **fail**. No visible sun disc this phase is a **fail**.

## Other-LLM claims

None. Pin excerpts above are enough.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `CelestialBody` / `Sun` | Path B sun quad, camera-relative | rewrite |
| `createTexturedQuad` | 1×1 quad × 450 at distance 1000 | rewrite |
| `sky.frag` `paintSun` | sky pass 4, tex × disc alpha | rewrite |
| `RenderingManager::setSunDirection` | freeze midday TES3 `(0,-75,400)` | rewrite |

# Phase 27: Clear day/night clock

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwworld/weather.cpp` (`TimeOfDayInterpolator::getValue` / sun orbit / `getSunPercentage` / `mResult.mNight`), `apps/openmw/mwrender/renderingmanager.cpp` (`setSunDirection`), `apps/openmw/mwrender/sky.cpp` (`setWeather` disc / night node), `files/openmw.cfg` Clear colours and `Weather_*_Time`. No other-LLM prompts: those paths are read from the pin.

## Goal

Same Path B viewer. Phase 26 sun disc stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Exteriors get a Clear-weather hour** you can scrub. Sky colour, fog tint, land ambient/sun, and the sun disc follow OpenMW’s interpolator and orbit. Night brings `sky_night_01` stars. A HUD slider plus **[ ]** step the clock; **Play** runs it. Moons, other weathers, and sunglare stay out.

## Why this slice

A frozen midday is one screenshot. OpenMW already drives the sky from `gameHour` with no extra systems: four Clear colours, a sun arc, a night mesh. The tester needs a slider more than a hidden 30× timescale.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. **Do not commit** night nifs or star textures.

- Freeze **Clear**. Times from cfg: sunrise **6**, sunset **18**, durations **2** → `mNightEnd=6`, `mDayStart=8`, `mDayEnd=18`, `mNightStart=20`. Default hour **13** (orbit peak, same disc as Phase 26).
- `TimeOfDayInterpolator` for Sky / Fog / Ambient / Sun colours (Clear sunrise/day/sunset/night from `openmw.cfg`) and Stars fade `(0,0,0,1)` with the Stars pre/post times. Horizon / clear colour / cloud emission (`fog + 0.13`) use the Fog colour. Land **ambient** and **sunDiffuse** use Ambient and Sun. Exterior fog **stays off**; only the colour updates. Interiors ignore the clock.
- Sun orbit: `sunDir = (-400*orbit, 75, -100)` with day `orbit = 1-2t` / night `2t-1` as in `WeatherManager::update`. `setSunDirection`: `position = -dir`, then `position.z = 400 - abs(position.x)`. Disc alpha **0** when `getSunPercentage` is 0 (hour ≤ 6 or ≥ 20), else that percentage. Do not draw a midnight disc. Water specular uses OpenMW `glareFade` (`sunVis`) so the harbor glint dies with the sun; the night orbit must not keep a noon sparkle.
- Night mesh: `sky_night_02.nif` if VFS has it, else **`meshes/sky_night_01.nif`**. `PASS_ATMOSPHERE_NIGHT` **1**, `paintAtmosphereNight` (tex × vertex alpha × opacity). `ModVertexAlphaVisitor` Stars: alpha 1 iff original colour.x == 1. Show when `mNight` (`hour < 6` or `hour > 19`). Opacity `nightFade` (`Glare_View` 1). Same camera-relative / reflection as atmosphere. No star roll this slice.
- HUD: slider **0–24**, label `hour=…`. **[** / **]** step 0.25h (Shift = 2h). **Play** advances **1 game hour per real second**. Scroll wheel still dollies. Esc-unlock to drag the slider. F3 writes `hour=`.
- Cloud UV follows the hour (not wall-clock `dt`): `timer = hour * Weather_Clear_Cloud_Speed / 6`, wrap **4**, so a 24h scrub is a bit more than one wrap. Pause freezes the dome.

## Out of scope

- Moons / moon phases
- Cloudy–Blizzard and region weather
- Sunglare / sunflash / `match sunlight to sun`
- Morrowind 30× timescale as the default clock
- Interior sky
- Walk-recenter
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town**: default still looks like midday. Esc, drag the slider (or **[ ]**): sun moves, sky warms at dusk, land darkens, stars come out at night. **Play** runs a full day in ~24s. **Cell** / **Cave** / **Guild** ignore the clock.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Scrubbing hour changes sun, sky, clouds, and exterior lighting. Night has stars, not a day dome.
- Interiors unchanged. Chair HUD unfogged. `glError=0`.

Moons or weather swaps this phase is a **fail**. Clock with no visible change this phase is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `TimeOfDayInterpolator` | `render.ClearCycle` | rewrite |
| `WeatherManager` sun orbit / `%` | `ClearCycle` hour → disc + light | rewrite |
| `sky.frag` `paintAtmosphereNight` | sky pass 1 | rewrite |
| `ModVertexAlphaVisitor` Stars | `MeshGpu.applyStarsVertexAlpha` | rewrite |

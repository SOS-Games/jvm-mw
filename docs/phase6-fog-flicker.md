# Phase 6: interior fog + light flicker

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/fogmanager.cpp` (`configure`), `files/shaders/compatibility/fog.glsl` (`applyFogAtDist`), `files/settings-default.cfg` (`viewing distance`, `use distant fog`, `radial fog`, `exponential fog`), `components/sceneutil/lightcontroller.cpp`, `components/esm3/loadligh.hpp` flicker/pulse flags, `components/sceneutil/lightutil.cpp` (`createLightSource` sets controller type). Other-LLM checks of those three claims **held** (2026-09-18).

## Goal

Same viewer, same Census office. Phase 5 lights are static. This phase makes candles **breathe** (flicker/pulse from `LHDT` flags) and applies the cell’s **AMBI fog** so distance falls into Morrowind haze instead of a hard grey clear color.

Chair / other HUD meshes stay unfogged and unflickered (not a cell).

## Why this slice

Fog and flicker were the leftover lighting mood in Phase 5. They are small, same cell, same records we already parse. Actors (body parts, `baseanim`, skinning, `.kf`) are a new chapter — not this phase.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only. Same interior.

### Fog

Keep the rest of `AMBI` (we already store ambient + sunlight). Also keep **fog color** (`colourFromRGB` on the third `uint32`) and **fog density** (the following `float`).

OpenMW default `use distant fog = false`. Then `FogManager::configure(viewDistance, fogDensity, …)`:

- `viewDistance` is settings **`viewing distance = 7168`**, not our inflated `camera.far`.
- If `fogDensity == 0`: fog off (`start = 0`, `end = +inf` — skip mixing).
- Else: `fogStart = 7168 * (1 - fogDensity)`, `fogEnd = 7168`.
- Fog color = `colourFromRGB(AMBI.fog)`.

Shader (OpenMW defaults `radial fog = false`, `exponential fog = false`):

```
dist = abs(viewZ)          // view-space Z, not world length
fogValue = clamp((dist - fogStart) / (fogEnd - fogStart), 0, 1)
color = mix(color, fogColor, fogValue)
```

That matches `fog.glsl` linear branch when `gl_Fog.scale = 1/(end-start)`. Need `v_viewPos` (or clip/view Z) from the vertex shader.

Clear color for the cell should be the fog color so the void matches the haze. HUD mesh previews keep the Phase 2 grey clear.

Underwater fog / distant fog / sky blending: **out**.

### Flicker / pulse

Flags we already stored on `EsmObject.lightFlags` and logged as ignorable:

| Flag | Value | OpenMW type | Speed |
| --- | --- | --- | --- |
| Flicker | `0x008` | `LT_Flicker` | `0.1` |
| FlickerSlow | `0x040` | `LT_FlickerSlow` | `0.05` |
| Pulse | `0x080` | `LT_Pulse` | `0.1` |
| PulseSlow | `0x100` | `LT_PulseSlow` | `0.05` |

`createLightSource` assigns at most one type (if-chain: flicker, then flickerSlow, then pulse, then pulseSlow). Last matching if wins if several bits were set — copy that if-chain, do not OR them.

Each emitting light (including empty-MODL) gets a controller state:

- `phase` starts `0.25 + random[0,1] * 0.75`
- `brightness` starts `0.675`
- Simulation time in **seconds**. Vanilla updates at **15 FPS**:  
  `ticks = (dt * 15) * 0.25 + previousTicks * 0.75`
- If `brightness >= phase`, subtract `ticks * speed`, else add.
- When `|brightness - phase| < speed`:  
  - Flicker / FlickerSlow: `phase = 0.25 + random[0,1] * 0.75`  
  - Pulse / PulseSlow: `phase = (phase <= 0.5) ? 1 : 0.25`
- Upload `diffuse * brightness` (and specular, which for us is 0 on negative lights and unused in the shader). `actorFade` is 1.

Random is closed `[0,1]` (`rollClosedProbability`). Java `Random.nextFloat()` is enough; do not port `minstd_rand`.

Normal lights (`LT_Normal`) keep constant color.

Do this every frame while the cell is shown. Chair button must not run it.

HUD: optional `fog=` density. `lights=` unchanged.

## Out of scope

- `NPC_` / `CREA`, body parts, `baseanim`, skinning, `.kf`
- Distant fog, radial fog, exponential fog, underwater
- Light sounds, equipped lights, `minimum interior brightness`
- Lua, Bullet, MyGUI, plugins, terrain

## Test cell

`Seyda Neen, Census and Excise Office`. Phase 5 already logged `ignoreFlags=0x51` / `0x53` on most candles (those bits include flicker/pulse). Watch a candle for a few seconds: brightness should wander, not sit still. Walk to a far corner: haze toward the AMBI fog color.

## Pass / fail

- Candles visibly flicker or pulse; not a strobe, not frozen.
- Empty Flame Light / `dark_128` still emit; `dark_128` still negative.
- Distance haze uses the cell fog, not the Phase 2 grey (cell only).
- Chair HUD is still the old even light, no flicker, grey backdrop.
- `glError=0`. Loader / mouse lock unchanged.

Cave kit seams (Addamasartus walls / hide doors) are **not** this phase — [docs/phase7-cave-kit.md](phase7-cave-kit.md).

## Other-LLM claims (held 2026-09-18)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Classic interior fog range.** With distant fog off: `fogStart = viewDistance * (1 - density)`, `fogEnd = viewDistance`; density 0 disables fog. Color is `colourFromRGB` of `AMBI` fog. Default viewing distance 7168.
2. **Linear, non-radial fog mix.** Default radial/exponential off: `dist = abs(view Z)`, `fogValue = clamp((dist - start) * scale, 0, 1)` (`applyFogAtPos` uses `pos.z` as that linear dist). Mix toward fog color. Use `scale = 1/(end-start)` so the mix hits 1 at `fogEnd` (OpenGL fog scale). Not the additive-blend branch (`color *= 1 - fogValue`).
3. **Flicker controller.** Types and speeds as the table. 15 FPS smoothed ticks. Flicker retargets phase randomly in `[0.25, 1]`; pulse flips 0.25/1. Diffuse (and specular) scaled by brightness. `createLightSource` if-chain picks the type.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `MWRender::FogManager::configure` | cell fog start/end/color | rewrite |
| `applyFogAtDist` | `ForwardRenderer` GLSL | rewrite |
| `SceneUtil::LightController` | per-light brightness on `CellLight` | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: With use distant fog false, interior FogManager::configure(viewDistance,
fogDensity, ...) sets fogStart = viewDistance * (1 - fogDensity) and
fogEnd = viewDistance when fogDensity != 0. When fogDensity == 0, fog is
disabled (start 0, end +inf). Fog color is colourFromRGB of the cell mood
fog color (AMBI fog uint32). Default viewing distance in settings-default.cfg
is 7168.0. use distant fog defaults false.

From apps/openmw/mwrender/fogmanager.cpp configure(viewDistance, cell):
    osg::Vec4f color = SceneUtil::colourFromRGB(cell.getMood().mFogColor);
    const float fogDensity = cell.getMood().mFogDensity;
    if (Settings::fog().mUseDistantFog) { ... }
    else
        configure(viewDistance, fogDensity, mUnderwaterIndoorFog, 1.0f, 0.0f, color);

From the overload configure(viewDistance, fogDepth, ...):
    else {
        if (fogDepth == 0.0) {
            mLandFogStart = 0.0f;
            mLandFogEnd = std::numeric_limits<float>::max();
        } else {
            mLandFogStart = viewDistance * (1 - fogDepth);
            mLandFogEnd = viewDistance;
        }
        ...
        mFogColor = color;
    }

From files/settings-default.cfg:
    viewing distance = 7168.0
    use distant fog = false

From components/esm3/loadcell.hpp AMBIstruct:
    Color mAmbient, mSunlight, mFog; float mFogDensity;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Default radial fog and exponential fog are false. Then applyFogAtDist
uses dist = abs(linearDist) (not euclidean length), and
fogValue = clamp((dist - gl_Fog.start) * gl_Fog.scale, 0, 1). The color is
mix(color, fogColor, fogValue). applyFogAtPos passes length(pos) as
euclidean and pos.z as linearDist.

From files/settings-default.cfg:
    radial fog = false
    exponential fog = false

From files/shaders/compatibility/fog.glsl:
    vec4 applyFogAtDist(vec4 color, float euclideanDist, float linearDist, float far)
    {
    #if @radialFog
        float dist = euclideanDist;
    #else
        float dist = abs(linearDist);
    #endif
    #if @exponentialFog
        float fogValue = 1.0 - exp(-2.0 * max(0.0, dist - gl_Fog.start/2.0)
            / (gl_Fog.end - gl_Fog.start/2.0));
    #else
        float fogValue = clamp((dist - gl_Fog.start) * gl_Fog.scale, 0.0, 1.0);
    #endif
        color.xyz = mix(color.xyz, gl_Fog.color.xyz, fogValue);
        return color;
    }
    vec4 applyFogAtPos(vec4 color, vec3 pos, float far)
    {
        return applyFogAtDist(color, length(pos), pos.z, far);
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: LightController flicker/pulse scales the stored diffuse and specular
by a brightness that eases toward a phase. Updates are smoothed toward 15 FPS:
ticks = (timeDelta * 15) * 0.25 + oldTicks * 0.75. Speed is 0.1 for Flicker
and Pulse, 0.05 for the Slow variants. When |brightness-phase| < speed,
Flicker/FlickerSlow pick a new phase 0.25 + rollClosedProbability()*0.75
(closed unit interval [0,1]); Pulse/PulseSlow set phase to 1 if phase<=0.5
else 0.25. Result uploaded is brightness * actorFade. createLightSource
assigns type with separate ifs (Flicker, FlickerSlow, Pulse, PulseSlow) so a
later matching flag overwrites an earlier one. Flags: Flicker 0x008,
FlickerSlow 0x040, Pulse 0x080, PulseSlow 0x100.

From components/sceneutil/lightcontroller.cpp operator():
    constexpr float updateRate = 15.f;
    mTicksToAdvance = (time - mStartTime - mLastTime) * updateRate * 0.25f
        + mTicksToAdvance * 0.75f;
    mLastTime = time - mStartTime;
    float speed = (mType == LT_Flicker || mType == LT_Pulse) ? 0.1f : 0.05f;
    if (mBrightness >= mPhase)
        mBrightness -= mTicksToAdvance * speed;
    else
        mBrightness += mTicksToAdvance * speed;
    if (std::abs(mBrightness - mPhase) < speed) {
        if (mType == LT_Flicker || mType == LT_FlickerSlow)
            mPhase = 0.25f + Misc::Rng::rollClosedProbability() * 0.75f;
        else
            mPhase = mPhase <= 0.5f ? 1.f : 0.25f;
    }
    const float result = mBrightness * node->getActorFade();
    light->setDiffuse(mDiffuseColor * result);
    light->setSpecular(mSpecularColor * result);

From components/misc/rng.hpp:
    /// return value in range [0.0f, 1.0f]  <- note closed upper range.
    float rollClosedProbability(...);

From components/sceneutil/lightcontroller.cpp ctor:
    mPhase(0.25f + Misc::Rng::rollClosedProbability() * 0.75f)
    mBrightness(0.675f)

From components/sceneutil/lightutil.cpp createLightSource:
    if (esmLight.mFlicker)
        ctrl->setType(LT_Flicker);
    if (esmLight.mFlickerSlow)
        ctrl->setType(LT_FlickerSlow);
    if (esmLight.mPulse)
        ctrl->setType(LT_Pulse);
    if (esmLight.mPulseSlow)
        ctrl->setType(LT_PulseSlow);

From components/esm3/loadligh.hpp Flags:
    Flicker = 0x008, FlickerSlow = 0x040, Pulse = 0x080, PulseSlow = 0x100

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

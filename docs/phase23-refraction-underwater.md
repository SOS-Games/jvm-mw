# Phase 23: Refraction and underwater fog

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/water.cpp` (`Refraction` / `updateWaterMaterial` / `ShaderWaterStateSetUpdater` / `isUnderwater`), `files/shaders/compatibility/water.frag` (`@waterRefraction` path), `files/shaders/lib/core/fragment.glsl` (`sampleRefractionMap` / `sampleRefractionDepthMap`), `files/shaders/lib/view/depth.glsl` (`linearizeDepth`), `apps/openmw/mwrender/fogmanager.cpp`, `apps/openmw/mwrender/renderingmanager.cpp` (`update` fog from `isUnderwater`), `files/settings-default.cfg` `[Water]`, `files/openmw.cfg` `Water_Underwater*` fallbacks. Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same Path B viewer. Phase 22 reflection water stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Exterior water gets OpenMW refraction (second RTT) and underwater fog.** From above, the harbor is a window onto the seafloor, not a blended sheet. Dive under −1 and the world goes murky; looking toward the surface shows the above-water scene in the water shader, not dry air. No rain ripples, sky, interior water, or walk-recenter.

## Why this slice

Phase 22 is reflection-only (`refraction = false` in OpenMW defaults). That already looks wavy from the docks. It does **not** match a dive: the plane stays a blend, there is no seafloor-through-water, and below the plane the air fog stays off so it does not feel underwater.

OpenMW’s shader path builds `Refraction` only when `Settings::water().mRefraction`. The fragment then mixes `sampleRefractionMap` with the reflection (`alpha = 1`). FogManager swaps to underwater start/end/color whenever `Water::isUnderwater` (camera Z below `mTop`). Those two are what a Town dive is missing. Skip wobbly shores and sunlight scattering (extra `#if`s on the same path).

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Keep Phase 22 mesh pose (height −1, 3×3, cell-root −90° X), reflection 512 RTT, `water_nm`, six normal samples, fresnel, specular. Never `ModelBatch`.
- When shader water is on, also construct `Refraction(rttSize)` (`rtt size` **512**). **Do** set `waterRefraction` on. `refraction scale` default **1.0** → extra view is identity; clip only.
- Refraction camera (OpenMW TES3 Z-up): view `scale(1,1,refractionScale) * translate(0,0,(1-scale)*waterLevel)`, clip plane `(0,0,-1)` at water height −1 (keep **z ≤ −1**). Fog off on that pass. Front face stays the unflipped winding (no `isReflection`). Do not draw the water mesh into this RTT. Draw terrain + opaque + alpha-test (same as the reflection’s land/objects, including submerged bits). Map the clip through the same −90° X: keep GL **y ≤ −1**.
- Refraction needs a **color texture and a depth texture** (depth is sampled; Phase 22’s reflection depth renderbuffer is not enough here). Bind `refractionMap` unit **2**, `refractionDepthMap` unit **3**.
- Water shader **with** refraction: OpenMW turns **blend off**, default bin, **depth write on**. `gl_FragData[0] = mix(refraction, reflection, fresnel)` with **alpha 1**. `sampleRefractionMap(screenCoords - normal.xy * offset)` after bump suppress from linearized refraction depth (`BUMP_SUPPRESS_DEPTH` 300, `REFR_BUMP` 0.07, `VISIBILITY` 2500, `DEPTH_FADE` 0.15). If camera TES3 z `< 0`, `refraction = clamp(refraction * 1.5, 0, 1)`; else mix toward `WATER_COLOR` by the depth factor in `water.frag`. Log-depth from Phase 20 stays; invert it when emulating `linearizeDepth` so shore bump-suppress still works.
- `@sunlightScattering` **off**, `@wobblyShores` **off** this slice. `rainIntensity = 0`.
- Underwater fog when the camera is under the plane: `Water::isUnderwater` is `pos.z < mTop` (TES3 z; Path B: GL `cam.position.y < -1`) and water is enabled. Then use FogManager underwater start/end/color even on exteriors (land fog stays **off** above water).
  - Color: `Water_UnderwaterColor` **(12, 30, 37) / 255** mixed with the cell fog color by `Water_UnderwaterColorWeight` **0.85**.
  - Range (no distant fog): `start = min(viewDistance, 7168) * (1 - underwaterFog)`, `end = min(viewDistance, 7168)`, `viewDistance` 7168. No weather yet: use **day** `Water_UnderwaterDayFog` **2.5** (not sunrise/sunset/night).
  - Apply that fog to the object shader **and** the water shader while underwater. Interiors still have no water, so a Town dive is the check; **Cell** / **Cave** / **Guild** stay as they are (interior fog unchanged).
- Debug CLI `exterior -2 -9`: keep `water=-1`. F3 may log `underwater=`.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- `@sunlightScattering` / `@wobblyShores` (cfg defaults true; skip anyway)
- Rain ripples / `RippleSimulation`
- Sky in reflection or refraction
- Interior `HasWater` / indoor underwater fog (`Water_UnderwaterIndoorFog`)
- Weather time-of-day underwater fog keys
- Walk-recenter
- Swimming physics / water walking
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. From the docks, harbor water still reflects land, but you **see the seafloor / submerged posts through it** (refraction), not Phase 22’s blended sheet. Beaches above −1 stay dry. Lily pads still sit on the plane.

**Ctrl** down under the waterline: the 3×3 goes murky green-blue (underwater fog). Looking up at the surface is a water window (refraction + reflection), not clear air with a tinted quad. **Space** back above −1 restores unfogged exterior air.

**Cell** / **Cave** / **Guild** interiors unchanged (no water). Chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town harbor is see-through to the bottom and still reflects docks/land.
- Diving below −1 applies underwater fog; above −1 does not.
- Interiors unchanged. Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Rain ripples, sky, wobbly shores, or sunlight shafts this phase is a **fail**. Still-blended no-refract water (Phase 22 look) is a **fail**. No murk when submerged is a **fail**.

## Other-LLM claims

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Refraction RTT / stateset.** `updateWaterMaterial` constructs `Refraction(rttSize)` only if `mRefraction`. `setWaterLevel` uses scale/translate from `refractionScale` and clip `(0,0,-1)` at water. With refraction: maps on units 2 and 3, default render bin, no blend-on / no depth-write-off. Define `waterRefraction` is `"1"` iff refraction exists. Defaults: refraction false, scale 1.0, rtt 512.
2. **Refraction fragment.** Depth-aware bump suppress, `sampleRefractionMap` at screen UV minus offset, underwater `* 1.5`, above-water mix toward `WATER_COLOR`, output `mix(refraction, reflection, fresnel)` with alpha 1.
3. **Underwater fog.** `isUnderwater` is `pos.z < mTop` (and water on). FogManager underwater color is the fallback mix; start/end use `min(viewDistance, 7168)` and `(1 - underwaterFog)`. Day fog 2.5, color 12,30,37, weight 0.85.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Water::Refraction` | second Path B RTT, 512, clip keep below | rewrite |
| `water.frag` `@waterRefraction` | water fragment mix refraction | rewrite |
| `Water::isUnderwater` | camera TES3 z `<` −1 | rewrite |
| `FogManager` underwater | exterior fog while submerged | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Water::updateWaterMaterial, when Settings::water().mShader is
true, constructs Reflection then, only if Settings::water().mRefraction,
constructs Refraction(rttSize) with rttSize from Settings::water().mRttSize,
setWaterLevel(mTop), setScene(mSceneRoot), and adds it to mParent.
Refraction::setWaterLevel(waterLevel) sets
mViewMatrix = scale(1,1,refractionScale) * translate(0,0,(1-refractionScale)*waterLevel)
with refractionScale from Settings::water().mRefractionScale, and clip
plane (0,0,-1) through (0,0,waterLevel). ShaderWaterStateSetUpdater
setDefaults: if mRefraction, refractionMap unit 2, refractionDepthMap
unit 3, RenderBin_Default; else GL_BLEND on, RenderBin_Water, depth
writeMask false. createShaderWaterStateSet sets defineMap
waterRefraction to "1" if mRefraction else "0". Default settings:
refraction = false, rtt size = 512, refraction scale = 1.0.

From apps/openmw/mwrender/water.cpp updateWaterMaterial:
    if (Settings::water().mShader) {
        rttSize = Settings::water().mRttSize;
        mReflection = new Reflection(rttSize, mInterior);
        ...
        if (Settings::water().mRefraction) {
            mRefraction = new Refraction(rttSize);
            mRefraction->setWaterLevel(mTop);
            mRefraction->setScene(mSceneRoot);
            mParent->addChild(mRefraction);
        }
        ...
    }

From Refraction::setWaterLevel(waterLevel):
    refractionScale = Settings::water().mRefractionScale;
    mViewMatrix = osg::Matrix::scale(1, 1, refractionScale)
        * osg::Matrix::translate(0, 0, (1.0 - refractionScale) * waterLevel);
    mClipCullNode->setPlane(osg::Plane(osg::Vec3d(0, 0, -1),
        osg::Vec3d(0, 0, waterLevel)));

From ShaderWaterStateSetUpdater::setDefaults:
    if (mRefraction) {
        refractionMap unit 2; refractionDepthMap unit 3;
        RenderBin_Default;
    } else {
        GL_BLEND ON; RenderBin_Water; Depth writeMask false;
    }

From Water::createShaderWaterStateSet:
    defineMap["waterRefraction"] = std::string(mRefraction ? "1" : "0");

From files/settings-default.cfg [Water]:
    rtt size = 512
    refraction = false
    refraction scale = 1.0

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: When @waterRefraction is set, water.frag computes
realWaterDepth from linearized refraction depth minus surface depth,
scales screenCoordsOffset (normal.xy * REFL_BUMP) by
clamp(realWaterDepth / BUMP_SUPPRESS_DEPTH, 0, 1) with
BUMP_SUPPRESS_DEPTH 300. It samples refraction =
sampleRefractionMap(screenCoords - screenCoordsOffset).rgb. If
cameraPos.z < 0, refraction = clamp(refraction * 1.5, 0, 1); else it
mixes refraction toward waterColor using VISIBILITY 2500 and
DEPTH_FADE 0.15. Then gl_FragData[0].rgb =
mix(refraction, reflection, fresnel) and .a = 1.0.
sampleRefractionMap is texture2D(refractionMap, uv). REFR_BUMP is 0.07.

From files/shaders/compatibility/water.frag:
    const float VISIBILITY = 2500.0;
    const float DEPTH_FADE = 0.15;
    const float REFL_BUMP = 0.10;
    const float REFR_BUMP = 0.07;
    const float BUMP_SUPPRESS_DEPTH = 300.0;
    screenCoordsOffset = normal.xy * REFL_BUMP;
    #if @waterRefraction
        realWaterDepth = linearizeDepth(sampleRefractionDepthMap(screenCoords), near, far)
            - linearizeDepth(gl_FragCoord.z, near, far);
        screenCoordsOffset *= clamp(realWaterDepth / BUMP_SUPPRESS_DEPTH, 0.0, 1.0);
    #endif
    reflection = sampleReflectionMap(screenCoords + screenCoordsOffset).rgb;
    #if @waterRefraction
        refraction = sampleRefractionMap(screenCoords - screenCoordsOffset).rgb;
        if (cameraPos.z < 0.0)
            refraction = clamp(refraction * 1.5, 0.0, 1.0);
        else {
            depthCorrection = sqrt(1.0 + 4.0 * DEPTH_FADE * DEPTH_FADE);
            factor = DEPTH_FADE * DEPTH_FADE / (-0.5 * depthCorrection + 0.5
                - waterDepthDistorted / VISIBILITY) + 0.5 * depthCorrection + 0.5;
            refraction = mix(refraction, waterColor, clamp(factor, 0.0, 1.0));
        }
        gl_FragData[0].rgb = mix(refraction, reflection, fresnel);
        gl_FragData[0].a = 1.0;
    #endif

From files/shaders/lib/core/fragment.glsl:
    vec4 sampleRefractionMap(vec2 uv) { return texture2D(refractionMap, uv); }
    float sampleRefractionDepthMap(vec2 uv) { return texture2D(refractionDepthMap, uv).x; }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Water::isUnderwater(pos) returns pos.z() < mTop && mToggled &&
mEnabled. RenderingManager::update sets isUnderwater from
mWater->isUnderwater(mCamera->getPosition()) and reads fog start, end,
and color from FogManager using that flag. FogManager::getFogColor
when underwater is mUnderwaterColor * mUnderwaterWeight + mFogColor *
(1 - mUnderwaterWeight). Fallbacks: Water_UnderwaterColor 012,030,037
(each / 255), Water_UnderwaterColorWeight 0.85,
Water_UnderwaterDayFog 2.5. FogManager constructor loads those color
and weight fallbacks. configure without distant fog sets
mUnderwaterFogStart = min(viewDistance, 7168) * (1 - underwaterFog)
and mUnderwaterFogEnd = min(viewDistance, 7168). getColour splits on
comma and divides by 255. viewing distance default is 7168.0.

From apps/openmw/mwrender/water.cpp:
    bool Water::isUnderwater(const osg::Vec3f& pos) const {
        return pos.z() < mTop && mToggled && mEnabled;
    }

From apps/openmw/mwrender/renderingmanager.cpp update:
    bool isUnderwater = mWater->isUnderwater(mCamera->getPosition());
    float fogStart = mFog->getFogStart(isUnderwater);
    float fogEnd = mFog->getFogEnd(isUnderwater);
    osg::Vec4f fogColor = mFog->getFogColor(isUnderwater);

From apps/openmw/mwrender/fogmanager.cpp:
    mUnderwaterColor(Fallback::Map::getColour("Water_UnderwaterColor"))
    mUnderwaterWeight(Fallback::Map::getFloat("Water_UnderwaterColorWeight"))
    getFogColor(isUnderwater): if underwater
        return mUnderwaterColor * mUnderwaterWeight + mFogColor * (1.f - mUnderwaterWeight);
    configure(...):
        mUnderwaterFogStart = std::min(viewDistance, 7168.f) * (1 - underwaterFog);
        mUnderwaterFogEnd = std::min(viewDistance, 7168.f);

From components/fallback/fallback.cpp getColour:
    split on ","; r,g,b / 255.0f

From files/openmw.cfg:
    fallback=Water_UnderwaterDayFog,2.5
    fallback=Water_UnderwaterColor,012,030,037
    fallback=Water_UnderwaterColorWeight,0.85

From files/settings-default.cfg:
    viewing distance = 7168.0

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

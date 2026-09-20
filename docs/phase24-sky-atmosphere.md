# Phase 24: Day atmosphere sky

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/sky.cpp` (`SkyManager` ctor / `create` / `setWeather` sky colour), `apps/openmw/mwrender/skyutil.cpp` (`CameraRelativeTransform` / `AtmosphereUpdater` / `ModVertexAlphaVisitor` Atmosphere), `apps/openmw/mwrender/renderbin.hpp` (`RenderBin_Sky`), `files/shaders/compatibility/sky.frag` (`paintAtmosphere`), `files/shaders/lib/sky/passes.glsl`, `files/settings-default.cfg` `[Models] skyatmosphere`, `files/openmw.cfg` `Weather_Clear_Sky_Day_Color`. Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 23 refraction water stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Exteriors get OpenMW’s day atmosphere dome** (`meshes/sky_atmosphere.nif`), camera-relative, Clear-day sky colour, drawn before the world and into the water reflection. Looking up from the docks is blue, not empty clear-color. Clouds, sun, moons, stars, weather, and rain stay out.

## Why this slice

Water reflections and the horizon still fall through to a dark clear. OpenMW always puts `Mask_Sky` in the reflection camera and draws the atmosphere in `RenderBin_Sky` (−1) with depth write off. That dome is one nif + emission colour + vertex alpha. Clouds/sun/night are extra meshes and passes.

Smallest sky: that day atmosphere on Town. Skip weather clocks.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. **Do not commit** `sky_atmosphere.nif` (Bethesda); load it through VFS/`testdata/` like other meshes.

- Load `Settings::models().mSkyatmosphere` default **`meshes/sky_atmosphere.nif`**. Instance under a camera-relative root, **not** the cell root’s world translation. `CameraRelativeTransform` (RELATIVE_RF) zeros the modelview translation so children sit at the eye. When drawing sky, use the current view with translation zeroed (also after the reflection view multiply). Keep the nif’s TES3 Z-up via the same −90° X as the cell, or equivalent. Never `ModelBatch`.
- Draw sky **first** (`RenderBin_Sky = -1`): blend on, **depth write off**, fog off, clip plane off (OpenMW turns `GL_CLIP_PLANE0` off so the water clip does not slice the dome). Then terrain / objects / water as now.
- Atmosphere shader (`pass == PASS_ATMOSPHERE` which is **0**): `paintAtmosphere` sets `color = gl_FrontMaterial.emission` and `color.a *= passColor.a`. Unlit. `AtmosphereUpdater` sets emission to the sky colour.
- No weather yet: freeze **Clear day** `Weather_Clear_Sky_Day_Color` **095,135,203** (each / 255).
- `ModVertexAlphaVisitor` Atmosphere: the mesh is a cylinder; **every second vertex** alpha 0, others 1 (`(i % 2) ? 0 : 1`). Write that into vertex colour alpha so the bottom rim fades into the fog/horizon.
- Include the atmosphere in the **reflection** RTT (OpenMW `calcNodeMask` always ORs `Mask_Sky`). Skip it in the **refraction** RTT (underwater clip; sky is not in the refraction default story for this slice — refraction already draws submerged land). Hide sky on **interiors** (no Town sky in Census/Cave/Guild).
- Log-depth from Phase 20 stays. Water unchanged aside from now reflecting the dome.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- Clouds (`sky_clouds_01.nif` / `CloudUpdater` / UV scroll)
- Sun / sunglare / sunflash
- Moons, night stars (`sky_night_01/02`)
- Weather changes, storms, ash/blight clouds
- Rain / snow particles / sky RTT postfx
- Interior sky
- Walk-recenter
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Look up: blue atmosphere dome, bottom faded, not a flat clear colour. Harbor water **reflects that blue** with the existing land reflection. Beaches stay dry. Lily pads stay.

**Ctrl** underwater: still murky (Phase 23). **Cell** / **Cave** / **Guild**: no sky, chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town sky is a blue dome (Clear day colour), faded at the horizon.
- Water reflection shows that blue plus land. Interiors have no sky.
- Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Clouds, sun, moons, stars, or weather this phase is a **fail**. Still-empty horizon/reflection this phase is a **fail**.

## Other-LLM claims

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Sky root / atmosphere nif / draw state.** `SkyManager` puts children under `CameraRelativeTransform`, early bin `RenderBin_Sky` (−1), clip plane off. `create()` instances `skyatmosphere`. Depth write false, blend on, fog off. Camera-relative RELATIVE_RF zeros modelview translation.
2. **Atmosphere colour / shader.** `AtmosphereUpdater` pass Atmosphere (0), unlit alpha-tracking material, apply writes emission. `paintAtmosphere` uses emission rgb and vertex alpha. Clear day sky colour 095,135,203 / 255.
3. **Vertex alpha / reflection mask.** Atmosphere visitor: even/odd cylinder vertices alpha 1 vs 0. Reflection `calcNodeMask` always includes `Mask_Sky`. Default mesh path `meshes/sky_atmosphere.nif`.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `SkyManager::create` atmosphere | sky nif, camera-relative | rewrite |
| `CameraRelativeTransform` | zero view translation when drawing sky | rewrite |
| `sky.frag` `paintAtmosphere` | sky pass, emission × vertex alpha | rewrite |
| `ModVertexAlphaVisitor` Atmosphere | cylinder even/odd alpha | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: SkyManager constructs mSkyRootNode as a CameraRelativeTransform
parented to parentNode. mEarlyRenderBinRoot uses RenderBin_Sky and
sets GL_CLIP_PLANE0 OFF. mSkyNode with Mask_Sky holds the early bin
and is added to mSkyRootNode. SkyManager::create instances
Settings::models().mSkyatmosphere into mEarlyRenderBinRoot, then
ModVertexAlphaVisitor Atmosphere. Early bin stateset: sky program,
depth writeMask false, GL_BLEND ON, GL_FOG OFF. RenderBin_Sky equals
-1. CameraRelativeTransform::computeLocalToWorldMatrix, when
RELATIVE_RF, does matrix.setTrans(0,0,0) and returns false. Default
skyatmosphere is meshes/sky_atmosphere.nif.

From apps/openmw/mwrender/sky.cpp SkyManager ctor:
    mSkyRootNode = new CameraRelativeTransform;
    parentNode->addChild(mSkyRootNode);
    mEarlyRenderBinRoot->getOrCreateStateSet()->setRenderBinDetails(
        RenderBin_Sky, "RenderBin");
    mEarlyRenderBinRoot->getOrCreateStateSet()->setMode(
        GL_CLIP_PLANE0, osg::StateAttribute::OFF);
    mSkyNode->setNodeMask(Mask_Sky);
    mSkyNode->addChild(mEarlyRenderBinRoot);
    mSkyRootNode->addChild(mSkyNode);

From SkyManager::create:
    mAtmosphereDay = mSceneManager->getInstance(
        Settings::models().mSkyatmosphere.get(), mEarlyRenderBinRoot);
    ModVertexAlphaVisitor modAtmosphere(ModVertexAlphaVisitor::Atmosphere);
    mAtmosphereDay->accept(modAtmosphere);
    depth->setWriteMask(false);
    mEarlyRenderBinRoot ... GL_BLEND ON; GL_FOG OFF;

From apps/openmw/mwrender/renderbin.hpp:
    RenderBin_Sky = -1

From CameraRelativeTransform::computeLocalToWorldMatrix:
    if (_referenceFrame == RELATIVE_RF) {
        matrix.setTrans(osg::Vec3f(0.f, 0.f, 0.f));
        return false;
    }

From files/settings-default.cfg [Models]:
    skyatmosphere = meshes/sky_atmosphere.nif

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: AtmosphereUpdater::setDefaults installs
createAlphaTrackingUnlitMaterial (unlit with ColorMode DIFFUSE) and
uniform pass = Pass::Atmosphere. apply() sets the material emission
FRONT_AND_BACK to mEmissionColor. SkyManager::setWeather, when
mSkyColour != weather.mSkyColor, assigns mSkyColour and
mAtmosphereUpdater->setEmissionColor(mSkyColour). paintAtmosphere
sets color = gl_FrontMaterial.emission and color.a *= passColor.a.
PASS_ATMOSPHERE is 0. Weather_Clear_Sky_Day_Color is 095,135,203.
getColour splits on comma and divides by 255.

From apps/openmw/mwrender/skyutil.cpp:
    createUnlitMaterial: diffuse/ambient 0, emission 1, ColorMode as given
    createAlphaTrackingUnlitMaterial: return createUnlitMaterial(DIFFUSE)
    AtmosphereUpdater::setDefaults:
        createAlphaTrackingUnlitMaterial OVERRIDE
        uniform pass = Pass::Atmosphere
    AtmosphereUpdater::apply:
        mat->setEmission(FRONT_AND_BACK, mEmissionColor)

From apps/openmw/mwrender/sky.cpp setWeather:
    if (mSkyColour != weather.mSkyColor) {
        mSkyColour = weather.mSkyColor;
        mAtmosphereUpdater->setEmissionColor(mSkyColour);
        ...
    }

From files/shaders/compatibility/sky.frag:
    void paintAtmosphere(inout vec4 color) {
        color = gl_FrontMaterial.emission;
        color.a *= passColor.a;
    }
    if (pass == PASS_ATMOSPHERE) paintAtmosphere(color);

From files/shaders/lib/sky/passes.glsl:
    #define PASS_ATMOSPHERE 0

From files/openmw.cfg:
    fallback=Weather_Clear_Sky_Day_Color,095,135,203

From components/fallback/fallback.cpp getColour:
    split on ","; r,g,b / 255.0f

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: ModVertexAlphaVisitor for Atmosphere writes per-vertex colour
(0,0,0,alpha) with alpha = (i % 2) ? 0 : 1 because "this is a
cylinder, so every second vertex belongs to the bottom-most row".
Reflection::calcNodeMask always returns Mask_Scene | Mask_Sky |
Mask_Lighting | extraMask (sky is not gated on reflection detail).
Water geometry uses Mask_Water, not that sky bit.

From apps/openmw/mwrender/skyutil.cpp ModVertexAlphaVisitor::apply:
    case Atmosphere:
        alpha = (i % 2) ? 0.f : 1.f;
        break;
    (*colors)[i] = osg::Vec4f(0.f, 0.f, 0.f, alpha);
    geometry.setColorArray(colors, BIND_PER_VERTEX);

From apps/openmw/mwrender/water.cpp Reflection::calcNodeMask:
    extraMask from reflectionDetail (terrain, static, ...)
    return Mask_Scene | Mask_Sky | Mask_Lighting | extraMask;

From Water::Water:
    mWaterGeom->setNodeMask(Mask_Water);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

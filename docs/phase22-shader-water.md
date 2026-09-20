# Phase 22: Shader water (reflection, no refraction)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/water.cpp` (`updateWaterMaterial` / `Reflection` / `createShaderWaterStateSet`), `files/shaders/compatibility/water.frag` (no-`@waterRefraction` path), `files/shaders/lib/water/fresnel.glsl`, `files/shaders/lib/core/fragment.glsl` (`sampleReflectionMap`), `files/settings-default.cfg` `[Water]`, `files/data/textures/omw/water_nm.png`. Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same Path B viewer. Phase 21 plane (TES3 height **−1**, 3×3, cell-root −90° X) stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load.

**Exterior water uses OpenMW shader water with a reflection RTT and `water_nm`, refraction off.** The tiled `water00`–`water31` flip goes away. No sky, rain ripples, underwater, or walk-recenter.

## Why this slice

Phase 21 is simple water: a blended flipbook. OpenMW shader water (`Settings::water().mShader`) always builds a **reflection** camera, binds `textures/omw/water_nm.png`, and runs `water.frag`. Refraction is a second RTT and is **off** in OpenMW’s defaults (`refraction = false`). That no-refraction path already looks like wavy water with reflected docks/land.

Smallest shader water: that path on the existing plane. Skip refraction, ripples, sky, interiors.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Keep Phase 21 mesh pose (height −1, cover 3×3, TES3 XY under cell-root −90° X). Stop the `water##.dds` flip.
- Copy OpenMW’s engine file `files/data/textures/omw/water_nm.png` into this repo’s `assets/textures/omw/water_nm.png` (GPLv3 OpenMW resource, **not** a Bethesda asset). Bind it repeating on the water shader. Never `ModelBatch`.
- When shader water is on, OpenMW always creates `Reflection(rttSize)` (`rtt size` default **512**) and `createShaderWaterStateSet`. Do that. **Do not** create refraction this slice (`refraction` default false).
- Reflection camera (OpenMW TES3 Z-up): view `scale(1,1,-1) * translate(0,0,2*waterLevel)`, clip plane `(0,0,1)` at water height −1, front face **clockwise**, uniform `isReflection = true`. Do not draw the water mesh into the RTT. Draw land + placed objects (OpenMW `reflection detail` default **2**: terrain + statics; we have no sky, so skip sky). Map that flip through the same −90° X as the main view.
- Water shader (no refraction): six `normalMap` samples (`normalCoords` / `WAVE_SCALE` 75, UV `worldPos.xy / (8192*5) * 3`), `fresnel_dielectric`, `sampleReflectionMap` at screen UV + `normal.xy * REFL_BUMP` (0.10). Color `mix(WATER_COLOR, reflection, (1+fresnel)*0.5)` with `WATER_COLOR = vec3(0.090195, 0.115685, 0.12745)`. Alpha `clamp(fresnel*6 + specular, 0, 1)`. Cull off, **blend on**, **depth write off**. `rainIntensity = 0`; dummy/black ripple map so `rippleAdd` is zero.
- Interiors: still no water. Exterior fog still off. Log-depth from Phase 20 stays.
- Debug CLI `exterior -2 -9`: keep `water=-1`.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- Refraction RTT / `waterRefraction` / wobbly shores / sunlight scattering (those `#if`s need refraction)
- Rain ripples / `RippleSimulation` / `Ripples` RTT
- Sky in the reflection (no sky yet)
- Interior `HasWater`
- Underwater fog / camera-under-water
- Walk-recenter
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Harbor water is wavy (normal map) and shows a **flipped reflection** of nearby docks/land, not the Phase 21 tiled flipbook. Beaches above −1 stay dry. Lily pads still sit on the plane.

**Cell** / **Cave** / **Guild** interiors unchanged (no water). Chair HUD unfogged.

```bat
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Town harbor water looks shader-wavy and reflects nearby land/buildings (even without sky).
- Phase 21 `water00`–`water31` tiling is gone.
- Interiors unchanged. Empty-`DNAM` and named dest still work. Chair HUD unfogged. `glError=0`.

Falling off **outside** the 3×3 is **expected**. Recenter-while-walking this phase is a **fail**. Refraction, rain ripples, or sky this phase is a **fail**. Still-tiled flipbook water is a **fail**.

## Other-LLM claims

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Shader vs simple / RTT.** `updateWaterMaterial` if `Settings::water().mShader`: construct `Reflection(rttSize)`, optionally `Refraction` only if `mRefraction`, then `createShaderWaterStateSet`. Else simple flipbook. Shader stateset: `normalMap` unit 0 from `textures/omw/water_nm.png`. No refraction → blend on, water bin, depth write false.
2. **Reflection camera.** `setWaterLevel`: view `scale(1,1,-1)*translate(0,0,2*waterLevel)`, clip `(0,0,1)` at waterLevel. Front face clockwise, `isReflection` true. Exterior `reflection detail` default 2 includes terrain and statics (sky always in the mask; we still skip drawing sky).
3. **No-refraction fragment.** UV `worldPos.xy/(8192*5)*3`, six normal-map layers, fresnel, `mix(WATER_COLOR, reflection, (1+fresnel)*0.5)`, alpha `waterTransparency`. `WATER_COLOR` is `(0.090195, 0.115685, 0.12745)`.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Water::updateWaterMaterial` shader | reflection FBO + water shader | rewrite |
| `Water::Reflection` | flipped Path B RTT, 512 | rewrite |
| `files/shaders/compatibility/water.frag` no refract | water fragment | rewrite |
| `textures/omw/water_nm.png` | `assets/textures/omw/water_nm.png` | same |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Water::updateWaterMaterial, when Settings::water().mShader is
true, constructs Reflection(rttSize) with rttSize from
Settings::water().mRttSize, setWaterLevel(mTop), setScene(mSceneRoot),
and adds it to mParent. It constructs Refraction only if
Settings::water().mRefraction. Then it calls
createShaderWaterStateSet(mWaterNode). If shader is false it calls
createSimpleWaterStateSet with Water_World_Alpha. Default settings:
shader false, rtt size 512, refraction false. createShaderWaterStateSet
loads textures/omw/water_nm.png as normalMap on unit 0. ShaderWaterStateSetUpdater
setDefaults: cull off; reflectionMap on unit 1; if no refraction,
GL_BLEND on, RenderBin_Water, depth write mask false.

From apps/openmw/mwrender/water.cpp updateWaterMaterial:
    if (Settings::water().mShader) {
        rttSize = Settings::water().mRttSize;
        mReflection = new Reflection(rttSize, mInterior);
        mReflection->setWaterLevel(mTop);
        mReflection->setScene(mSceneRoot);
        mParent->addChild(mReflection);
        if (Settings::water().mRefraction) { mRefraction = new Refraction(...); }
        createShaderWaterStateSet(mWaterNode);
    } else
        createSimpleWaterStateSet(mWaterGeom, Water_World_Alpha);

From createShaderWaterStateSet:
    constexpr VFS::Path::NormalizedView waterImage("textures/omw/water_nm.png");

From ShaderWaterStateSetUpdater::setDefaults:
    normalMap unit 0; GL_CULL_FACE OFF; reflectionMap unit 1;
    if (mRefraction) { refraction units; RenderBin_Default; }
    else {
        GL_BLEND ON;
        RenderBin_Water;
        Depth writeMask false;
    }

From files/settings-default.cfg [Water]:
    shader = false
    rtt size = 512
    refraction = false

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Reflection::setWaterLevel(waterLevel) sets
mViewMatrix = scale(1,1,-1) * translate(0,0,2*waterLevel) and clip
plane (0,0,1) through (0,0,waterLevel). setDefaults sets FrontFace
CLOCKWISE and uniform isReflection true. calcNodeMask always includes
Mask_Scene | Mask_Sky | Mask_Lighting; if reflectionDetail >= 1 also
Mask_Terrain; >= 2 also Mask_Static. settings-default reflection
detail = 2. Water geometry uses Mask_Water (not in that extra mask).

From apps/openmw/mwrender/water.cpp Reflection:
    setWaterLevel(waterLevel):
        mViewMatrix = osg::Matrix::scale(1, 1, -1)
            * osg::Matrix::translate(0, 0, 2 * waterLevel);
        mClipCullNode->setPlane(osg::Plane(osg::Vec3d(0, 0, 1),
            osg::Vec3d(0, 0, waterLevel)));
    setDefaults:
        uniform isReflection true;
        FrontFace CLOCKWISE;
    calcNodeMask:
        reflectionDetail = clamp(Settings::water().mReflectionDetail,
            mInterior ? 2 : 0, 5);
        extraMask = 0;
        if (>=1) extraMask |= Mask_Terrain;
        if (>=2) extraMask |= Mask_Static;
        ...
        return Mask_Scene | Mask_Sky | Mask_Lighting | extraMask;

From Water::Water:
    mWaterGeom->setNodeMask(Mask_Water);

From files/settings-default.cfg:
    reflection detail = 2

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: water.frag UV is worldPos.xy / (8192.0*5.0) * 3.0. It samples
normalMap six times via normalCoords (WAVE_SCALE 75). Fresnel uses
fresnel_dielectric. Reflection is sampleReflectionMap(screenCoords +
normal.xy * REFL_BUMP) with REFL_BUMP 0.10. When @waterRefraction is
0: gl_FragData[0].rgb = mix(waterColor, reflection, (1.0+fresnel)*0.5)
and .a = waterTransparency, where waterColor = WATER_COLOR * sunFade
and WATER_COLOR is vec3(0.090195, 0.115685, 0.12745), and
waterTransparency = clamp(fresnel * 6.0 + specular, 0.0, 1.0).
sampleReflectionMap is texture2D(reflectionMap, uv).

From files/shaders/compatibility/water.frag:
    const float REFL_BUMP = 0.10;
    const float WAVE_SCALE = 75.0;
    const vec3 WATER_COLOR = vec3(0.090195, 0.115685, 0.12745);
    UV = worldPos.xy / (8192.0*5.0) * 3.0;
    normal0..normal5 = 2.0 * texture2D(normalMap, normalCoords(...)).rgb - 1.0;
    fresnel = clamp(fresnel_dielectric(viewDir, normal, ior), 0.0, 1.0);
    reflection = sampleReflectionMap(screenCoords + screenCoordsOffset).rgb;
    waterTransparency = clamp(fresnel * 6.0 + specular, 0.0, 1.0);
    #else // !@waterRefraction
        gl_FragData[0].rgb = mix(waterColor, reflection, (1.0 + fresnel) * 0.5);
        gl_FragData[0].a = waterTransparency;
    #endif

From files/shaders/lib/core/fragment.glsl:
    vec4 sampleReflectionMap(vec2 uv) { return texture2D(reflectionMap, uv); }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

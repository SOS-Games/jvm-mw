# Phase 5: cell lights (point + interior AMBI)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/loadligh.cpp` (`LHDT`), `apps/openmw/mwclass/light.cpp` (`insertObjectRendering`), `apps/openmw/mwrender/animation.cpp` (`ObjectAnimation` / `addExtraLight`), `components/sceneutil/lightutil.cpp` (`addLight`, `createLightSource`, `configureLight`), `components/sceneutil/lightcommon.cpp`, `components/sceneutil/util.cpp` (`colourFromRGB`), `apps/openmw/mwrender/renderingmanager.cpp` (`configureAmbient`), `files/shaders/lib/light/lighting.glsl` + `lighting_util.glsl`, `files/openmw.cfg` LightAttenuation fallbacks, `files/settings-default.cfg` (`classic falloff`, `max lights`). Other-LLM checks of those three claims **held** (2026-09-18).

## Goal

Same Path B viewer, same Census office. Phase 4 drew candle **meshes** but the room is still one fake directional light. This phase makes `LIGH` refs emit, including empty-model lights (`Flame Light`, `dark_128`), and uses the cell’s `AMBI` instead of hardcoded `0.35`.

Walk in: desks near candles are warm; corners fall off; the office is no longer evenly lit.

Phase 1–4 HUD meshes and the Cell button still work. Chair previews keep the old single directional light (not a cell).

## Why this slice

Meshes are in. The remaining hole in this interior is lighting. OpenMW still inserts a light when `MODL` is empty; we skipped that on purpose in Phase 4. Actors, plugins, fog, and flicker can wait — they are not what makes this room look unlit.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only. Same interior as Phase 4.

**`LIGH` data** (keep on the object record, not just MODL):

- `LHDT` is 24 bytes, little-endian: `float weight`, `int32 value`, `int32 time`, `int32 radius`, `uint32 color`, `int32 flags`.
- Color via `colourFromRGB`: `r = (c >> 0) & 0xFF`, `g = (c >> 8) & 0xFF`, `b = (c >> 16) & 0xFF`, each `/ 255`. Alpha ignored. Specular = same RGB (unless negative).
- Radius: `max(lhdt.radius, 16)`.
- Flags we honor: `Negative = 0x004` (diffuse `*= -1`, specular zero), `OffDefault = 0x020` (**no** point light; mesh still draws if MODL is present).
- Flags we **log and ignore**: Flicker / FlickerSlow / Pulse / PulseSlow / Fire / Dynamic / Carry.

**Insert even if MODL is empty.** `Light::insertObjectRendering` always `insertModel`, and `allowLight = !(OffDefault)`. `ObjectAnimation` calls `addExtraLight` when the ptr is `ESM::Light` and `allowLight`. Empty mesh still gets an object root so the light has a transform.

**Where the light sits:** `addLight` parents to a descendant named `AttachLight` if present, else the object root. Use that node’s world translation after the usual cell-root −90° X. No second axis convert. If there is no mesh, the light is at the ref origin (`EsmTransforms` + cell root).

**Attenuation** (interior, OpenMW `openmw.cfg` fallbacks): `UseConstant=0`, `UseLinear=1`, `LinearMethod=1`, `LinearValue=3`, `LinearRadiusMult=1`, `UseQuadratic=0`. So `linear = 3 / radius`, `quadratic = 0`, `constant = 0`. Illumination:

```
1 / (constant + linear * dist + quadratic * dist * dist)
```

OpenMW default `classic falloff = false`: also multiply by `1 - quickstep(dist/radius - 1)` and skip the light if `dist > radius * 2`. `quickstep` is OpenMW’s smoothstep helper in `files/shaders/lib/util/quickstep.glsl` — copy it, do not invent a different fade.

**How many lights:** OpenMW default `max lights = 8` **per object**. For each mesh draw, pick up to 8 point lights whose positions are closest to that mesh (or its AABB center). Do not bind all 27 Census lights on every draw.

**Cell `AMBI`:** four fields, little-endian: `uint32 ambient`, `uint32 sunlight`, `uint32 fog`, `float fogDensity`. Colors through `colourFromRGB`.

- Replace hardcoded `u_ambientLight = 0.35` with the cell ambient RGB.
- Interior still has a directional “sun” from `AMBI` sunlight. OpenMW’s interior sun direction is the Morrowind nonsense vector `(-1, 45°, 45°)` then the light vector is the negation of that, in **OSG Z-up**. Apply the same cell-root −90° X so it matches our Y-up world. Do not keep Phase 2’s `(0.35, 0.8, 0.45)` in the cell.
- Skip OpenMW’s `minimum interior brightness` boost unless the office comes out implausibly black.
- Fog density / fog color: **out of scope** (clear color can stay the Phase 2 grey).

**Shader:** still Path B, owned GLSL, no ModelBatch. Point lights in **world space** (same space as `u_model`). Lambert like today’s directional term. Negative lights can darken (do not clamp each light to 0 before summing; OpenMW `clampLightingResult` is `max(lighting, 0)` when not the old clamp mode).

HUD: `lights=` count actually bound as sources (including empty-MODL). Status still has `placed=` from Phase 4.

Single-mesh HUD buttons: unchanged lighting.

## Coordinate trap

Light positions live in the **same world** as meshes. If you skip the cell-root −90° on lights, pools sit in the floor or on the wall. If you apply −90° a second time, they miss the candles.

| Node | Transform |
| --- | --- |
| Cell root | −90° around X |
| Light instance | ESM `makeOsgQuat` + `pos` (and uniform scale, unused for the light itself) |
| `AttachLight` | that node’s world translation |
| Empty-model light | ref origin only |

## Out of scope

- Flicker / pulse controllers (`LightController`)
- Light sounds (`SNAM`)
- Carryable / equipped lights on actors
- `minimum interior brightness` / `light fade start` / `maximum light distance` camera fade
- Real-time LightManager bounding spheres, UBO packing, shadows
- Fog from `AMBI`, water, weather sun
- Actors, plugins, Tribunal/Bloodmoon, Lua, Bullet, MyGUI
- Particle systems as actual particles (stubs stay)

## Test cell

`Seyda Neen, Census and Excise Office`. Phase 4 log: **23** `LIGH` meshes + **4** empty (`dark_128` + 3 `Flame Light`). Those 4 must now count as lights. Walk desks and the doorway.

## Pass / fail

- Candles / lamps pool light on nearby wood and paper; far walls are darker than Phase 4.
- Empty-model flame and `dark_128` affect the room (no new meshes).
- `OffDefault` lights (if any in this cell) do not emit.
- Chair HUD button is still the old directional look.
- `glError=0`. Mouse lock / cell loader unchanged.

## Other-LLM claims (held 2026-09-18)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Insert light even if MODL is empty; OffDefault kills the light, not the mesh.** `Light::insertObjectRendering` always `insertModel(ptr, model, !(OffDefault))`. `ObjectAnimation` adds `addExtraLight` iff the record is `ESM::Light` and `allowLight`. `LHDT` is required; `MODL` is not.
2. **Color, radius, attenuation, negative.** `colourFromRGB` is R in the low byte. Radius `max(mRadius, 16)`. Interior attenuation from `openmw.cfg` fallbacks: linear `3/radius`, no quadratic. Negative inverts diffuse and zeroes specular. `addLight` parents to `AttachLight` else the object root.
3. **Interior mood.** `configureAmbient` sets ambient and sunlight from cell `AMBI` via `colourFromRGB`. Interior sun direction is Morrowind’s fixed `(-1, 45°, 45°)` then negated. Point-light illumination is `1/(c + l*d + q*d*d)` times the non-classic falloff fade. Default `max lights = 8` per object, `classic falloff = false`.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::Light::LHDTstruct` | fields on `EsmObject` or a thin `EsmLight` | same |
| `SceneUtil::addLight` / `createLightSource` | cell light list + `AttachLight` lookup | rewrite |
| `SceneUtil::configureLight` | attenuation from OpenMW fallbacks | rewrite |
| `RenderingManager::configureAmbient` | cell `AMBI` → shader ambient + interior sun | rewrite |
| `lcalcIllumination` | `ForwardRenderer` GLSL | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts (or the same files in a local OpenMW checkout at that commit). Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: TES3 LIGH always gets a scene insert even when MODL is empty, so the
light source can exist. allowLight is false only when OffDefault is set;
that suppresses the light (and particles) but does not by itself skip the
mesh. LHDT is required data (weight, value, time, radius, packed color,
flags). MODL may be absent/empty. OffDefault is flag 0x020.

From components/esm3/loadligh.hpp:

    struct LHDTstruct {
        float mWeight;
        int32_t mValue;
        int32_t mTime;
        int32_t mRadius;
        uint32_t mColor;
        int32_t mFlags;
    }; // Size = 24 bytes
    enum Flags { ..., OffDefault = 0x020, ... };
    std::string mModel, ...;

From components/esm3/loadligh.cpp load(): NAME required; LHDT required
unless deleted; MODL optional string.

From apps/openmw/mwclass/light.cpp:

    void Light::insertObjectRendering(...) const {
        MWWorld::LiveCellRef<ESM::Light>* ref = ptr.get<ESM::Light>();
        // Insert even if model is empty, so that the light is added
        renderingInterface.getObjects().insertModel(
            ptr, model, !(ref->mBase->mData.mFlags & ESM::Light::OffDefault));
    }

From apps/openmw/mwrender/objects.hpp:
    /// @param allowLight If false, no lights will be created, and
    /// particles systems will be removed.

From apps/openmw/mwrender/animation.cpp ObjectAnimation:
    if (!model.empty()) { setObjectRoot(model, ...); ... }
    if (ptr.getType() == ESM::Light::sRecordId && allowLight)
        addExtraLight(getOrCreateObjectRoot(),
            SceneUtil::LightCommon(*ptr.get<ESM::Light>()->mBase));
    if (!allowLight && mObjectRoot) { RemoveParticlesVisitor ... }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Point-light color is colourFromRGB (R = low byte, G = next, B = next,
/255, alpha 1). Radius is max(esm radius, 16). Negative flag inverts
diffuse and sets specular to 0. addLight attaches the LightSource under a
child named AttachLight if found, else under the passed node. For interiors,
openmw.cfg fallbacks UseConstant=0 UseLinear=1 LinearMethod=1 LinearValue=3
LinearRadiusMult=1 UseQuadratic=0 produce linear attenuation = 3/radius,
quadratic 0, constant 0 (and only if all three ended up 0 would constant
be forced to 1).

From components/sceneutil/util.cpp:
    osg::Vec4f colourFromRGB(unsigned int clr) {
        osg::Vec4f colour(
            ((clr >> 0) & 0xFF) / 255.0f,
            ((clr >> 8) & 0xFF) / 255.0f,
            ((clr >> 16) & 0xFF) / 255.0f, 1.f);
        return colour;
    }

From components/sceneutil/lightcommon.cpp:
    LightCommon::LightCommon(const ESM::Light& light)
        : mNegative(light.mData.mFlags & ESM::Light::Negative)
        , mColor(SceneUtil::colourFromRGB(light.mData.mColor))
        , mRadius(static_cast<float>(light.mData.mRadius))

From components/sceneutil/lightutil.cpp:
    void configureLight(osg::Light* light, float radius, bool isExterior) {
        ...
        if (useLinear) {
            linearAttenuation = linearMethod == 0 ? linearValue : 0.01f;
            float r = radius * linearRadiusMult;
            if (r > 0.f && (linearMethod == 1 || linearMethod == 2))
                linearAttenuation = linearValue / std::pow(r, (float)linearMethod);
        }
        if (useQuadratic && (!outQuadInLin || isExterior)) { ... }
        if (constant == 0 && linear == 0 && quadratic == 0)
            constantAttenuation = 1.f;
        ...
    }
    osg::ref_ptr<LightSource> addLight(osg::Group* node, const LightCommon& esmLight, ...) {
        FindByNameVisitor visitor("AttachLight");
        node->accept(visitor);
        osg::Group* attachTo = visitor.mFoundNode ? visitor.mFoundNode : node;
        ...
        createLightSource(esmLight, ..., isExterior, osg::Vec4f(0,0,0,1));
        attachTo->addChild(lightSource);
    }
    createLightSource:
        const float radius = std::max(esmLight.mRadius, 16.f);
        configureLight(light, radius, isExterior);
        osg::Vec4f diffuse = esmLight.mColor;
        osg::Vec4f specular = esmLight.mColor;
        if (esmLight.mNegative) { diffuse *= -1; diffuse.a() = 1; specular = {}; }
        light->setDiffuse(diffuse); light->setAmbient(ambient); // ambient arg is 0
        light->setSpecular(specular);

From files/openmw.cfg:
    fallback=LightAttenuation_UseConstant,0
    fallback=LightAttenuation_UseLinear,1
    fallback=LightAttenuation_LinearMethod,1
    fallback=LightAttenuation_LinearValue,3.0
    fallback=LightAttenuation_LinearRadiusMult,1.0
    fallback=LightAttenuation_UseQuadratic,0
    fallback=LightAttenuation_OutQuadInLin,0

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Interior mood uses cell AMBI ambient and sunlight, both colourFromRGB.
configureAmbient also sets a fixed interior sun position
Vec4(-1, radians(45), radians(45), 0) and sun vector = -that position
(Morrowind nonsense, still used). Default classic falloff is false, max
lights 8 per object. lcalcIllumination is 1/(const + lin*d + quad*d*d),
and when classic falloff is false it also multiplies
1 - quickstep(dist/radius - 1) and the lighting loop skips a point light
when dist > radius * 2. Point-light ambient added in that loop is the
light's ambient channel (createLightSource passed 0,0,0,1) times
illumination; cell ambient is gl_LightModel.ambient / getAmbientColor,
not each LIGH.

From apps/openmw/mwrender/renderingmanager.cpp configureAmbient:
    osg::Vec4f ambient = SceneUtil::colourFromRGB(cell.getMood().mAmbiantColor);
    ...
    setAmbientColour(ambient);
    osg::Vec4f diffuse = SceneUtil::colourFromRGB(cell.getMood().mDirectionalColor);
    setSunColour(diffuse, diffuse, 1.f);
    static const osg::Vec4f interiorSunPos =
        osg::Vec4f(-1.f, osg::DegreesToRadians(45.f), osg::DegreesToRadians(45.f), 0.f);
    mPostProcessor->getStateUpdater()->setSunPos(interiorSunPos, false);
    mPostProcessor->getStateUpdater()->setSunVec(-interiorSunPos);

From files/settings-default.cfg:
    classic falloff = false
    max lights = 8

From files/shaders/lib/light/lighting.glsl (point loop):
    vec3 lightPos = lcalcPosition(lightIndex) - viewPos;
    float lightDistance = length(lightPos);
#if !@classicFalloff
        if (lightDistance > lcalcRadius(lightIndex) * 2.0)
            continue;
#endif
    float illumination = lcalcIllumination(lightIndex, lightDistance);
    diffuseLight += lcalcDiffuse(lightIndex) * lambert * illumination;
    ambientLight += lcalcAmbient(lightIndex) * illumination;

From files/shaders/lib/light/lighting_util.glsl:
    float lcalcIllumination(int lightIndex, float dist) {
        float illumination = 1.0 / (const + lin * dist + quad * dist * dist);
#if !@classicFalloff
        illumination *= 1.0 - quickstep((dist / lcalcRadius(lightIndex)) - 1.0);
#endif
        return illumination;
    }

From components/esm3/loadcell.hpp AMBIstruct:
    Color mAmbient, mSunlight, mFog; float mFogDensity;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

# Phase 34: Cheaper opaque and alpha

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/renderingmanager.cpp` (`SMALL_FEATURE_CULLING` on the scene camera) and `files/settings-default.cfg` `[Camera] small feature culling pixel size = 2.0`. Do **not** port OSG `CullVisitor`, occlusion, or cluster culling. Reuse the water-camera AABB pixel test already in `ForwardRenderer`. No other-LLM prompts: the main-camera threshold is read from the pin.

## Goal

Same viewer. Phase 33 water cameras / intern / frustum / Dump stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load. Grid stays **5×5-minus-corners**.

**Town’s opaque and alpha passes submit less.** Harbor in front of you still looks full (docks, trees, nearby lily pads). Distant specks that cover fewer than **2 pixels** are not drawn. Water RTTs stay at pixel size **20** on the 512 maps.

## Why this slice

Phase 33 harbor Dump (`n=60`): **~25 fps**, **39.9 ms**. Fat sections are the main view, not water:

- `ms.alpha=9.6` `draws.alpha=3558`
- `ms.opaque=7.7` `draws.opaque=2518`
- water maps already `ms.rttRefract=5.6` / `ms.rttReflect=5.3` `draws.rtt=1064`

Phase 31 frustum-culls off-screen meshes. Tiny on-screen clutter (ingredients, far leaves, distant posts) still submits. OpenMW’s scene camera drops those by default (`small feature culling = true`, pixel size **2**). Water cameras already use the same math at 20 px / 512.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- **Main-view small-feature cull:** after the frustum test passes, skip a mesh when its world AABB sphere projects smaller than **2** pixels on the **framebuffer height** (OpenMW/OSG uses viewport height). Same radius / view-space z / `2*tan(fov/2)` formula as the water RTT test. Invalid AABB still draws.
- **Viewing distance:** skip **small** non-terrain meshes whose world AABB is entirely farther than **7168**. Meshes with a longest AABB axis ≥ **128** (trees, shacks, docks) still draw across the 5×5. Land and the water plane stay. 2 px still drops specks.
- Apply to **opaque and alpha** (passes 0 and 2). Terrain tiles are huge and will not hit the 2 px cut; running the test on them is fine. Do not small-feature-cull sky or the water plane.
- Water cameras **keep** pixel size 20 on 512. Do not use 2 px on the RTTs (docks would vanish).
- Profiler: `draws` / `draws.opaque` / `draws.alpha` / `drawsCulled` stay submitted vs skipped. Overlay / Dump shape unchanged. `n=60` Dump is still the perf record.
- Never `ModelBatch`. Chair HUD unfogged. Grid radius, intern cache, and water maps unchanged.

## Out of scope

- Occlusion (docks behind a hill still draw if they project ≥ 2 px)
- Merging land draws, instancing, keep overlapping tiles
- Changing water RTT size or pixel size
- Weather fog (Clear still has density 0 on exteriors)
- Physics, navmesh, moons, weather, sunglare
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Look at the harbor: docks, trees, and nearby lily pads still there. Dump `n=60`: `draws.opaque` and `draws.alpha` below the Phase 33 **2518 / 3558**; `ms.opaque` / `ms.alpha` down or fps up / `frameMs` down vs **39.9**. Distant specks may disappear (that is the feature). **Cell** / **Cave** still load; chair unfogged.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Harbor in front of the camera still complete (no missing nearby trees, docks, or water). `glError=0`.
- Dump looking at the harbor: `draws.opaque` and `draws.alpha` down vs 2518 / 3558. fps up or `frameMs` down. `n=60`.
- Chair HUD unfogged. Walk-grid / Dump / F4 / intern / water RTT pixel size 20 unchanged.

Missing nearby meshes in view, a dry harbor, or applying 2 px to the water cameras is a **fail**. Distant tiny clutter disappearing is **not** a fail. Distant trees/shacks missing is a **fail**. Occlusion is out of scope, not a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `[Camera] small feature culling pixel size = 2` | main-view AABB pixel test vs framebuffer height | rewrite |

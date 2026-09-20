# Phase 31: Frustum-cull draws

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with OSG view-frustum culling (`osg::CullStack` / `VIEW_FRUSTUM_CULLING` on the scene camera in `apps/openmw/mwrender/renderingmanager.cpp`). Do **not** port OSG `CullVisitor`, cluster culling, or `smallFeatureCulling`. Path B: test existing mesh AABBs against the libGDX camera frustum. No other-LLM prompts: AABB vs frustum is read from the pin and from `MeshGpu.expandWorldAabb`.

## Goal

Same Path B viewer. Phase 30 Dump / fps overlay stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load. Grid stays **5×5-minus-corners**.

**Town stops submitting draws for meshes outside the camera.** Harbor in front of you still looks full. Land and shacks behind you are not drawn. Water RTTs use the same rule with the camera for that pass.

## Why this slice

Phase 30 Dump at Census `DODT` (n=60): **~13 fps**, **~14k main draws**, **~14k RTT draws** (`draws.rtt` ≈ 2 × (terrain + opaque)). Every mesh in 21 cells is submitted three times (main + refract + reflect) with no frustum test. `MeshGpu` already has local AABB. This is the cheapest cut that hits both the main view and the water cameras.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- In `ForwardRenderer.drawNode`, skip `glDrawElements` when the mesh has a valid local AABB and its world box is outside `cam.frustum` (`Frustum.boundsInFrustum` or equivalent 8-corner test). Reuse `MeshGpu.expandWorldAabb`. Invalid AABB (`localMin > localMax`): still draw.
- After the reflection view matrix is applied, **rebuild that camera’s frustum** from `combined` before drawing the RTT. Do not cull the reflection pass with the unrelected player frustum. Refraction keeps the player frustum (same view, clip plane only).
- Do not frustum-cull sky or the water plane (camera-relative / huge). Node `mesh.cull` (back-face) is unchanged.
- Profiler: `draws` / `draws.rtt` stay **submitted** counts. Add `drawsCulled=` (main + RTT skips this frame) to Dump so an agent can see the win. Overlay may show culled.
- Dump after `n=60` is still the perf record. Never `ModelBatch`. Chair HUD unfogged.

## Out of scope

- Occlusion / small-feature pixel culling
- Skipping water RTT, shrinking RTT size, or dropping `CELL_GRID_RADIUS`
- Merging land draws, instancing, GPU occlusion queries
- Incremental keep of overlapping tiles
- Skinned bound updates (rest-pose AABB on the bone node is OK for this slice)
- Physics, navmesh, moons, weather, sunglare
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Look at the harbor: trees, docks, water, lily pads still there; Dump `n=60` still has water RTT times. Turn ~180° toward the census building / inland: `draws` and `draws.rtt` drop vs the Phase 30 ~14k/~14k baseline; fps goes up. Nearby objects in view do not pop out. **Cell** / **Cave** still load; chair unfogged.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Harbor view still complete (no missing water, docks, or trees in front of the camera).
- Dump looking away from the harbor: `drawsCulled` > 0 and `draws` well below the uncull ~14k. `glError=0`.
- Chair HUD unfogged. Walk-grid / Dump / F4 unchanged.

Missing nearby meshes in view, or a dry harbor, is a **fail**. Changing grid radius or skipping water RTT is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| OSG `VIEW_FRUSTUM_CULLING` | `ForwardRenderer` mesh AABB vs `cam.frustum` | rewrite |

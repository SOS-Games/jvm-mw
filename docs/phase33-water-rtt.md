# Phase 33: Cheaper water cameras

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/water.cpp` (`Reflection::calcNodeMask`, `setSmallFeatureCullingPixelSize` on the water cameras) and `files/settings-default.cfg` `[Water]`. Do **not** port OSG `ClipCullNode`, `smallFeatureCulling` on the **main** camera, or drop refraction. No other-LLM prompts: those two water-camera rules are read from the pin.

## Goal

Same viewer. Phase 32 intern / frustum cull / Dump stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load. Grid stays **5×5-minus-corners**. Water stays shader water with **both** 512 RTTs.

**Town’s water cameras draw less.** Harbor water still shows land, docks, and sky. People need not appear in the reflection. Dump `n=60` looking at the harbor: `draws.rtt` and `ms.rttRefract` / `ms.rttReflect` drop vs the post-cull baseline. fps goes up.

## Why this slice

Phase 31 frustum-culls the player camera **and** the water cameras, but the water cameras still submit every remaining opaque mesh (including NPCs) into two 512 RTTs. Phase 30 Dump: `ms.rttRefract` ≈ `ms.rttReflect` ≈ 16 ms each, `draws.rtt` ≈ 2 × (terrain + opaque). OpenMW’s water cameras are cheaper than the main view by default:

- `reflection detail = 2`: reflection gets sky + terrain + statics. Not actors (`Mask_Actor` at detail 4).
- Water RTT `small feature culling pixel size = 20` (main camera uses 2). Tiny distant meshes never hit the 512 maps.

Keep `rtt size = 512`. Do not turn refraction off (we already shipped it).

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- **Reflection detail 2:** when drawing the reflection RTT, skip NPC and creature subgraphs (the placed actor roots from `NpcMannequin`). Terrain, STAT, doors, clutter, land, and sky stay. Refraction still draws actors (OpenMW refraction cull mask includes `Mask_Actor`). A node flag on the actor root is enough; do not invent OSG masks.
- **Small-feature cull on both water cameras only:** skip a mesh in an RTT when its world AABB projects smaller than **20** pixels on the 512 map (OpenMW water camera pixel size). Main view stays frustum-only. Invalid AABB still draws.
- Profiler: `draws.rtt` stays submitted RTT draws. Overlay / Dump unchanged in shape. `n=60` Dump is still the perf record.
- Never `ModelBatch`. Chair HUD unfogged. Grid radius, intern cache, and water plane (no frustum cull of the plane) unchanged.

## Out of scope

- Shrinking `RTT_SIZE` below 512
- Skipping water RTT entirely, or skipping refraction
- Incremental keep of overlapping tiles
- Main-view small-feature cull
- Merging land draws, occlusion, distant land
- Physics, navmesh, moons, weather, sunglare
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Look at the harbor: water, docks, trees, land in the reflection/refraction still there. NPCs may vanish from the **reflection**. Dump `n=60`: `draws.rtt` well below the post–Phase 31 harbor figure; `ms.rttRefract` / `ms.rttReflect` down; `glError=0`. **Cell** / **Cave** still load (no water RTT); chair unfogged.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Harbor water still looks like water (docks/land/sky in the maps). `glError=0`.
- Dump looking at the harbor: `draws.rtt` down vs frustum-cull-only; fps up or frame ms down. `n=60`.
- Chair HUD unfogged. Walk-grid / Dump / F4 / intern / frustum on the **main** view unchanged.

A dry harbor, missing docks in the water, shrinking the RTT, or dropping refraction is a **fail**. Actors missing from the reflection is **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Reflection::calcNodeMask` detail 2 | skip actor roots in reflection RTT | rewrite |
| `[Water] small feature culling pixel size = 20` | RTT AABB pixel test | rewrite |

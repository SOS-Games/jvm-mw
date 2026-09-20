# Distant land / object paging

This is a **big topic**, not a phase. Do **not** implement from this file. Split **one** row into [next-topics.md](next-topics.md), then spec that slice after the user picks it. The leftover stays in [big-topics.md](big-topics.md).

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/renderingmanager.cpp` (quadtree vs cell grid), `components/terrain/quadtreeworld.*`, `apps/openmw/mwrender/objectpaging.*`, `apps/openmw/mwrender/fogmanager.cpp`, `files/settings-default.cfg` `[Terrain]` / `[Fog]` / `[Camera] viewing distance`.

## What Town has now

Walk Town and you get a **5×5-minus-corners** of full cells: land, statics, NPCs, water. Fog and viewing distance are **7168**. Small meshes past that are skipped; trees and shacks in the 5×5 still draw. There is no land outside those 21 tiles. Crossing a cell rebuilds the grid (textures intern; live tiles do not).

That matches OpenMW’s *default* more than it sounds: `distant terrain = false`, `viewing distance = 7168`, `use distant fog = false`. Horizon pop is expected until this work ships.

## What OpenMW does when you turn it on

`distant terrain = true` swaps the cell-grid heightfields for a **quadtree**: chunks at several sizes, vertex LOD, and (farther out) **composite maps** baked from land textures. `object paging` (default true, only used with distant terrain or groundcover) packs far `STAT` / `ACTI` / `DOOR` into those chunks; containers stay near; NPCs and creatures never page. The active 3×3 (our 5×5) still owns gameplay objects. `getPagedRefnums` stops the scene from drawing the same shack twice. Take / disable updates the pager so a picked bottle does not come back as a distant mesh.

Distant fog is a second switch (`use distant fog`, land start **16384** / end **40960`). Off, fog still tracks viewing distance.

## Why it is large

It is a second world around the 5×5: stream land you do not fully simulate, merge meshes without `ModelBatch`, keep pager and live cells in sync, grow the camera far plane, and not hitch every walk. Groundcover, shadows, and occlusion are other rows in [big-topics.md](big-topics.md).

## Prerequisites

| Must already be true | Why |
| --- | --- |
| **Keep overlapping tiles** ([next-topics.md](next-topics.md)) | Far rings that rebuild on every cell cross are worse than today’s swap. Do this first. |
| **Merge land draws** (speed queue, after overlapping tiles) | More land tiles without merging multiplies blend-layer submits. Can overlap with early far-land, but Town will stall if both lag. |

Do not grow the 5×5 into a 9×9 of *full* cells (NPCs, lights, chests). That is not distant land; it is a bigger active grid.

## Work order

Check a box only when that slice has shipped as a phase. Move a row to [next-topics.md](next-topics.md) when the user wants it next; write the phase spec then.

### 0. Keep overlapping tiles

- [ ] Walk keeps live land and scene nodes for cells that stay in the 5×5. Only the new strip loads; only the old strip drops.

Already on the small queue. Not distant land, but nothing below is cheap without it.

### 1. Far land rings (land only)

- [ ] Extra `LAND` tiles **outside** the 5×5, same 65×65 decode as Town, no refs.
- [ ] Grow the camera far plane past 7168 so the new ground is not clipped.
- [ ] Water plane covers the new ring (or ocean is a hole).
- [ ] No NPCs, lights, doors, or take on those cells. Missing land stays height **−2048**.
- [ ] Fog may stay 7168 at first (far land fades); or nudge end with the far plane — say which in the phase spec.

**Town test:** from Census `DODT` look inland / along the coast; ground continues past the 5×5. Walk still recenters the *active* grid only. Chair unfogged.

This is the first slice that can become a small phase. Vertex LOD and composite maps wait.

### 2. Viewing distance and distant fog

- [ ] Exterior viewing distance is a real number (OpenMW default 7168; distant land needs more). Small-feature 7168 skip must follow it, not stay hardcoded while land draws farther.
- [ ] With `use distant fog` off: `fogStart = view * (1 - density)`, `fogEnd = view` (Clear density 0 still means no land fog).
- [ ] With it on: land start **16384**, end **40960**, then weather factor/offset (Clear is 1.0 / 0.0). Full weather types stay [big-topics.md](big-topics.md).

Can ship with (1) or right after. Underwater fog still `min(view, 7168)` unless the underwater distant-fog settings are in that same slice.

### 3. Coarser far land (vertex LOD)

- [ ] Rings farther than one cell use fewer verts than 65×65 (OpenMW `lod factor` / `vertex lod mod`).
- [ ] Seams at LOD borders must not open (same gotcha as kit walls).

Skip until (1) is on screen and Dump says land draws are the cost. [Merge land draws](next-topics.md) may be enough for a first ring.

### 4. Far statics, still one mesh each

- [ ] On far cells, place `STAT` (maybe `ACTI` / `DOOR`) with the existing NIF path. No NPC / `CREA` / `LEVC` / lights / take.
- [ ] Size / distance cull like OpenMW `object paging min size` (default **0.01** × distance): silt striders stay, bowls go.
- [ ] Far containers stay out (`typeFilter`: `CONT` only on chunks smaller than 2 cells).

Looks like Vvardenfell sooner than true paging. Draw count will hurt; (5) is the fix. Must not also draw that ref inside the 5×5.

### 5. Object paging (batched chunks)

- [ ] Chunk id: center, size, in-active-grid flag (`ObjectPaging::ChunkId`).
- [ ] Collect refs from ESM cell contexts (not a full `LoadedCell`). Far filter: `STAT` / `ACTI` / `DOOR`. Merge compatible draws into fewer GPU meshes. **Never `ModelBatch`.**
- [ ] Active-grid sync: `getPagedRefnums` — if paging owns it, `CellSceneBuilder` skips it (or the inverse). Double shacks are a fail.
- [ ] Take / disable / blacklist so a world item does not reappear as a distant batch (`enableObject` / `blacklistObject`).
- [ ] Optional: paging *inside* the 5×5 (`object paging active grid`) is a later knob; first pass can page **only** outside the active grid.

OpenMW merge factor **250**, min size **0.01**. Copy those numbers unless Dump says otherwise.

### 6. Composite maps

- [ ] Far land chunks sample a baked atlas instead of per-layer blend (`composite map level` 0, resolution **512**, `max composite geometry size` **4**).

Last on purpose: needs the quadtree (or a fake one), extra RTT, and [merge land draws](next-topics.md) already taught us blend cost. Do not start here.

## Stay out of this topic

- Grass / groundcover (own big-topics row; OpenMW adds it as another chunk manager).
- Shadows, occlusion, sunglare, moons.
- Bigger active grid, pathgrid, navmesh, actor collision. Nav work order: [nav-paths.md](nav-paths.md).
- ESP tools, pre-baked distant-land files (do not port OpenMW tools).
- `ModelBatch`.

## Debug

No new CLI until a slice specs one. `exterior -2 -9` stays the 5×5. A later `exterior` line for `far=` cell counts is enough. F3 Dump should grow `draws` / culled so far land vs 5×5 is obvious. F4 overlay unchanged unless a slice adds a far-chunk count.

## OpenMW files (for NAME_MAP when a slice ships)

| OpenMW | Viewer when that slice exists |
| --- | --- |
| `[Terrain] distant terrain` | far land rings / quadtree on |
| `Terrain::QuadTreeWorld` | far `LAND` chunks + LOD |
| `Terrain::TerrainGrid` | today’s 5×5 heightfields (keep for active cells) |
| `[Terrain] object paging` | batched far STAT |
| `MWRender::ObjectPaging` | chunk collect / merge / cache |
| `ObjectPaging::getPagedRefnums` | skip those refs in the 5×5 |
| `[Camera] viewing distance` | far plane + small-feature distance |
| `[Fog] use distant fog` / land start-end | exterior fog when that slice ships |
| `FogManager::configure` + weather `dlFactor` / `dlOffset` | Clear 1.0 / 0.0 until more weather |
| `CompositeMapRenderer` | (6) only |

## How to split a phase

1. User picks **one** unchecked row (or **proceed** naming it).
2. Write `docs/phaseN-*.md` for that row only. Goal / in / out / Town test / pass-fail. Out of scope lists the later rows here.
3. After **working**: commit, push, check the box, add the small row to next-topics only if more of *this* row remains; otherwise delete the next-topics tease and leave this file + big-topics.

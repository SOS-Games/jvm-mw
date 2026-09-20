# Phase 32: Intern textures (and static NIFs) by path

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/resource/imagemanager.cpp` (`getImage` cache by normalized VFS path), `components/terrain/texturemanager.cpp` (`getTexture` same cache), and `Resource::SceneManager::getTemplate` (one loaded subgraph per mesh path). Do **not** port OSG `ObjectCache` expiry, `SharedStateManager`, or incremental compile. One GL texture per corrected DDS path, one static GPU template per mesh path. No other-LLM prompts: intern-by-path is read from those three.

## Goal

Same viewer. Phase 31 frustum cull / Dump / fps overlay stay. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load. Grid stays **5×5-minus-corners**.

**Town uploads each DDS once.** Eighty shacks that share `tx_wood_weathered.dds` share one GL texture. Walk to the next cell does not re-upload that wood, or re-upload `ex_de_shack_01.nif`, just because a new `CellSceneBuilder` was created. Harbor still looks full.

## Why this slice

Phase 31 got Town to ~25–35 fps by skipping off-camera draws. Load and VRAM are still wasteful:

- `NifSceneBuilder` keeps its own `Map<String, DdsTexture>`. Different NIFs that name the same file each call `DdsTexture.load` (new `glGenTexture`). `LandMesh` has a third map. `NpcMannequin` has another set of builders.
- Static STAT copies **inside one** `CellSceneBuilder` already share GPU meshes (`templates` + `cloneTree`). Walk constructs a **new** builder, `dispose()`s the old one, and re-parses / re-uploads every unique NIF still in the overlapping 16 cells.

OpenMW interned decoded images by path and interned scene templates by mesh path. This slice does the equivalent for DDS + static templates. It does **not** keep live land tiles or SceneNodes in the grid (that is the next cut).

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- A small process-lifetime cache (name as you like: `resource.TextureCache` / `GpuCache`) keyed by `TexturePaths.correctTexturePath` / `normalizeMeshPath`:
  - DDS: one `DdsTexture` (one GL id) per path. `NifSceneBuilder`, `LandMesh`, `SkyClouds`, `SkySun` all go through it. Wrap/filter stay **per draw** (`ForwardRenderer.bindUnit` already sets wrap on bind).
  - Static NIF templates: lift `CellSceneBuilder.templates` (and the `NifSceneBuilder` that owns those `MeshGpu`s) so a second builder / walk swap clones the same GPU meshes. `NpcMannequin` rigid parts / skeleton templates use the same intern. **Skinned** `MeshGpu` (`uploadSkinned` / `dynamic`) stay per actor — they write vertices.
- `dispose()` on a builder, mannequin, or `LandMesh` **must not** `glDeleteTexture` / delete interned `MeshGpu`. Cache owns those until app / renderer dispose. Blendmaps stay per land tile (unique); do not intern them. Water `water_nm` may intern or stay the existing single upload.
- Dump: add `texGpu=` (unique interned DDS) and `nifGpu=` (unique interned static templates) so an agent can see the win. Overlay may show them. `meshes=` stays instance count. `n=60` Dump is still the perf record.
- Never `ModelBatch`. Chair HUD unfogged. Frustum cull / water RTT / grid radius unchanged.

## Out of scope

- Incremental keep of overlapping **tiles** (live land chunks, water plane recenter, lights, taken-item nodes). Walk still builds a new scene graph; it just hits the GPU cache.
- Interning parsed `NifFile` bytes beyond what the template cache already avoids
- Texture expiry / LRU
- Merging land draws, instancing, occlusion
- Physics, navmesh, moons, weather, sunglare
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Harbor (trees, docks, water, lily pads) still complete. Dump `n=60`: `texGpu` is on the order of unique DDS files, not unique-NIF × textures-per-NIF. Walk far enough for a grid swap: harbor textures do not go white/black for a beat; `texGpu` / `nifGpu` do not jump by the overlapping unique count (they stay flat or grow only by **new** paths). **Cell** / **Cave** still load; chair unfogged.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Harbor view still complete. `glError=0`.
- Dump `texGpu=` well below “one upload per NIF that named that file.” After a walk swap, no missing textures.
- Chair HUD unfogged. Walk-grid / Dump / F4 / frustum cull unchanged.

Missing or white world textures, a dry harbor, or deleting a shared GL id on builder dispose (black/white flash after walk) is a **fail**. Changing grid radius or skipping water RTT is a **fail**. Keeping live land tiles is **out of scope**, not a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Resource::ImageManager::getImage` | intern DDS by corrected VFS path | rewrite |
| `Terrain::TextureManager::getTexture` | `LandMesh` uses the same intern | rewrite |
| `Resource::SceneManager::getTemplate` | static NIF `MeshGpu` template intern | rewrite |

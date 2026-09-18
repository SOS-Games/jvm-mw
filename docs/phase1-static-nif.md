# Phase 1: static NIF + Scene2D

OpenMW pin: **openmw-0.51.0**.

## Goal

Prove Path B: libGDX owns the window; **not** `ModelBatch`. One Morrowind chair, parent/child `SceneNode`s, Scene2D controls on top, no GL errors.

## In scope

- Morrowind NIF **4.0.0.2** only (`NifFile.VER_MW`).
- Records needed for static props: `NiNode` (incl. `RootCollisionNode`), `NiTriShape` / `NiTriStrips`, geometry data, `NiTexturingProperty`, `NiSourceTexture`, common properties (`NiMaterialProperty`, `NiAlphaProperty`, `NiVertexColorProperty`, `NiZBufferProperty`, …), extra data (`NiStringExtraData`, `NiExtraData`).
- Hide `RootCollisionNode` meshes; **do not delete** the nodes.
- Texture path: `\` → `/`, try `.dds` then original extension (`resourcehelpers.cpp`).
- TES3 BSA extract of one mesh + textures into gitignored `testdata/`.
- Custom VAO/VBO, GLSL 330, orbit camera.
- Scene2D: label + button after a GL state reset.

## Out of scope

Lua, Bullet, ESM, VFS merge of the full mod list, skinning, `.kf`, particles, water, shadows, MyGUI layouts.

## Morrowind 4.0.0.2 gotchas (from OpenMW)

- `bool` is **int32**, not int8 (`NIFStream::read<bool>`: version `< 4.1.0.0`).
- Vertex color modes: `0` ignore, `1` **emissive**, `2` **ambient+diffuse** (do not swap).
- Morrowind specular is disabled in `nifosg` (`mVersion <= VER_MW`).
- Root `NiNode` that is not `bip01` gets identity transform in OpenMW `NiNode::read`.

## Pass / fail

- Chair visible, textured, orbiting.
- Scene2D button clickable; no leftover depth test on the HUD.
- `glGetError() == 0` after the frame.
- Collision proxies not drawn.

## Test meshes

From `Morrowind.bsa` (Steam `Data Files`). Not committed.

- `meshes/f/furn_de_chair_01.nif` — opaque furniture (vanilla has no `furn_chair_01.nif`)
- `meshes/x/ex_de_shack_01.nif` — larger static (more nodes / textures)
- `meshes/f/flora_bc_tree_01.nif` — cutout foliage (`NiAlphaProperty`)

HUD buttons switch among them. Collision proxies must stay hidden.

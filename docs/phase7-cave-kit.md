# Phase 7: cave kit seams

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/nif/niftypes.hpp` (`NiTransform::toMatrix`, `Matrix3::toOsgMatrix`), `components/nifosg/matrixtransform.cpp`, `components/nifosg/nifloader.cpp` (`createNode` / `handleNode`), `components/nif/node.cpp` (`NiNode::read` root identity), `components/misc/convert.hpp` (`makeOsgQuat`), `apps/openmw/mwworld/scene.cpp` (`addObject` / `makeDirectNodeRotation`). Other-LLM checks of those three claims: **pending**.

## Goal

Same Path B viewer. Phase 6 fog and flicker stay. **Addamasartus** (Cave button) should look like a Morrowind cave: rock modules meet, hide doors sit in their frames, fog only in the distance — not through holes in the walls.

Census office (**Cell** button) must not regress. Chair HUD unchanged.

## Why this slice

Phase 6 is done: candles flicker, AMBI fog is in the shader. Addamasartus was only a **fog test cell**. Walking it showed the next hole: `in_moldcave_*` kit pieces and `door_cavern_doors00` do not meet. That is placement / NIF local transforms, not fog, and not “camera outside the hull.”

Actors still wait.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only. Test cell: **Addamasartus** (inbound door spawn already matches the exterior `DODT` `(1280, 992, 480)`).

The same STAT/DOOR path as Census. The kit is what changes:

- Cave chunks (`meshes/i/in_moldcave_*.nif`, `in_moldcave2_*.nif`) keep a **large local translation** on the `NiTriShape` (often `(0, 128, 0)` or bigger). Root `NiNode` is already identity; OpenMW still identity’s record 0 if it were not.
- Hide doors (`door_cavern_doors00`) have a small hinge offset (`~11.84` on X) and must line up with `in_moldcave_doorway00`.
- World matrix is **cell −90° X** × **ESM `makeOsgQuat` + pos + XSCL** × **each NIF node’s `NiTransform::toMatrix`**. No second −90°. No actor yaw-only quat. No inverse-order quat on first insert (`addObject` uses `makeDirectNodeRotation`).

Debug CLI already dumps this (`nif`, `cell`, `spawn`). Use it; do not add another viewer.

Fix until seams close. Likely suspects (check against OpenMW, do not guess):

- `NiTransform::toMatrix` row/column swap into libGDX `Matrix4` (OpenMW: `transform(j,i) = mValues[i][j] * scale`, translation via `setTrans`).
- `EsmTransforms.setLocal` vs `makeOsgQuat` (Z×Y×X, axes `(0,0,-1)`, `(0,-1,0)`, `(-1,0,0)`).
- Extra identity `nif-root` wrapper vs OpenMW parenting the NIF root directly under the ref node (should be harmless if identity).
- Backface cull / `NiStencilProperty` draw mode only if a dump shows cave walls need `DRAW_BOTH`; do not disable cull globally as a cheat.

## Out of scope

- `NPC_` / `CREA`, body parts, `baseanim`, skinning, `.kf`
- Door **open/close** animation (closed pose only)
- Other biomes except as a sanity check (one `in_lava*` or Dwemer corridor is enough if moldcave is fixed)
- Distant/radial/exponential fog, actors, plugins, terrain, Lua, Bullet

## Test cell

`Addamasartus`. Spawn is the inbound door (already). Walk the first tunnel. **Cell** still loads Census.

```bat
gradlew.bat :core:debugCli --args="nif meshes/i/in_moldcave_03.nif"
gradlew.bat :core:debugCli --args="cell Addamasartus"
```

F3 writes `build/debug-snapshot.txt`.

## Pass / fail

- Tunnel walls meet; no fog-colored slits between `in_moldcave_*` chunks.
- Hide doors sit in doorways, not floating beside them.
- Fog still thickens with distance down a **closed** tunnel.
- Census office still looks like Phase 6.
- Chair HUD unfogged. `glError=0`.

## Other-LLM claims (pending)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **NIF node matrix.** `NiTransform::toMatrix` writes `transform(j, i) = mRotation.mValues[i][j] * mScale` then keeps translation from `setTrans`. Child `NiTriShape` offsets (kit centering) are this matrix, not baked into vertex positions.
2. **First insert rotation.** `addObject` uses `makeDirectNodeRotation` → `makeOsgQuat`: `Quat(rot[2], (0,0,-1)) * Quat(rot[1], (0,-1,0)) * Quat(rot[0], (-1,0,0))`. Inverse-order (`X*Y*Z`) is a different helper, not first-load insert. Doors are not actors (not yaw-only).
3. **Root identity.** `NiNode::read`: if `mRecordIndex == 0` and name is not `bip01`, replace the node transform with identity. Children keep theirs. Only `NiNode` (not a root `NiTriShape`). Skip if node 0 is not the only root (OpenMW FIXME; Morrowind cave files we dumped are `roots=[0]`).

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `Nif::NiTransform::toMatrix` | `NiTransform.toMatrix` | same (verify vs libGDX) |
| `NifOsg::MatrixTransform` | `SceneNode.local` | rewrite |
| `makeDirectNodeRotation` | `EsmTransforms.setLocal` | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: NiTransform::toMatrix builds an OSG matrix whose 3x3 is
transform(j, i) = mRotation.mValues[i][j] * mScale (row/column swap vs the
NIF 3x3), and whose translation is mTranslation via setTrans. The NIF 3x3
can include negative/nonuniform scale. Child NiTriShape local translation
is applied by this matrix, not by moving vertices in NiTriShapeData.

From components/nif/niftypes.hpp NiTransform::toMatrix:
    osg::Matrixf transform;
    transform.setTrans(mTranslation);
    for (int i = 0; i < 3; ++i)
        for (int j = 0; j < 3; ++j)
            transform(j, i) = mRotation.mValues[i][j] * mScale;
    return transform;

From the Matrix3 comment on mRotation:
    // this can contain scale components too, including negative and nonuniform scales

From components/nifosg/nifloader.cpp createNode:
    node = new NifOsg::MatrixTransform(nifNode->mTransform);

From components/nifosg/matrixtransform.cpp ctor:
    : osg::MatrixTransform(transform.toMatrix())

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: When adding an object to the scene the first time, addObject uses
makeDirectNodeRotation, which for non-actors is Misc::Convert::makeOsgQuat:
Quat(rot[2], (0,0,-1)) * Quat(rot[1], (0,-1,0)) * Quat(rot[0], (-1,0,0)).
makeInversedOrderObjectOsgQuat is X * Y * Z with the same negated axes and
is not what addObject passes. Actors use yaw-only Quat(rot[2], (0,0,-1)).
Doors are not actors.

From apps/openmw/mwworld/scene.cpp:
    osg::Quat makeInversedOrderObjectOsgQuat(...) {
        return osg::Quat(xr, osg::Vec3(-1, 0, 0)) * osg::Quat(yr, osg::Vec3(0, -1, 0))
            * osg::Quat(zr, osg::Vec3(0, 0, -1));
    }
    osg::Quat makeDirectNodeRotation(...) {
        return ptr.getClass().isActor() ? makeActorOsgQuat(pos) : Misc::Convert::makeOsgQuat(pos);
    }
    void addObject(...) {
        const auto rotation = makeDirectNodeRotation(ptr);
        ...
        ptr.getClass().insertObjectRendering(ptr, model, rendering);
        setNodeRotation(ptr, rendering, rotation);
    }

From components/misc/convert.hpp makeOsgQuat:
    return osg::Quat(rotation[2], osg::Vec3f(0, 0, -1)) * osg::Quat(rotation[1], osg::Vec3f(0, -1, 0))
        * osg::Quat(rotation[0], osg::Vec3f(-1, 0, 0));

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: After reading a NiNode, if mRecordIndex == 0 and the node name is
not bip01 (case-insensitive), OpenMW replaces mTransform with identity.
This is only in NiNode::read, not NiTriShape. The comment says skip this if
node 0 is not the only root. Children are not identity’d. Purpose: some
meshes get the wrong orientation if the root transform is kept.

From components/nif/node.cpp NiNode::read:
    // Discard transformations for the root node, otherwise some meshes
    // occasionally get wrong orientation. Only for NiNode-s for now, but
    // can be expanded if needed.
    // FIXME: if node 0 is *not* the only root node, this must not happen.
    if (mRecordIndex == 0 && !Misc::StringUtils::ciEqual(mName, "bip01"))
    {
        mTransform = Nif::NiTransform::getIdentity();
    }

From components/nifosg/nifloader.cpp createNode:
    if (nifNode->mParents.empty() && nifNode->mController.empty() && nifNode->mTransform.isIdentity())
        node = new osg::Group;
    if (!node)
        node = new NifOsg::MatrixTransform(nifNode->mTransform);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

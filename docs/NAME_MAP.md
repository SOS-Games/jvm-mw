# Name map

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Status: `same` = keep the C++ name, `rewrite` = new Java type (no OSG/MyGUI 1:1), `not ported`.

| OpenMW path | C++ symbol | Java | Status |
| --- | --- | --- | --- |
| `components/nif/nifstream.hpp` | `Nif::NIFStream` | `io.github.jvmmw.nif.NifStream` | same |
| `components/nif/niffile.hpp` | `Nif::NIFFile` / `Reader` | `io.github.jvmmw.nif.NifFile` | same |
| `components/nif/niftypes.hpp` | `Nif::NiTransform` | `io.github.jvmmw.nif.NiTransform` | same |
| `components/nif/niftypes.hpp` | `Nif::NiTransform::toMatrix` | `NiTransform.toMatrix` | same (GL layout) |
| `components/nif/nifstream.cpp` | `NIFStream::read<NiTransform>` | `NiTransform.readPacked` | same (rotation then translation; `NiAVObject` uses `read`) |
| `components/nifosg/matrixtransform.cpp` | `NifOsg::MatrixTransform` | `SceneNode.local` from `toMatrix` | rewrite |
| `components/nif/node.hpp` | `Nif::NiNode` | `io.github.jvmmw.nif.NiNode` | same |
| `components/nif/node.hpp` | `Nif::NiTriShape` | `io.github.jvmmw.nif.NiTriBasedGeom` | same |
| `components/nif/data.hpp` | `Nif::NiTriShapeData` | `io.github.jvmmw.nif.NiTriShapeData` | same |
| `components/nif/property.hpp` | `Nif::NiTexturingProperty` | `io.github.jvmmw.nif.NiTexturingProperty` | same |
| `components/nif/texture.hpp` | `Nif::NiSourceTexture` | `io.github.jvmmw.nif.NiSourceTexture` | same |
| `components/nifosg/nifloader.cpp` | `NifOsg::Loader` | `io.github.jvmmw.render.NifSceneBuilder` | rewrite |
| `components/sceneutil/` | `osg::Node` / `SceneUtil::*` | `io.github.jvmmw.render.SceneNode` | rewrite |
| `components/bsa/bsafile.hpp` | `Bsa::BSAFile` | `io.github.jvmmw.bsa.BsaArchive` | same |
| `components/vfs/registerarchives.cpp` | `VFS::registerArchives` | extra dirs after `Morrowind.bsa` | rewrite |
| `components/vfs/manager.cpp` | `VFS::Manager::buildIndex` | `vfs.VfsManager` last archive wins | rewrite |
| `components/vfs/filesystemarchive.cpp` | `VFS::FileSystemArchive` | `vfs.FileSystemArchive` | rewrite |
| `components/misc/resourcehelpers.cpp` | `correctTexturePath` | `io.github.jvmmw.resource.TexturePaths` | same |
| `apps/openmw/mwrender/*` | OSG draw | `io.github.jvmmw.render.ForwardRenderer` | rewrite |
| `apps/openmw/mwgui/*` | MyGUI | Scene2D HUD (Phase 1 debug only) | rewrite |
| `components/nif/property.hpp` | `Nif::NiVertexColorProperty` | `io.github.jvmmw.nif.NiVertexColorProperty` | same |
| `components/nif/property.hpp` | `Nif::NiMaterialProperty` | `io.github.jvmmw.nif.NiMaterialProperty` | same |
| `components/nif/property.hpp` | `Nif::NiAlphaProperty` | `io.github.jvmmw.nif.NiAlphaProperty` (full bitfield) | same |
| `components/nif/property.hpp` | `Nif::NiZBufferProperty` | `io.github.jvmmw.nif.NiZBufferProperty` | same |
| `components/nif/property.hpp` | `Nif::NiStencilProperty` | `io.github.jvmmw.nif.NiStencilProperty` (draw-mode cull only) | same (partial) |
| `components/nifosg/nifloader.cpp` | `collectDrawableProperties` / `applyDrawableProperties` | `NifSceneBuilder` flatten | rewrite |
| `files/shaders/compatibility/objects.frag` | dark / detail / glow | `io.github.jvmmw.render.ForwardRenderer` | rewrite |
| `components/esm3/esmreader.hpp` | `ESM::ESMReader` | `io.github.jvmmw.esm.EsmReader` | same |
| `components/esm3/loadcell.hpp` | `ESM::Cell` | `io.github.jvmmw.esm.EsmFile` / `LoadedCell` | same |
| `components/esm3/cellref.hpp` | `ESM::CellRef` | `io.github.jvmmw.esm.CellRef` | same |
| `components/esm3/loadstat.hpp` | `ESM::Static` | `io.github.jvmmw.esm.EsmObject` | rewrite |
| `components/misc/convert.hpp` | `Misc::Convert::makeOsgQuat` | `io.github.jvmmw.render.EsmTransforms` | rewrite |
| `apps/openmw/mwworld/scene.cpp` | insert static | `io.github.jvmmw.render.CellSceneBuilder` | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `makeDirectNodeRotation` | `EsmTransforms.setLocal` | rewrite |
| `apps/openmw/mwclass/classmodel.hpp` | `getClassModel` | `esm.EsmObject` lookup | rewrite |
| `components/misc/resourcehelpers.cpp` | `isHiddenMarker` | `EsmFile.isHiddenMarker` | same |
| `components/esm3/loadligh.hpp` | `ESM::Light::LHDTstruct` | `esm.EsmObject` light fields | same |
| `components/sceneutil/lightutil.cpp` | `addLight` / `createLightSource` | `render.CellSceneBuilder` + `CellLight` | rewrite |
| `components/sceneutil/lightutil.cpp` | `configureLight` | `CellLight` attenuation | rewrite |
| `apps/openmw/mwrender/renderingmanager.cpp` | `configureAmbient` | `CellLighting` from cell `AMBI` | rewrite |
| `files/shaders/lib/light/lighting.glsl` | `lcalcIllumination` | `ForwardRenderer` GLSL | rewrite |
| `apps/openmw/mwrender/fogmanager.cpp` | `FogManager::configure` | `CellLighting.configureFog` | rewrite |
| `files/shaders/compatibility/fog.glsl` | `applyFogAtDist` | `ForwardRenderer` GLSL fog | rewrite |
| `components/sceneutil/lightcontroller.cpp` | `LightController` | `CellLighting.updateFlicker` | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `makeActorOsgQuat` | `EsmTransforms.setActorLocal` | rewrite |
| `apps/openmw/mwclass/npc.cpp` | `Npc::adjustScale` | race height/weight on instance scale | rewrite |
| `apps/openmw/mwrender/npcanimation.cpp` | `MWRender::NpcAnimation` | `render.NpcMannequin` | rewrite |
| `components/sceneutil/attach.cpp` | `SceneUtil::attach` | part attach + left mirror | rewrite |
| `components/nif/data.hpp` | `Nif::NiSkinInstance` | `nif.NiSkinInstance` | same |
| `components/nif/node.hpp` | `Nif::NiSequenceStreamHelper` | `nif.NiSequenceStreamHelper` | same |
| `components/nif/extra.hpp` | `Nif::NiTextKeyExtraData` | `nif.NiTextKeyExtraData` | same |
| `components/nif/controller.hpp` | `Nif::NiKeyframeController` | `nif.NiKeyframeController` | same |
| `components/nif/data.hpp` | `Nif::NiKeyframeData` | `nif.NiKeyframeData` | same |
| `components/nifosg/nifloader.cpp` | `NifOsg::Loader::loadKf` | `nif.KfFile` | rewrite |
| `apps/openmw/mwrender/animation.cpp` | `MWRender::Animation::play` idle | `render.NpcMannequin` idle | rewrite |
| `components/esm3/loadcrea.hpp` | `ESM::Creature` | `esm.EsmCreature` | same |
| `apps/openmw/mwclass/creature.cpp` | `Creature::insertObjectRendering` | `CellSceneBuilder` CREA place | rewrite |
| `apps/openmw/mwrender/objects.cpp` | `Objects::insertCreature` | `NpcMannequin.buildCreature` | rewrite |
| `apps/openmw/mwrender/creatureanimation.cpp` | `MWRender::CreatureAnimation` | idle on creature nif root | rewrite |
| `components/sceneutil/visitor.cpp` | `RemoveTriBipVisitor` | skip `"tri bip"` drawables | rewrite |
| `apps/openmw/mwclass/creature.cpp` | `Creature::adjustScale` | `ref.scale * CREA.XSCL` | rewrite |
| `apps/openmw/mwclass/door.cpp` | `Door::activate` | viewer **E** + `DoorSwing` pick | rewrite |
| `apps/openmw/mwworld/actiondoor.cpp` | `ActionDoor` | `DoorSwing.activateDoor` | rewrite |
| `apps/openmw/mwworld/worldimp.cpp` | `activateDoor` / `rotateDoor` / `processDoors` | `render.DoorSwing` | rewrite |
| `apps/openmw/mwworld/doorstate.hpp` | `MWWorld::DoorState` | `DoorSwing.State` | same |
| `apps/openmw/mwworld/worldimp.cpp` | `getMaxActivationDistance` | `DoorSwing.MAX_ACTIVATE` (192) | rewrite |
| `apps/openmw/mwworld/worldimp.cpp` | `getFocusObject` | camera ray vs door AABB | rewrite |
| `apps/openmw/mwworld/actionteleport.cpp` | `ActionTeleport` | `DoorSwing.InteriorTeleport` | rewrite |
| `apps/openmw/mwworld/cellref.cpp` | `getDestCell` | empty `destCell` → `LandRecord.cellGrid(DODT)` | rewrite |
| `apps/openmw/mwworld/worldimp.cpp` | `changeToCell` | `loadInterior` or `loadExterior` + `placeEye` at `DODT` | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `changeToInteriorCell` | dispose + rebuild cell | rewrite |
| `components/esm3/loadcell.hpp` | `ESM::Cell::isExterior` | `LoadedCell.interior` from `DATA` flags | same |
| `components/esm3/loadcell.hpp` | `ESM::Cell::getGridX/Y` | `LoadedCell.gridX/Y` | same |
| `components/esm/util.hpp` | `positionToExteriorCellLocation` | `LandRecord.cellGrid` `floor(x/8192)` | same |
| `components/esm3/loadland.hpp` | `ESM::Land` / `VHGT` | `esm.LandRecord` + `LandMesh` | rewrite |
| `apps/openmw/mwrender/renderingmanager.cpp` | `addCell` terrain | `LAND` layers + TES3 blendmaps | rewrite |
| `components/esmterrain/storage.cpp` | `getBlendmaps` TES3 | `LandMesh` 17→34 alpha | rewrite |
| `components/terrain/material.cpp` | `createPasses` | land layer blend + depth | rewrite |
| `apps/openmw/mwworld/cell.cpp` | TES3 exterior water | always on, height −1 | same |
| `components/sceneutil/waterutil.cpp` | `createWaterGeometry` | `render.WaterMesh` on cell root | rewrite |
| `apps/openmw/mwrender/water.cpp` | `createSimpleWaterStateSet` | blend, no cull, no depth write, water## flip | rewrite |
| `components/esm3/loadltex.hpp` | `ESM::LandTexture` / `LTEX` | `esm.LandTexture` (`INTV` + `DATA`) | same |
| `components/esm3/landrecorddata.hpp` | `mTextures` / `VTEX` | `LandRecord.textures` after transpose | rewrite |
| `components/esm3/loadland.cpp` | `transposeTextureData` | `EsmFile.decodeVtex` | same |
| `components/esmterrain/storage.cpp` | `getTextureName` | `LandMesh.textureName` | rewrite |
| `components/misc/constants.hpp` | `CellGridRadius` | `EsmFile.CELL_GRID_RADIUS` (1 → 3×3) | same |
| `apps/openmw/mwworld/scene.cpp` | `iterateOverCellsAround` | `loadExterior` one pass `|x-cx|<=1` | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `changeCellGrid` | one-shot 3×3, no walk recenter | rewrite |
| `apps/openmw/mwclass/door.cpp` | `Door::activate` empty `DNAM` | **E** → `loadExterior` | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `changeToExteriorCell` | dest grid + 3×3 around it | rewrite |
| `apps/openmw/mwworld/class.cpp` | `Class::defaultItemActivate` | **E** + item pick | rewrite |
| `apps/openmw/mwworld/actiontake.cpp` | `ActionTake` | unparent mesh, skip inventory | rewrite |
| `apps/openmw/mwworld/worldimp.cpp` | `World::deleteObject` | `SceneNode.removeFromParent` | rewrite |
| `apps/openmw/mwrender/objects.cpp` | `Objects::removeObject` | drop instance node | rewrite |
| `components/esm3/loadligh.hpp` | `ESM::Light::Carry` | `EsmObject.LIGH_CARRY` `0x002` | same |
| `apps/openmw/mwclass/container.cpp` | `Container::useAnim` | object kf on chest nif | rewrite |
| `apps/openmw/mwclass/container.cpp` | `Container::activate` | **E** + CONT pick | rewrite |
| `apps/openmw/mwworld/actionopen.cpp` | `ActionOpen` | play lid, skip GUI | rewrite |
| `apps/openmw/mwmechanics/character.cpp` | `CharacterController::onOpen` | `containeropen` once | rewrite |
| `components/nifosg/controller.cpp` | `NifOsg::KeyframeController` | `KfFile` sample → bone `SceneNode.local` | rewrite |
| `components/sceneutil/riggeometry.cpp` | `SceneUtil::RigGeometry` | skinned mesh on `SceneNode` | rewrite |
| `components/misc/resourcehelpers.cpp` | `correctActorModelPath` | `TexturePaths.correctActorModelPath` (VFS sees extra dirs) | same |
| `components/esm3/loadnpc.hpp` | `ESM::NPC` | `esm.EsmNpc` | same |
| `components/esm3/loadrace.hpp` | `ESM::Race` | `esm.EsmRace` | same |
| `components/esm3/loadbody.hpp` | `ESM::BodyPart` | `esm.EsmBodyPart` | same |

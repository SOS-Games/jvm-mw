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
| `apps/openmw/mwmechanics/character.cpp` | `movementStateToAnimGroup(WalkForward)` | `KfFile.play("walkforward", …)` | rewrite |
| `apps/openmw/mwrender/animation.cpp` | `ResetAccumRootCallback` (XY) | `NpcMannequin` zero `Bip01` / `root bone` XY | rewrite |
| `components/esm3/loadcrea.hpp` | `ESM::Creature` | `esm.EsmCreature` | same |
| `apps/openmw/mwclass/creature.cpp` | `Creature::insertObjectRendering` | `CellSceneBuilder` CREA place | rewrite |
| `apps/openmw/mwrender/objects.cpp` | `Objects::insertCreature` | `NpcMannequin.buildCreature` | rewrite |
| `apps/openmw/mwrender/creatureanimation.cpp` | `MWRender::CreatureAnimation` | idle on creature nif root | rewrite |
| `components/sceneutil/visitor.cpp` | `RemoveTriBipVisitor` | skip `"tri bip"` drawables | rewrite |
| `apps/openmw/mwclass/creature.cpp` | `Creature::adjustScale` | `ref.scale * CREA.XSCL` | rewrite |
| `components/esm3/loadlevlist.hpp` | `ESM::CreatureLevList` | `esm.EsmLevc` | same |
| `apps/openmw/mwmechanics/levelledlist.cpp` | `getLevelledItem` (creature) | `esm.LevelledCreatures` | rewrite |
| `apps/openmw/mwclass/creaturelevlist.cpp` | `CreatureLevList::insertObjectRendering` | `CellSceneBuilder` LEVC → `buildCreature` | rewrite |
| `components/esm3/aipackage.hpp` | `ESM::AIWander` / `AI_W` | `EsmNpc` / `EsmCreature` wander distance | same |
| `apps/openmw/mwmechanics/aiwander.cpp` | `wanderNearStart` / `getRandomPointAround` | `NpcMannequin` slide near spawn | rewrite |
| `apps/openmw/mwmechanics/steering.cpp` | `zTurn` / `getAngularVelocity` | wander yaw toward dest | rewrite |
| `components/esm3/loadpgrd.hpp` | `ESM::Pathgrid` / `REC_PGRD` | `esm.EsmPathgrid` | same |
| `Store<ESM::Pathgrid>::load` key | bind by interior name vs grid XY | rewrite |
| `components/sceneutil/pathgridutil.cpp` | `createPathgridGeometry` | `render.PathgridDebug` spheres + lines, F5 | rewrite |
| `apps/openmw/mwmechanics/pathgrid.hpp` | `PathgridGraph` | `esm.PathgridGraph` | rewrite |
| `components/misc/pathgridutils.hpp` | `Misc::getClosestPoint` | `PathgridGraph.closest` | rewrite |
| `apps/openmw/mwmechanics/aiwander.cpp` | `AiWander::fillAllowedPositions` | `PathgridGraph.allowed` | rewrite |
| `components/detournavigator/navmeshdb.cpp` | `NavMeshDb` sqlite tiles | `render.NavmeshDb` + `NavmeshCache` keep tiles | rewrite |
| `components/detournavigator/makenavmesh.cpp` | Recast bake | `render.NavmeshBaker` fallback if db misses | rewrite |
| `files/settings-default.cfg` `[Navigator]` | Recast defaults | same numbers in `NavmeshBaker` | same |
| `components/detournavigator/settingsutils.hpp` | `toNavMeshCoordinates` scale | scale GL verts, no OSG YZ swap | rewrite |
| `apps/openmw/mwworld/worldimp.cpp` | `getPathfindingAgentBounds` exterior default | one AABB agent | rewrite |
| `components/sceneutil/navmesh.cpp` | navmesh debug draw | `render.NavmeshDebug` + **F6** | rewrite |
| `components/detournavigator/makenavmesh.cpp` `dtCreateNavMeshData` | pnav → Detour tile | recast4j `NavMeshBuilder` | rewrite |
| `dtNavMeshQuery::findPath` / `findStraightPath` | Recast path | `render.NavmeshQuery` | rewrite |
| `PathFinder::buildPathByNavMesh` | wander fallback polyline | `NpcMannequin` straight dest | rewrite |
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
| `components/esmterrain/storage.cpp` | `getBlendmaps` TES3 | `LandMesh` 17×17 `GL_LINEAR` | rewrite |
| `BlendmapTexMat` 2× nudge | half-texel `16/17 + 0.5/17` | rewrite |
| `components/terrain/material.cpp` | `createPasses` | land layer blend + depth | rewrite |
| `apps/openmw/mwworld/cell.cpp` | TES3 exterior water | always on, height −1 | same |
| `components/sceneutil/waterutil.cpp` | `createWaterGeometry` | `render.WaterMesh` on cell root | rewrite |
| `apps/openmw/mwrender/water.cpp` | `createSimpleWaterStateSet` | blend, no cull, no depth write, water## flip | rewrite |
| `apps/openmw/mwrender/water.cpp` | `Water::updateWaterMaterial` shader | reflection FBO + water shader | rewrite |
| `apps/openmw/mwrender/water.cpp` | `Water::Reflection` | flipped RTT, 512 | rewrite |
| `files/shaders/compatibility/water.frag` | no-refract path | `ForwardRenderer` water fragment | rewrite |
| `files/data/textures/omw/water_nm.png` | `water_nm.png` | `assets/textures/omw/water_nm.png` | same |
| `apps/openmw/mwrender/water.cpp` | `Water::Refraction` | second RTT, 512, clip keep below | rewrite |
| `Reflection::calcNodeMask` detail 2 | skip `SceneNode.actor` in reflection RTT | rewrite |
| `[Water] small feature culling pixel size = 20` | RTT AABB pixel test | rewrite |
| `files/shaders/compatibility/water.frag` | `@waterRefraction` path | `ForwardRenderer` water mix refraction | rewrite |
| `files/shaders/compatibility/water.frag` | `sunSpec.a` visibility | water specular × `ClearCycle.sunVis` | rewrite |
| `apps/openmw/mwrender/water.cpp` | `Water::isUnderwater` | camera TES3 z `<` −1 | rewrite |
| `apps/openmw/mwrender/fogmanager.cpp` | underwater fog | exterior fog while submerged | rewrite |
| `apps/openmw/mwrender/sky.cpp` | `SkyManager::create` atmosphere | `render.SkyAtmosphere` camera-relative nif | rewrite |
| `apps/openmw/mwrender/skyutil.cpp` | `CameraRelativeTransform` | zero view translation when drawing sky | rewrite |
| `files/shaders/compatibility/sky.frag` | `paintAtmosphere` | `ForwardRenderer` sky emission × vertex alpha | rewrite |
| `apps/openmw/mwrender/skyutil.cpp` | `ModVertexAlphaVisitor` Atmosphere | `MeshGpu.applyAtmosphereVertexAlpha` | rewrite |
| `apps/openmw/mwrender/sky.cpp` | `SkyManager::create` clouds | `render.SkyClouds` camera-relative nif | rewrite |
| `apps/openmw/mwrender/skyutil.cpp` | `CloudUpdater` | `SkyClouds` tex / emission / UV from hour | rewrite |
| `files/shaders/compatibility/sky.frag` | `paintClouds` | `ForwardRenderer` sky pass 2 | rewrite |
| `apps/openmw/mwrender/skyutil.cpp` | `ModVertexAlphaVisitor` Clouds | `MeshGpu.applyCloudsVertexAlpha` | rewrite |
| `apps/openmw/mwrender/skyutil.cpp` | `CelestialBody` / `Sun` | `render.SkySun` camera-relative quad | rewrite |
| `apps/openmw/mwrender/skyutil.cpp` | `createTexturedQuad` | `SkySun` 1×1 × 450 at 1000 | rewrite |
| `files/shaders/compatibility/sky.frag` | `paintSun` | `ForwardRenderer` sky pass 4 | rewrite |
| `apps/openmw/mwrender/renderingmanager.cpp` | `setSunDirection` midday | freeze TES3 `(0,-75,400)` | rewrite |
| `apps/openmw/mwworld/weather.cpp` | `TimeOfDayInterpolator` | `render.ClearCycle` | rewrite |
| `apps/openmw/mwworld/weather.cpp` | sun orbit / `getSunPercentage` | `ClearCycle` hour → disc + light | rewrite |
| `files/shaders/compatibility/sky.frag` | `paintAtmosphereNight` | `ForwardRenderer` sky pass 1 | rewrite |
| `apps/openmw/mwrender/skyutil.cpp` | `ModVertexAlphaVisitor` Stars | `MeshGpu.applyStarsVertexAlpha` | rewrite |
| `components/esm3/loadltex.hpp` | `ESM::LandTexture` / `LTEX` | `esm.LandTexture` (`INTV` + `DATA`) | same |
| `components/esm3/landrecorddata.hpp` | `mTextures` / `VTEX` | `LandRecord.textures` after transpose | rewrite |
| `components/esm3/loadland.cpp` | `transposeTextureData` | `EsmFile.decodeVtex` | same |
| `components/esmterrain/storage.cpp` | `getTextureName` | `LandMesh.textureName` | rewrite |
| `components/misc/constants.hpp` | `CellGridRadius` | `EsmFile.CELL_GRID_RADIUS` (2, corners cut) | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `iterateOverCellsAround` | `loadExterior` 5×5 minus corners | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `playerMoved` | exterior walk → maybe recenter | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `getNewGridCenter` | `LandRecord.newGridCenter` Chebyshev + 1024 | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `changeCellGrid` | 5×5-minus-corners around camera cell, keep eye | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `requestChangeCellGrid` | walk load without overlay | rewrite |
| `apps/openmw/mwworld/cellpreloader.cpp` | `CellPreloader` work queue | worker `loadExterior` + GL step | rewrite |
| `Resource::Profiler` / osg F3 stats | `debug.FrameProfiler` CPU sections + F3 keys | rewrite |
| OSG `VIEW_FRUSTUM_CULLING` | `ForwardRenderer` mesh AABB vs `cam.frustum` | rewrite |
| `[Camera] small feature culling pixel size = 2` | main-view AABB pixel test vs framebuffer height | rewrite |
| `[Camera] viewing distance = 7168` | skip small non-terrain AABB beyond 7168; longest axis ≥ 128 stays | rewrite |
| `Resource::ImageManager::getImage` | `GpuCache` intern DDS by corrected VFS path | rewrite |
| `Terrain::TextureManager::getTexture` | `LandMesh` uses `GpuCache` | rewrite |
| `Resource::SceneManager::getTemplate` | `GpuCache` static NIF `MeshGpu` template intern | rewrite |
| `MovementSolver::move` / `traceDown` | `BulletWorld` player capsule sweeps | rewrite |
| `Stepper` `sStepSizeUp` 34 / `sStepSizeDown` 62 | `BulletWorld` dock/stair step | rewrite |
| `sMaxSlope` 46 / `sGroundOffset` 1 | `BulletWorld` walkable floor | same |
| `HeightField` TES3 65×65 | `LandRecord` bilinear + `CollisionWorld` | rewrite |
| `btCollisionWorld` + dispatcher / dbvt | `render.BulletWorld` (JNI world) | rewrite |
| `HeightField` / `btHeightfieldTerrainShape` | `BulletWorld` land tris (Y-up GL) | rewrite |
| `BulletNifLoader` RootCollisionNode / NC / NCC | `CollisionMesh` tris from NIF | rewrite |
| `BulletNifLoader` instance in `btCollisionWorld` | `BulletWorld` World bodies from `CollisionMesh` | rewrite |
| `apps/openmw/mwclass/door.cpp` | `Door::activate` empty `DNAM` | **E** → `loadExterior` | rewrite |
| `apps/openmw/mwworld/scene.cpp` | `changeToExteriorCell` | dest grid + 5×5-minus-corners | rewrite |
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

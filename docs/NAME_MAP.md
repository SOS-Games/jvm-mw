# Name map

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Status: `same` = keep the C++ name, `rewrite` = new Java type (no OSG/MyGUI 1:1), `not ported`.

| OpenMW path | C++ symbol | Java | Status |
| --- | --- | --- | --- |
| `components/nif/nifstream.hpp` | `Nif::NIFStream` | `io.github.jvmmw.nif.NifStream` | same |
| `components/nif/niffile.hpp` | `Nif::NIFFile` / `Reader` | `io.github.jvmmw.nif.NifFile` | same |
| `components/nif/niftypes.hpp` | `Nif::NiTransform` | `io.github.jvmmw.nif.NiTransform` | same |
| `components/nif/node.hpp` | `Nif::NiNode` | `io.github.jvmmw.nif.NiNode` | same |
| `components/nif/node.hpp` | `Nif::NiTriShape` | `io.github.jvmmw.nif.NiTriBasedGeom` | same |
| `components/nif/data.hpp` | `Nif::NiTriShapeData` | `io.github.jvmmw.nif.NiTriShapeData` | same |
| `components/nif/property.hpp` | `Nif::NiTexturingProperty` | `io.github.jvmmw.nif.NiTexturingProperty` | same |
| `components/nif/texture.hpp` | `Nif::NiSourceTexture` | `io.github.jvmmw.nif.NiSourceTexture` | same |
| `components/nifosg/nifloader.cpp` | `NifOsg::Loader` | `io.github.jvmmw.render.NifSceneBuilder` | rewrite |
| `components/sceneutil/` | `osg::Node` / `SceneUtil::*` | `io.github.jvmmw.render.SceneNode` | rewrite |
| `components/bsa/bsafile.hpp` | `Bsa::BSAFile` | `io.github.jvmmw.bsa.BsaArchive` | same |
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
| `apps/openmw/mwclass/classmodel.hpp` | `getClassModel` | `esm.EsmObject` lookup | rewrite |
| `components/misc/resourcehelpers.cpp` | `isHiddenMarker` | `EsmFile.isHiddenMarker` | same |
| `components/esm3/loadligh.hpp` | `ESM::Light::LHDTstruct` | `esm.EsmObject` light fields | same |
| `components/sceneutil/lightutil.cpp` | `addLight` / `createLightSource` | `render.CellSceneBuilder` + `CellLight` | rewrite |
| `components/sceneutil/lightutil.cpp` | `configureLight` | `CellLight` attenuation | rewrite |
| `apps/openmw/mwrender/renderingmanager.cpp` | `configureAmbient` | `CellLighting` from cell `AMBI` | rewrite |
| `files/shaders/lib/light/lighting.glsl` | `lcalcIllumination` | `ForwardRenderer` GLSL | rewrite |

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

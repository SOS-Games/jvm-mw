# JVM-MW

**JVM-MW** — a desktop Java host for OpenMW data.

This is an unofficial GPLv3 translation of [OpenMW](https://openmw.org) C++ into Java (libGDX window/input/Scene2D, owned OpenGL renderer — never `ModelBatch`). It is not affiliated with OpenMW or Bethesda.

You must own *The Elder Scrolls III: Morrowind*. Game ESM/BSA/meshes/textures are **not** shipped in this repository.

Parser behavior follows OpenMW tag **`openmw-0.51.0`** (`f4bec41444214a7903bebd178389ca22ca13f646`).

## Run

JDK 21, Morrowind **Data Files**, optional OpenMW `navmesh.db`. Full steps: [docs/setup.md](docs/setup.md).

```bat
gradlew.bat lwjgl3:run
```

```bat
gradlew.bat :core:debugCli --args="help"
```

In the viewer, **F3** writes `build/debug-snapshot.txt`. Coding-agent notes: [AGENTS.md](AGENTS.md).

## License

[GPL-3.0](LICENSE). Ported files keep OpenMW copyright notices. libGDX is Apache 2.0 (not relicensed).

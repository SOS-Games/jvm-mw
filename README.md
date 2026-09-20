# JVM-MW

**JVM-MW** — a desktop Java host for OpenMW data.

This is an unofficial GPLv3 translation of [OpenMW](https://openmw.org) C++ into Java (libGDX window/input/audio/Scene2D, owned OpenGL renderer). It is not affiliated with OpenMW or Bethesda.

You must own *The Elder Scrolls III: Morrowind*. Game ESM/BSA/meshes/textures are **not** shipped in this repository.

## OpenMW pin

Parser behavior follows OpenMW tag **`openmw-0.51.0`** (`f4bec41444214a7903bebd178389ca22ca13f646`).

## Phase 30 (current)

Town shows an fps overlay (F4) and F3 writes `fps=` / `ms.update=` / draw counts so later speed work has numbers. Phase 29 walk-grid stays. See [docs/phase30-perf-debug.md](docs/phase30-perf-debug.md). Phase 29 background grid: [docs/phase29-bg-grid.md](docs/phase29-bg-grid.md).

```bat
gradlew.bat lwjgl3:run
```

Point at your Morrowind `Data Files` folder (not committed) with one of:

- environment `JVMMW_DATA`
- `-Djvmmw.data=...`
- gitignored `local.properties` with `jvmmw.data=...`
- optional extra folders: `jvmmw.data.extra=...` (`;` separated)

Vanilla meshes are extracted from `Morrowind.bsa` into gitignored `testdata/` on first run. Do not commit `testdata/`, `build/`, or `.gradle/`.

Headless dumps for agents (NIF tree, interior fog/spawn, inbound door):

```bat
gradlew.bat :core:debugCli --args="help"
```

In the viewer, **F3** writes `build/debug-snapshot.txt`. See [AGENTS.md](AGENTS.md).

## License

[GPL-3.0](LICENSE). Ported files keep OpenMW copyright notices. libGDX is Apache 2.0 (not relicensed).

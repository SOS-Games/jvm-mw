# Setup

What you need to run JVM-MW. This is a viewer, not a full game. You must own *The Elder Scrolls III: Morrowind*. ESM, BSA, meshes, and textures are **not** in this repo. Do not commit them.

Parser behavior follows OpenMW **0.51.0**. You do not need an OpenMW source tree to run.

## Machine

- **JDK 21** on `PATH` (`java -version` should say 21). Gradle uses that for compile and run.
- **Git**, to clone the repo.
- **Network** on the first Gradle run (wrapper fetches Gradle 9.3.1 and Maven jars).
- A GPU that can open an **OpenGL 3.3** window (1280×720).
- Windows, Linux, or macOS. On macOS the run task already passes `-XstartOnFirstThread`.

From the repo root:

```bat
gradlew.bat lwjgl3:run
```

Linux / macOS: `./gradlew lwjgl3:run`. Always run from the repo root so `local.properties` is found. First launch extracts meshes from the BSA into gitignored `testdata/`. Town (Seyda Neen) is the default view.

Headless dumps (no window):

```bat
gradlew.bat :core:debugCli --args="help"
```

## Morrowind data (required)

Point at the game’s **Data Files** folder — the one that contains `Morrowind.esm` and `Morrowind.bsa`. Tribunal and Bloodmoon are not required for the HUD cells (Census, Cave, Nix, Guild, Town, Zain, Manor, Club).

Typical locations:

- Steam: `...\Steam\steamapps\common\Morrowind\Data Files`
- GOG: `...\GOG Games\Morrowind\Data Files`

Set it in gitignored `local.properties` at the repo root (forward slashes are fine on Windows):

```properties
jvmmw.data=D:/Games/Morrowind/Data Files
```

Same key as env `JVMMW_DATA` or JVM `-Djvmmw.data=...`. If none of those are set, the viewer looks in the current directory and will fail to load the ESM.

VFS order: `Morrowind.bsa`, then loose files under that Data Files folder, then extra folders below. Later folders win the same mesh path.

## Extra folders (optional)

Loose mod dirs, `;` separated. Used for things vanilla BSA does not have (for example OpenMW Containers Animated, so chest lids lift with **E**).

```properties
jvmmw.data.extra=D:/morrowind_mods/morrowind-starter-pack/FurnitureandClutter/OpenMWContainersAnimated/Containers Animated
```

Env `JVMMW_DATA_EXTRA` or `-Djvmmw.data.extra=...`. Missing folders are skipped (a line prints to the console). This is not a full OpenMW `data=` / plugin list: still one ESM and one BSA.

## Navmesh database (optional, recommended)

Town’s green **F6** carpet and Detour paths around shacks prefer OpenMW’s `navmesh.db` (umo / `openmw-navmeshtool`). It is often ~2 GB. Do not commit it.

Default search:

- Windows: `Documents\My Games\OpenMW\navmesh.db`
- Linux: `~/.config/openmw/navmesh.db`

Override with `jvmmw.navmesh` / `JVMMW_NAVMESH` / `-Djvmmw.navmesh=...`.

Without that file, the viewer can bake a smaller Recast mesh from land and shack collision for the camera cell. The 5×5 Town carpet and crab paths around buildings expect the OpenMW db. If Dump shows `src=load`, wait; `src=db` means the file was used.

Generate it with OpenMW 0.51 (umo or navmeshtool) the same way you would for OpenMW itself. This project does not ship that tool.

## Debug knobs (optional)

`debug.DebugVars` values can be overridden without a rebuild:

```properties
jvmmw.debug.wanderSpeed=3
jvmmw.debug.creaWanderSpeed=3
jvmmw.debug.wanderTurn=270
jvmmw.debug.wanderRadius=900
jvmmw.debug.nodeWanderRadius=900
jvmmw.debug.wanderFrequency=5
jvmmw.debug.idleDuration=0
```

Env is `JVMMW_DEBUG_wanderSpeed` (dots to underscores). `-Djvmmw.debug.wanderSpeed=...` also works. Dump / F4 print the live values. Vanilla-ish wander is speed 1, turn 900, both radii 0 (ESM `AI_W`), frequency 1.

## Viewer keys (short)

- WASD walk (Town land/docks; ceilings stop the camera)
- HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** / **Zain** / **Manor** / **Club**, hour slider
- **E** door / chest / take, **R** the looked-at NPC escorts you
- **F3** Dump (`build/debug-snapshot.txt` + clipboard)
- **F4** fps overlay
- **F5** pathgrid (off at load)
- **F6** Recast carpet (off at load)

Wait until overlay `n=60` before treating fps as settled. Gradle Ctrl+C does not save a dump.

## Do not commit

`local.properties`, `testdata/`, `build/`, `.gradle/`, Bethesda ESM/BSA/meshes, OpenMW `navmesh.db`.

## If it fails

| What you see | Likely cause |
| --- | --- |
| Parse / missing `Morrowind.esm` | `jvmmw.data` is not the Data Files folder, or you ran Gradle from the wrong cwd |
| Empty / missing meshes | `Morrowind.bsa` not next to the ESM |
| Window will not open | GPU / driver below OpenGL 3.3, or JDK is not 21 |
| `src=none` / no F6 carpet | No `navmesh.db` and bake did not run yet |
| Chest **E** does not lift the lid | No extra folder with container `x*.kf` |
| First run is slow | Gradle download, then BSA extract into `testdata/` |

# Phase 30: Frame perf debug for agents

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with the *idea* of in-game F3 stats (`scripts/HOWTO-benchmark.md`, `apps/openmw/engine.cpp` `initStatsHandler` / `Resource::Profiler`). Do **not** port OSG stats, `osg_stats.py`, or Lua profiler. Path B timers and counters only. No other-LLM prompts: those paths are read from the pin.

## Goal

Same Path B viewer. Phase 29 walk-grid stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged. Town still the default load. **Do not make the 5×5 faster this slice.**

**Town shows where the frame goes.** A compact overlay plus F3 `build/debug-snapshot.txt` print stable `key=value` lines so a later agent can tell CPU update, water RTT, terrain draws, and walk-load hitch apart without guessing.

## Why this slice

Phase 29 grew the live grid from 9 cells to 21. The harbor is heavier (more land layers, refs, NPCs, and two water RTTs that redraw the scene). There is no frustum cull, no incremental tiles, and F3 today is camera/fog only. The next speed work needs numbers, not hunches.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- A small `io.github.jvmmw.debug.FrameProfiler` (or same-package name): CPU `System.nanoTime` sections on the GL thread, draw/tri counters, rolling 60-frame fps / ms / max ms. No `GL_TIME_ELAPSED` queries, Tracy, or `glFinish`.
- Count **CPU submit** around the real work, not GPU elapsed:
  - `walkStep` — `pumpWalkLoad` / `cellBuilder.step` when a walk GPU step runs
  - `update` — `cellBuilder.update` (idle kf, skin, `updateWorld`)
  - `rttRefract` / `rttReflect` — water 512 RTTs
  - `sky` / `terrain` / `opaque` / `water` / `alpha` — main framebuffer
  - `hud` — Scene2D
  - `frame` — whole `render()`
- Draw counters while drawing: `draws`, `tris` (indexCount/3), split `terrain` / `opaque` / `alpha` / `rtt` if cheap. Scene size: `meshes`, `placed`, `npc`, `crea`, `lights`, `landTiles` (21 on Town).
- Last walk load: `walkParseMs` (worker), `walkGpuMs` (begin/step/end sum), `walkSwapMs` (`end` + dispose old). Persist until the next walk.
- HUD: always-on compact Scene2D label, top-right, `Touchable.disabled` (do not steal look). fps, frame ms, max ms, sample count `n=`, draws, and the fattest section. Walk ms when a load just finished. **F4** hides/shows it (default on). Status line may also show `fps=`. HUD **Dump** (and **F3**) copies that text to the clipboard and writes `build/debug-snapshot.txt`. Gradle Ctrl+C does not save a dump. Treat fps as settled when `fpsSamples=60` / overlay `n=60`.
- **F3** keeps today’s camera/fog/grid lines and **appends** grep-stable perf keys to `build/debug-snapshot.txt` and the log. Overwrite the file as today. Example (names may match this shape):

```
fps=41.2 frameMs=24.3 frameMaxMs=38.1
ms.update=4.1 ms.rttRefract=5.8 ms.rttReflect=6.2 ms.terrain=2.0 ms.opaque=3.1 ms.water=0.4 ms.alpha=1.2 ms.hud=0.2
draws=1840 tris=920000 draws.terrain=210 draws.opaque=900 draws.alpha=700 draws.rtt=1840
meshes=2100 placed=1800 npc=40 crea=8 lights=120 landTiles=21
walkParseMs=850 walkGpuMs=2400 walkSwapMs=12
```

`draws.rtt` is the extra submits inside both water cameras (same scene, smaller target). Zero walk keys if no walk has run this session.
- Debug CLI unchanged except `help` can point at F3 perf. Headless has no fps. `exterior -2 -9` already lists per-tile refs for content size.
- Chair HUD stays unfogged. Never `ModelBatch`.

## Out of scope

- Speeding up the 5×5 (frustum cull, merge land draws, skip distant NPCs, keep overlapping tiles, smaller water RTT, drop radius)
- GPU timer queries / Tracy / OSG stats file / `osg_stats.py`
- Changing `CELL_GRID_RADIUS` or the 12 ms walk step budget
- Physics, navmesh, moons, weather, sunglare
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Town** (app default): Census `DODT`. Overlay shows fps and a non-zero `draws` / section ms. **F3** writes those keys. Walk **south** until a swap; F3 then has `walkParseMs` / `walkGpuMs`. **F4** hides the overlay. **Cell** still loads; chair unfogged.

```bat
gradlew.bat lwjgl3:run
```

## Pass / fail

- Town F3 file has `fps=` and `ms.update=` (or the listed section keys) with plausible numbers (`frameMs` in the same ballpark as `1000/fps`).
- Overlay visible by default; F4 toggles; look/WASD still live.
- Walk after a recenter fills walk ms keys. `glError=0`. Chair unfogged.

A faster harbor is **not** a pass. Missing F3 keys or a HUD that locks the mouse is a **fail**.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Resource::Profiler` / osg F3 stats | `debug.FrameProfiler` CPU sections + F3 keys | rewrite |

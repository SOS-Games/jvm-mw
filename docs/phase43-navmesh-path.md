# Phase 43: Detour path around shacks

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/pathfinding.cpp` (`buildPath` / `buildPathByNavMesh`), `components/detournavigator/makenavmesh.cpp` (`makeNavMeshTileData` / `dtCreateNavMeshData`), `components/detournavigator/findsmoothpath.hpp` (`findNearestPoly` / `findPath` / `findStraightPath`), `files/settings-default.cfg` `[Navigator]` (`max polygon path size`, `max smooth path size`, `max nav mesh query nodes`) and `[Game] default actor pathfind half extents`. No other-LLM prompts: those functions are in the pin.

This is the **`buildPathByNavMesh` split** of [nav-paths.md](nav-paths.md) **6**. Query the loaded Recast tiles. Do not change pathgrid wander when the graph is usable. Do not add Travel / Follow.

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 stay. **Wander dests that today walk a straight TES line (thin pathgrid, random `wanderRadius`) follow a Detour polyline on the F6 carpet instead, so they go around shacks.** Fargoth and other usable-graph NPCs still walk F5 edges.

HUD buttons unchanged (including **Zain**). Chair HUD unfogged. DebugVars wander knobs stay.

## Why this slice

Phase 42 put the walkable surface on screen. Phase 41 already routes people along the authored graph. The leftover hole is the straight-line fallback: beach crabs and anyone with two or fewer reachable nodes cut through shacks that the green carpet already goes around.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Add recast4j **detour** (same 1.5.7 as recast) on `core`. Never `ModelBatch`. No JNI, no Bullet, no second physics engine.
- When a `navmesh.db` tile is decoded, keep `rcPolyMesh` (not only detail tris). Convert it the way OpenMW `makeNavMeshTileData` does: `dtCreateNavMeshData` / recast4j `NavMeshBuilder` with the same default AABB agent (`29.28 28.48 66.5`), `walkableClimb` 34, tile XY from the sqlite row, no off-mesh links. Recast bake fallback tiles already have recast4j `PolyMesh`; convert those too.
- One live Detour mesh per worldspace (`sys::default` outdoors, lowercased interior name). It holds the **same tiles F6 already cached**. OpenMW’s `max tiles number = 1024` is too small for Town’s 5×5 (~2300 Recast tiles); init with at least **8192** tiles and `max polygons per tile = 4096`. Add tiles on the existing navmesh worker; swap the query mesh when a batch is ready. Query is not allowed until that mesh exists.
- Path query (main thread, must stay cheap): TES start/end → Recast `(x, height, y) * scale` (same as the overlay, no extra OSG YZ swap). `findNearestPoly` with half extents `4 * agent`. `findPath` then `findStraightPath` (`max polygon path size` / `max smooth path size` 1024, `max nav mesh query nodes` 2048). Unscale corners back to TES. Ground area only; ignore water / door / pathgrid area costs. Partial path is OK (use what Detour returned). Empty / mesh-not-ready → keep today’s straight dest.
- **Who uses it:** only the wander fallback that currently calls `pickStraightDest` (unusable graph, cluster ≤ 2, or A* miss). Dest pick stays the same random TES XY in `wanderRadius` from spawn. If Detour returns two or more corners, walk that polyline with the existing wander step / turn / `walkforward` / land-stick Z / arrive 8. If Detour returns nothing, straight line as today.
- **Who does not:** usable pathgrid wander (local cluster, far component trip, Census distance 0). Do not send Fargoth onto the Recast mesh this slice.
- If the carpet is still `src=load`, skip Detour that idle (straight line). Do not hitch the GL thread building tiles.
- Dump (`n=60`): `navPath=N` is the waypoint count of the last successful Detour query this cell (`0` if none yet). Overlay, F5, F6, `nav=` / `tiles=` / `src=` unchanged.
- Debug CLI: `navpath -2 -9` (or an interior name) prints `db=` / `tiles=` then one query from that cell’s inbound spawn to a point 512 TES east: `path=N` and `wp=` TES lines. No GL. Needs `navmesh.db` (or says `path=0`).
- Class comment on the query helper (plain English, per AGENTS). Update AGENTS.md: wander still pathgrid when the graph works; straight dests can Detour.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="navpath -2 -9"
gradlew.bat lwjgl3:run
```

## Out of scope

- Replacing pathgrid wander with Detour-first (OpenMW `buildPath` tries navigator first; we do not this slice)
- Shared `PathFinder`, Travel / Follow / Escort ([nav-paths.md](nav-paths.md) **5**, [AI packages](big-topics.md))
- Occupied dest / LOS ([nav-paths.md](nav-paths.md) **4**)
- Writing our own `navmeshdb`, OpenMW `wait until min distance`, water swim surface, off-mesh pathgrid links, per-actor agent sizes
- Player / camera pathfinding, drawing agent paths, changing DebugVars
- Filling the known cell-edge cracks in `navmesh.db` (`navdb` dump)

## Test cell

**Town** (app default). F5 on, F6 on. Fargoth still walks cyan edges, not through the Census office or the enclosed yard. A wanderer who is **not** on a usable graph (thin neighbouring `PGRD`, or cluster ≤ 2) walks around a shack that sits on the straight line to their dest, following the green carpet. Census clerks stay.

**Cave:** vermin with a usable tunnel graph stay on F5. If any creature is on the random-dest fallback, they follow the cave floor carpet instead of clipping the rock when Detour has a path.

Chair HUD still flies and has no wandering actors.

## Pass / fail

- Town `navPath>0` in Dump after a fallback wanderer has picked a dest with the carpet up (`src=db`). `glError=0`.
- Fargoth still pathgrid. Distance-0 NPCs stay. F5 / F6 still independent. Walk kf, land stick, fog, water, **Zain** halls, Chair unchanged.
- Debug CLI `navpath -2 -9` prints `path>0` when the db is present.

A street NPC cutting through the Census office is still a **fail** (wrong graph). Fargoth walking the Recast mesh instead of F5 is a **fail** (wrong slice). A fallback wanderer cutting through a street shack while F6 shows a way around it is a **fail**. `navPath=0` forever on Town with `src=db` after crabs have been walking is a **fail**. Detour hitching Town the way the old sync bake did is a **fail**. Crossing a `navdb` cell-edge crack is **not** a fail. Straight line while `src=load` is **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `dtCreateNavMeshData` / `makeNavMeshTileData` | recast4j `NavMeshBuilder` from pnav | rewrite |
| `dtNavMeshQuery::findPath` / `findStraightPath` | recast4j `NavMeshQuery` | rewrite |
| `PathFinder::buildPathByNavMesh` | wander fallback polyline | rewrite |

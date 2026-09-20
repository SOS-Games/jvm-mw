# Phase 41: Wander along the pathgrid

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwmechanics/aiwander.cpp` (`fillAllowedPositions` / `setPathToAnAllowedPosition` / `addNonPathGridAllowedPoints`), `apps/openmw/mwmechanics/pathgrid.cpp` (`isPointConnected` / `aStarSearch`), `components/misc/pathgridutils.hpp` (`getClosestPoint`). Occupied dest, hidden dest, and Detour stay out. No other-LLM prompts: those functions are in the pin.

This is slice **3** of [nav-paths.md](nav-paths.md). Overlay and CLI dump stay as Phase 40.

## Goal

Same viewer. Town / Cave / Zain stay. **Actors with a wander radius and a usable pathgrid walk along its edges instead of cutting a straight line.** Walkforward still plays. Distance 0 still stays. F5 still shows the graph.

HUD buttons unchanged (including **Zain**). Chair HUD unfogged.

## Why this slice

Phase 38 is OpenMW’s *no-pathgrid* fallback. Phase 40 put the graph in memory and on screen. Vanilla Seyda Neen has three components; Fargoth should stay in the town one, not walk through the Census yard.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Each actor uses **one** cell’s graph: interior `LoadedCell.pathgrid`, or the exterior `GridTile` that contains their **spawn** (`floor(tes / 8192)`). Do not merge the 5×5 into one graph. Do not use the center tile for a crab two cells over.
- Exterior dests are world TES: `grid * 8192 + local`. Interior dests are the stored local TES. Same as the overlay.
- Build connected components once per graph (undirected: an edge either way joins the nodes). Same component ⇔ OpenMW `isPointConnected` on vanilla two-way `PGRC`.
- Closest node: min TES distance² from spawn (local) to points. Empty graph: do not call it.
- **Use the grid** when all of these are true:
  - `AI_W` distance > 0
  - the graph has ≥ 2 points
  - the actor is not a pure water creature (`SWIMS` and not biped / fly / walk)
  - at least three nodes that can reach each other inside the node radius
- Range is ESM `AI_W`, capped by `DebugVars.nodeWanderRadius` when that is > 0. Random fallback dests use `wanderRadius` instead. A hop is allowed only when both nodes sit inside that radius of spawn **and** of each other. Same component as the closest in-range node.
- **Two or fewer** nodes in that reachable cluster: do not walk the graph; use random dests. **Zero** nodes, `< 2` points on the cell graph, or pure water: same random dests.
- Pick a random allowed dest. A* along edges from the closest node to the current pose to the closest node to that dest. Drop the first node (already next to them). If start and goal are the same index, walk straight to the dest. Empty A* → drop that dest and pick another; if none left, fall back to a straight line this idle.
- Walk the polyline with the existing wander step / turn / `walkforward` / land-stick Z / stop within 8 / 2–5 s pause. DebugVars speed / turn / frequency stay. Never `ModelBatch`.
- Distance 0 still stays. Walk-grid swap: new mannequins rebuild the allowed list from the new cell’s graph (spawn is still ESM placement).
- Overlay, F5, and `pgrd` dump unchanged. Debug CLI `npc` / `crea` may add `allowed=N` next to `wander=` (0 means random dests).
- Class comment on any new graph helper (plain English, per AGENTS). `NpcMannequin` comment: they follow the cell’s spheres when the graph is usable.

```bat
gradlew.bat :core:debugCli --args="npc fargoth"
gradlew.bat :core:debugCli --args="pgrd -2 -9"
gradlew.bat lwjgl3:run
```

## Out of scope

- Occupied dest / line-of-sight skip ([nav-paths.md](nav-paths.md) 4)
- Shared `PathFinder`, Travel / Follow ([nav-paths.md](nav-paths.md) 5)
- Detour / Recast ([nav-paths.md](nav-paths.md) 6)
- Actor collision, swim as movement, fly 3D
- Changing overlay colours per component
- Duration / time-of-day / idle2–9

## Test cell

**Town** (app default). F5 on. Fargoth and street NPCs walk the cyan edges, not through shacks. They must not enter the enclosed three-node yard (52–54) or the ship/Census cluster (48–51, 84–90) unless they spawned there. Census clerks (distance 0) stay. Beach crabs in a thin neighbouring grid may still straight-line if that tile has `< 2` nodes in range.

**Cell** (Census): distance-0 NPCs stay idle. A wanderer in the office, if any, stays on the interior 20-node graph.

**Cave:** vermin with wander follow the tunnel graph instead of clipping through walls when nodes exist.

Chair HUD still flies and has no wandering actors.

## Pass / fail

- Town wanderers with a usable graph follow edges. `glError=0`. F5 still hides the overlay.
- Distance-0 NPCs stay. Walk kf, land stick, fog, water, **Zain** halls, Chair unchanged.

A street NPC cutting through the Census office or the enclosed yard is a **fail**. Everyone still using a straight line on Seyda Neen `(-2, -9)` while `pgrd=95` is a **fail**. Clipping a crate that sits on an edge is **not** a fail. A TES `nodeWanderRadius` shrinking the allowed set is **not** a fail (they should still A* between the nearby nodes).

## Other-LLM claims

None.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `PathgridGraph` | components + A* on `EsmPathgrid` | rewrite |
| `Misc::getClosestPoint` | nearest node to spawn / dest | rewrite |
| `AiWander::fillAllowedPositions` | wander node set | rewrite |
| `PathgridGraph::aStarSearch` | polyline of node TES | rewrite |
| `AiWander::wanderNearStart` | keep Phase 38 fallback | rewrite |

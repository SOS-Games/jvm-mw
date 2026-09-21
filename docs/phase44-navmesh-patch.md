# Phase 44: Patch cracked Recast tiles from sqlite

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with Recast tile size already in `NavmeshBaker` / `NavmeshDb` (`128 * 0.2` nav, ~870 TES) and the `navdb` edge samples in `debug.NavmeshDbDump`. No other-LLM prompts: this is our fill of a **db-mesh-gap**, not an OpenMW feature.

This is a leftover of [nav-paths.md](nav-paths.md) **6** (cell-edge cracks). Do not change wander. Do not write `navmesh.db`.

## Goal

Same viewer. Town / Cave / Zain / F5 / F6 / Detour wander stay. **After sqlite tiles are in, the worker rebakes only Recast tiles whose walkable tris actually fail coverage (the thinner/fatter lines, sometimes a run of 2–5 tiles along an edge). Fine tiles on the same TES cell border stay from the db.** Patched tiles stay in the process cache when the 5×5 moves, the same way sqlite tiles already do.

HUD buttons unchanged (including **Zain**). Chair HUD unfogged. DebugVars wander knobs stay.

## Why this slice

`navdb` proved Town’s lines are holes *inside* present Recast tiles (`recastMissing=0`, verdict `db-mesh-gap`). Rebaking every tile that touches a TES cell edge would redo the good half. A thin homemade Detour tile cannot sit off the 870 TES grid. Replace only the failing Recast tiles.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins. Never `ModelBatch`. No JNI, no second physics engine. Do not commit `navmesh.db`.

- **SQL first, then patch.** Same sqlite worker as today (`loadMissing` / overlay / Detour add). `src=load` only while that SQL batch is running; `src=db` as soon as sqlite is in. Detect failing Recast tiles and bake them on a **second** background thread so Recast never delays the next SQL ring or hitch the GL thread. Missing carpet is fine until that bake finishes.
- **Which tiles:** Recast tiles (~870 TES) that fail the same kind of coverage `navdb` already uses: walkable nearby but the sample is not on a tri. Sample TES cell borders in the loaded 5×5 (or interior hull), and a few short insets (so a fat crack can mark 2 tiles inward). Map uncovered samples to Recast XY. Bake **only those XY**. Skip tiles that touch the seam but cover it. A run of 2–5 failing tiles along an edge is expected — bake each failure, not the whole edge. Ocean / empty wilderness is not a failure.
- **Bake:** one Recast tile AABB from land + `CollisionWorld` (same agent / Recast numbers as today). Output in **sqlite Recast space** (`tesSpace=true`, TES `(x, height, y) * scale`) so the tile joins the live Detour mesh. `removeTile` then `addTile` at that XY. Queue overlay tris for F6 (additive is OK; do not leave Detour with two tiles at the same XY).
- **Keep them.** Mark the XY patched in `NavmeshCache` so a later 5×5 does not fetch the cracked sqlite blob again and does not rebake. Tiles stay when TES cells unload, same as Phase 42’s keep. HUD Town / Cave may keep the in-memory mesh (process cache); do not write the db.
- If `navmesh.db` is missing, today’s full-cell bake fallback is enough — no patch pass.
- Dump (`n=60`): `nav=` / `tiles=` / `src=` unchanged in meaning (`src=db` when sqlite was used; patch does not keep `src=load`). Add `patch=N` (Recast tiles replaced this world, `0` if none). F5 / F6 / `navPath=` unchanged.
- Debug CLI `navdb` stays headless: also print `patch tx= ty=` for Recast tiles that would bake (coverage only, no Recast). Viewer still does the bake.
- Class comment on the patch step (plain English). Update AGENTS.md: sqlite first; only failed Recast tiles are rebaked; patches persist across walk.

```bat
gradlew.bat compileJava
gradlew.bat :core:debugCli --args="navdb -2 -9"
gradlew.bat lwjgl3:run
```

## Out of scope

- Rebaking every Recast tile that merely touches a TES cell edge
- A Detour tile smaller than the 870 TES grid / a second NavMesh
- Writing `navmesh.db` / porting navmeshtool
- Detour-first wander, water swim, off-mesh links ([nav-paths.md](nav-paths.md) leftover of 6)
- Dropping Recast tiles when a TES cell leaves the 5×5 (already out since 42)
- Changing DebugVars, Fargoth’s pathgrid, Chair HUD

## Test cell

**Town** (app default). F6 on. Wait until `src=db`. The magenta cell-edge lines from `navdb` should fill with green on the **bad** Recast tiles only. Walk far enough for a 5×5 swap and back: those patches stay (holes do not return). Fargoth still F5. Crabs still Detour on the carpet, including across a patched seam when the bake covers it.

Chair HUD still flies.

## Pass / fail

- After Town `src=db`, `patch>0` and F6 no longer shows the long empty lines on the edges `navdb` called `seam`. `glError=0`.
- Recast tiles on the same TES border that already covered stay sqlite (not a full-edge rebake). A 2–5 tile run of holes along an edge all get patched.
- Walk away and back: patched XY are not reloaded from sqlite; F6 stays filled. No Town hitch like the old sync bake.
- Fargoth pathgrid, distance-0 clerks, F5 / F6 independent, **Zain**, Chair unchanged.

Baking every seam-touching Recast tile is a **fail**. Dropping patches when the walk grid moves is a **fail**. Replacing a good db tile is a **fail**. `patch=0` forever on Town with `src=db` and visible edge cracks is a **fail**. Crossing a seam that the bake still cannot cover (no collision / water) is **not** a fail.

## Other-LLM claims

None.

## NAME_MAP rows

None (this fill is ours; sqlite / Recast / Detour rows already exist).

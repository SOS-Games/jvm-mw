# Phase 38: Cheap wander

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/aipackage.hpp` / `aipackage.cpp` (`AIWander`, subrecord `AI_W`), `apps/openmw/mwclass/npc.cpp` / `creature.cpp` (`AiSequence::fill` from the record’s package list), `apps/openmw/mwmechanics/aisequence.cpp` (empty list → no package), and `apps/openmw/mwmechanics/aiwander.cpp` (`wanderNearStart` / `getRandomPointAround` when there is no pathgrid). Navmesh and `PGRD` stay out. No other-LLM prompts: the binary layout and “distance 0 stays put” are in the pin.

## Goal

Same viewer. Town / Cave / Zain / walk stay. **Actors with an `AI_W` distance greater than 0 slide around their spawn.** Idle bones keep playing. They do not switch to a walk cycle.

Census clerks with distance 0 stay. Beach vermin and anyone with a real wander radius shuffle. They clip shacks and each other. That is this slice.

HUD buttons unchanged (including **Zain**). Chair HUD unfogged.

## Why this slice

Phase 37 filled Town with idle creatures. They still stand on the marker. Full AI packages and navmesh are [big-topics](big-topics.md). Walk-cycle animation is a later small slice — after they actually move. Vanilla wander used a **pathgrid**, not a navmesh; this slice copies only the no-pathgrid fallback: a random TES XY near spawn, then a straight line.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Parse `AI_W` on `NPC_` and `CREA`. Layout: `int16` distance, `int16` duration, `uchar` timeOfDay, `uchar idle[8]`, `uchar` shouldRepeat (14 bytes; skip leftover padding). Keep the **first** wander package. Skip `AI_T` / `AI_F` / `AI_E` / `AI_A` / `AIDT`. Duration, time-of-day, idle weights, and shouldRepeat are unused.
- Empty package list → no wander (OpenMW does not invent one). Distance **0** → stay at spawn (many town NPCs). Distance **> 0** → cheap wander.
- `LEVC` spawns use the picked `CREA`’s packages, not the list id.
- Origin is the ESM ref position. After a walk-grid swap, new mannequins start there again (picks stay via Phase 37 `pickOrRemember`).
- Pick a dest in TES XY: radius `(0.2 + rand * 0.8) * distance` around spawn, heading `rand * 2π` around TES Z (`getRandomPointAround`). Straight line. Face the move (`atan2(dx, dy)` so heading 0 is +TES Y). Speed **80** TES units/s (no GMST this slice). Stop within **8**, then idle **2–5** s, then pick again. First dest after **0.5–2** s so Town is not frozen on load.
- Outdoor: stick TES Z to the same land bilinear as the player (`CollisionWorld.landHeight`). Interior / no land: keep spawn TES Z. Fly and swim flags still use XY at that height (no 3D fly, no swim).
- Tick in `NpcMannequin.update` (already called from the cell scene). Idle kf keeps playing. `setActorLocal` each move. Never `ModelBatch`. Class comment on any new type (plain English, per AGENTS).
- Debug CLI: `npc` / `crea` print `wander=<distance>` (`0` if none). No new command.

```bat
gradlew.bat :core:debugCli --args="npc sellus gravius"
gradlew.bat :core:debugCli --args="npc fargoth"
gradlew.bat :core:debugCli --args="crea mudcrab"
gradlew.bat lwjgl3:run
```

## Out of scope

- Walk / run kf groups (they moonwalk on idle)
- Pathgrid (`PGRD`), Detour, obstacle evade, “hidden dest”
- Travel / Follow / Escort / Activate
- Actor-actor or actor-world collision (still walk through people; they clip walls)
- Water as a floor, destination-is-water skip, GMST walk speed
- Duration / time-of-day / idle2–9 selection
- Combat, dialogue

## Test cell

**Town** (app default). Watch the Bitter Coast: crabs and rats should leave their markers. **Fargoth** (if `wander>0`) should leave the crate. **Sellus Gravius** and other Census clerks stay.

**Cell** (Census): distance-0 NPCs do not roam the office.

**Cave** / **Zain** / **Nix**: kit and spawn unchanged. A vermin with wander may slide through kit; that is not a fail.

Chair HUD still flies and has no wandering actors.

## Pass / fail

- Town vermin with `wander>0` move. Distance-0 NPCs stay. `glError=0`.
- Reload Town / walk-grid swap: they restart from spawn (creature type still remembered).
- Census, Cave, Zain, water, fog, Chair unchanged.

Standing still on every marker that `npc`/`crea` reports `wander>0` is a **fail**. Census clerks leaving the office is a **fail**. Moonwalking and clipping a shack are **not** fails.

## Other-LLM claims

None.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::AIWander` / `AI_W` | `EsmNpc` / `EsmCreature` wander distance | same |
| `AiWander::wanderNearStart` / `getRandomPointAround` | slide toward a TES XY near spawn | rewrite |

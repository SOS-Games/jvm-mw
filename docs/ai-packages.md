# AI packages

This is a **big topic**, not a phase. Do **not** implement from this file. Split **one** row into [next-topics.md](next-topics.md), then spec that slice after the user picks it. The leftover stays in [big-topics.md](big-topics.md).

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/aipackage.*` (the five subrecords), `apps/openmw/mwmechanics/aisequence.cpp` (`fill`, front package is active, repeat goes to the back), `aiwander.cpp`, `aitravel.cpp`, `aifollow.cpp`, `aiescort.cpp`, `aiactivate.cpp`. The shared walk is [nav-paths.md](nav-paths.md) **5**, not a second pathfinder.

## What Town has now

Each placement copies the package list. The **front** row is active. A front `AI_W` uses that distance: 0 stays, and a distance above 0 walks the cell pathgrid when the reachable cluster is big enough ([phase 41](phase41-pathgrid-wander.md)); otherwise a random point near spawn, along Detour when the carpet is ready ([phase 43](phase43-navmesh-path.md)). They play `walkforward` while moving ([phase 39](phase39-walk-cycle.md)). Any other front kind stands, even when a later wander has a distance. A front wander with a duration above 0 ends after that many Clear hours; 0 does not. While they stand, idle2–idle9 can play. The time-of-day byte is stored and unused. Finishing drops the front row and, when repeat is set, puts a copy on the back. Actually walking travel, follow, escort, or activate is not run.

## What OpenMW does

Each NPC and creature has an ordered list. The **first** subrecord is the active package. When it finishes, it is removed. If the repeat flag is set, a fresh copy goes on the **back** so it can run again later. Wander is usually last and repeats, so it is what they do when nothing else is in front.

| Subrecord | Record | Runs until |
| --- | --- | --- |
| `AI_W` | int16 distance, int16 duration (hours), uint8 time of day, 8 idle chances (`idle2`–`idle9`), repeat | Duration hours elapse. Distance is the wander radius. Time of day is stored and **not used** (OpenMW: unimplemented, and not in the original engine). |
| `AI_T` | 3 floats xyz, repeat, 3 pad | The actor is at that point. Points farther than **7168** from the actor are ignored (one cell). |
| `AI_F` | 3 floats, int16 duration, 32-byte target id, repeat, optional `CNDT` cell name | Duration hours elapse, or there is no target. While active, walk when farther than the package distance from the target; stop when close. A cell name pauses the package outside that cell. |
| `AI_E` | Same shape as follow | The follower (usually the player) is at the dest. If they lag past the escort range, the leader waits (`idle3`). A dest of “nowhere” finishes as soon as the follower is in range. |
| `AI_A` | 32-byte object id, repeat | The object is missing or disabled. In range, use it and **keep** the package (original-engine compatibility). Walk is a straight line, not a path. |

`AIDT` (hello, fight, flee, alarm, services) is not a package. Combat, pursue, and cast are runtime packages OpenMW pushes later. Script commands (`AITravel`, `AIFollow`, …) are the script VM, not this list.

## Why it is large

The list is small to parse. Each package is a different “when am I done?” on top of one walker. That walker is still inside wander ([nav-paths.md](nav-paths.md) **5**). Travel and Follow need it aimed at an arbitrary point. Escort needs the player as the follower. Activate needs the same use as **E**.

## Prerequisites

| Must already be true | Why |
| --- | --- |
| Wander + walk cycle | Phases 38–39 and 41. Packages reuse the step, turn, and kf. |
| Pathgrid and Detour | Travel and Follow call the same polyline. Do not invent a third navigator. |
| **E** on doors, chests, and takeables | Activate fires that, from the actor instead of the camera. |

[nav-paths.md](nav-paths.md) **5** (shared walker) ships before Travel and Follow. Actor capsules ([physics.md](physics.md) **4**) are not required. The grid and the carpet already go around shacks.

## Work order

Check a box only when that slice has shipped as a phase. Move **one** row to [next-topics.md](next-topics.md) when the user wants it next; write the phase spec then.

### 1. Parse the list and print it

Spec: [phase52-ai-package-list.md](phase52-ai-package-list.md). **Working.**

- [x] On `NPC_` and `CREA`, read every `AI_W`, `AI_T`, `AI_F`, `AI_E`, `AI_A` in file order. `CNDT` after a follow or escort is that package’s cell name.
- [x] Keep today’s wander: the first `AI_W` distance still drives the walk. Do not run travel, follow, escort, or activate.
- [x] **E** on a nearby NPC or creature logs that list. Debug CLI `actor`, and `npc` / `crea`, print the same lines.

**Town test:** Fargoth and the Census clerks still wander. **E** on Fargoth logs his packages. Walk feel unchanged.

### 2. One active package

Spec: [phase53-active-package.md](phase53-active-package.md). **Working.**

- [x] Each placed actor keeps the list. The front package is active.
- [x] When it finishes, drop it. If repeat is set, put a reset copy on the back.
- [x] Front `AI_W` uses today’s wander. Any other front type **stays put** (same as distance 0) until its row below ships.

**Town test:** wander-first NPCs unchanged. An NPC whose first package is travel or follow stands instead of wandering off.

### 3. Wander duration and idles

Spec: [phase54-wander-duration.md](phase54-wander-duration.md). **Working.**

- [x] Duration hours (game hour, the Clear slider) end the package. 0 duration does not end.
- [x] While standing, roll `idle2`–`idle9` from the eight chances when that kf group exists. Walking still plays `walkforward`.
- [x] Store the time-of-day byte. Do not branch on it.

Needs (2). Does not need the shared walker.

### 4. Travel

- [ ] Front `AI_T` walks to that xyz with the shared walker (pathgrid, else Detour, else straight), then the package ends.
- [ ] Ignore a point farther than 7168 from the actor.
- [ ] Repeat puts it on the back, same as (2).

Needs (2) and [nav-paths.md](nav-paths.md) **5**.

**Town test:** an NPC with a travel point inside Seyda Neen walks there and stops. They do not set off for another city.

### 5. Follow

- [ ] Front `AI_F` finds the target id among loaded actors. No target: the package ends.
- [ ] Farther than the package distance: walk toward them with the shared walker. Close: stand.
- [ ] Duration hours end it. A `CNDT` cell name pauses it while the actor is not in that cell.

Needs (4)’s walker.

### 6. Escort

- [ ] Front `AI_E`: the target is the follower (the player, when the id is the player).
- [ ] Follower in range: walk to the dest like travel. Follower too far: wait, `idle3` if that group exists.
- [ ] No real dest: finish once the follower is in range.

Needs (5).

### 7. Activate

- [ ] Front `AI_A` walks a **straight line** to the named ref in the loaded cells (no path).
- [ ] In use range, run the same door / chest / take path as **E**. The package stays (it does not end on a successful use).
- [ ] Missing or disabled target: the package ends.

Needs (2). Does not need the shared walker.

## Stay out of this topic

- `AIDT` hello / fight / flee / alarm, greeting, dialogue.
- Combat, pursue, cast.
- MWScript / Lua package commands (`AITravel`, `StartCombat`, …).
- Saving the stack in `.ess`.
- Actor capsules, swimming, jumping ([physics.md](physics.md)).
- Detour-first wander, occupied nodes, hidden dests ([nav-paths.md](nav-paths.md) **4** and **6**).

## Debug

Until a slice specs a command, `npc` / `crea` gain a package line in (1). Chair HUD has no actors. Do not draw package dests unless the slice says so.

## OpenMW files (for NAME_MAP when a slice ships)

| OpenMW | Viewer when that slice exists |
| --- | --- |
| `ESM::AIPackageList` | parsed list on NPC and creature |
| `AiSequence::fill` / front package | (2) active package |
| `AiWander` duration + idle chances | (3); distance walk is already phase 41 |
| `AiTravel` + 7168 cap | (4) |
| `AiFollow` | (5) |
| `AiEscort` | (6) |
| `AiActivate` | (7) |
| `MWMechanics::PathFinder` | [nav-paths.md](nav-paths.md) **5** |

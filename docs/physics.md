# Physics (Bullet walk, drop the custom tracer)

This is a **big topic**, not a phase. Do **not** implement from this file. Split **one** sub-row into [next-topics.md](next-topics.md), then spec that slice after the user picks it. The leftover stays here and in [big-topics.md](big-topics.md). **(1) is several specs**, not one.

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwphysics/movementsolver.cpp` (`move`, `traceDown`, slide), `apps/openmw/mwphysics/stepper.cpp`, `apps/openmw/mwphysics/constants.hpp` / `components/misc/constants.hpp` (`sStepSizeUp` 34, `sStepSizeDown` 62, `sMaxSlope` 46, `sGroundOffset` 1, gravity), `apps/openmw/mwphysics/collisiontype.hpp` (player hits **World** + **HeightMap**, not actors), `apps/openmw/mwphysics/heightfield.cpp`, `apps/openmw/mwphysics/trace.cpp` (Bullet convex sweep / ray), and `components/nifbullet/bulletnifloader.cpp`. Do **not** port `mtphysics` (threaded world) or a second Recast.

## Do not touch nav

Recast, Detour, sqlite `navmesh.db`, F6 overlay, and the Phase 44 patch pass **stay as they are**. They are working. This work replaces **player movement**, not the walkable carpet.

While the player is on Bullet, Recast may keep pulling shack tris from `CollisionWorld.appendObjectTris`. Do not change Recast config, tile size, Detour queries, or gap detection. When the old tracer is deleted (after player Bullet works), that gather must still see the same `CollisionMesh` intern + live node matrices and land — plumbing only.

## What Town has now

WASD is a custom capsule vs land + object triangles (`CollisionWorld`, [phase 36](phase36-stay-on-land.md)). Same numbers as OpenMW’s stepper (eye 96, capsule 128×30, step 34/62, slope 46°, gravity 627). NPCs are ignored by the tracer; outdoor wanderers only snap TES Z to land bilinear. Doors and chest lids do not update collision after they move. Chair HUD still flies.

That tracer is ours. OpenMW does the same *movement* on **Bullet** collision tests, not a hand-rolled triangle loop.

Navmesh is separate: sqlite first, then a background rebake of cracked Recast tiles ([phase 44](phase44-navmesh-patch.md)). Straight wander dests use Detour ([phase 43](phase43-navmesh-path.md)). Pathgrid wander stays on F5 edges ([phase 41](phase41-pathgrid-wander.md)).

## What OpenMW does

Bullet is the **library**. OpenMW does not write a physics engine. `mwphysics` is a few thousand lines of glue:

- A Bullet collision world holds heightfields (TES 65×65 land) and triangle meshes from NIFs (`RootCollisionNode` if it has children, else rendered tris; `NC` extra = no collide; empty collision node / `NCC` still hits the camera).
- The player and actors are **not** rigid bodies. `MovementSolver` does kinematic traces: convex sweep the capsule, slide, step up, snap down. Same idea as today’s `CollisionWorld.move`, with Bullet answering “what did I hit?”
- Actors are capsules on **Actor** + **World** + **HeightMap**. The player does not collide with actors in the default camera walk (that is a later flag / occupancy).
- Projectiles and contact tests sit on the same world. `mtphysics` runs steps off the main thread. Swimming is a different move mode.

libGDX already ships this library as **`gdx-bullet`** (JNI natives next to the LWJGL3 desktop jar). We do not vendor Bullet C++ ourselves.

## Frozen custom tracer

`CollisionWorld` stays for NPCs (land stick) and Recast object tris **only until player Bullet works**. Do **not** fix, extend, or re-tune it. No new features on that path. Bugs in NPC stick or the old tracer are accepted until the delete-and-port slice.

Player WASD stays on the old tracer until the last spec in (1). Then spawn snap, Dump `onGround` / `floorY` / `ceilY`, and ceilings go through Bullet.

## Why it is large

Not because we implement Bullet. Because the player walk has to match Town docks and cave roofs on a new JNI world, then the old class has to go away without breaking Recast gather. Later slices (live doors, NPC capsules, actor-actor) hang off Bullet.

## Prerequisites

| Must already be true | Why |
| --- | --- |
| Stay-on-land behavior is the bar | Town docks / cave roofs / Census ceiling are the pass test. Bullet must match that, not invent a new feel. |
| `CollisionMesh` NIF rules | Same `RootCollisionNode` / `NC` / `NCC` choice as OpenMW. Keep intern-by-VFS. Recast gather reuses it. |
| Recast / Detour working | Do not “fix” nav by changing physics mid-slice. |

Overlapping tiles and distant land are **not** required.

## Work order

Check a box only when that slice has shipped as a phase. Move **one** 1.x (or later row) to [next-topics.md](next-topics.md) when the user wants it next; write the phase spec then. Do not spec all of (1) at once.

NPCs keep today’s `CollisionWorld` land stick through every 1.x. Recast gather may still call `appendObjectTris`. Do not change or bugfix that tracer. Chair HUD still flies. Water is never a floor.

### 1. Player walk on Bullet (several specs)

Do not switch WASD off the old tracer until **1.4**. Town docks must not go fly-through in 1.1–1.3. Two worlds may exist on purpose until (2).

#### 1.1 Wire `gdx-bullet`

Spec: [phase45-bullet-wire.md](phase45-bullet-wire.md). **Working.**

- [x] Add `gdx-bullet` + desktop natives on `lwjgl3` (libGDX **1.14.0**). No extra physics engine besides Bullet.
- [x] Create / dispose one Bullet collision world with the loaded cells (empty or stub is fine). Rebuild hooks on interior load and walk-grid swap, same moments as today.
- [x] Player still uses `CollisionWorld`.

**Town test:** walk unchanged. `glError=0`. Dump `bullet=1` `bodies=0`.

#### 1.2 Land in Bullet

- [ ] TES land as a heightfield (or equivalent triangles) in that world.
- [ ] Dump can show a Bullet land height next to today’s `floorY` (optional). Player still uses `CollisionWorld`.

**Town test:** walk unchanged. Land bodies exist for the loaded 5×5 (or interior: none). Nav unchanged.

#### 1.3 Object meshes in Bullet

- [ ] Static objects as meshes from existing `CollisionMesh` + `SceneNode` world matrices. Same `RootCollisionNode` / `NC` / `NCC` as today.
- [ ] Player still uses `CollisionWorld`.

**Town test:** walk unchanged. Bullet world has docks / kit / Census furniture. Doors still do not update after they swing.

#### 1.4 Player WASD on Bullet

- [ ] Player WASD / step / slide / gravity / spawn `traceDown` go through Bullet convex sweeps / rays. Keep the Phase 36 numbers (eye 96, capsule 128×30, step 34/62, slope 46°, ground offset 1, gravity 627). Player hits World + HeightMap only.
- [ ] Dump `onGround` / `floorY` / `ceilY` from Bullet for the camera.
- [ ] NPCs still on `CollisionWorld`. Recast gather still may use `appendObjectTris`.

**Town test:** Census `DODT`. Dirt, docks, dock undersides, hills, Census rafters, Addamasartus roof. Same pass/fail as Phase 36. `glError=0`. F5 / F6 / `src=db` / `patch=` / Detour wander unchanged. NPCs may still clip shacks and float on bilinear land.

When **1.4** is **working**, do not patch `CollisionWorld`; next is (2) delete-and-port.

### 2. Delete the custom tracer, port NPCs to Bullet

- [ ] NPC / creature floor stick and any remaining `CollisionWorld.move` callers use the Bullet world (same kinematic capsule idea as the player, actor-sized).
- [ ] Recast `gatherTesRecast` object tris come from `CollisionMesh` placements (or the Bullet mesh verts — same triangles). No Recast config or patch logic changes.
- [ ] Delete `CollisionWorld` (triangle loop, `fits`, interned-float traces). No leftover dual path.
- [ ] Still no actor-actor unless that is explicitly in the spec.

**Town test:** player feel unchanged from **1.4**. Outdoor wanderers still follow pathgrid / Detour; they sit on land / floors without the old class. Recast `src=db` and patch still work. Chair still flies.

Until this row ships, do not invest in the old tracer.

### 3. Live object poses

- [ ] Door swing and chest lid update the Bullet mesh transform (or rebuild that one body). **E** that opens a hide door actually blocks the camera.
- [ ] Taken world items leave the collision world when the mesh unparents.

Clipping a swung door is expected until this row. Update **Bullet** only. Do not teach `CollisionWorld` about swinging doors.

### 4. Actor vs world (shacks, not only land stick)

- [ ] After NPCs are on Bullet, fallback wanderers collide with World + HeightMap so they cannot walk through a shack the F6 carpet already goes around.
- [ ] Pathgrid wander can stay on authored edges; this slice is for dests that are not on the graph.
- [ ] Still no actor-actor.

### 5. Actor vs player (and each other)

- [ ] Walking into an NPC stops the camera (or slides). Occupied dest ([nav-paths.md](nav-paths.md) **4**) can use the same contacts later.
- [ ] Actor-actor skip-through is optional in the same slice or right after; say which in the spec.

### 6. Activation ray

- [ ] **E** nearest-door / chest / take can share Bullet ray tests instead of a second triangle picker. Only if the current pickers are the pain; otherwise leave them.

### 7. Projectiles / combat contacts

- [ ] Stays with [combat](big-topics.md). Same collision world, new shapes and callbacks. Do not spec from this file.

### 8. Swim / water move

- [ ] Stays [Swimming](big-topics.md). Underwater fog already exists. Bullet walk must not treat the water plane as a floor (already true in **1.4**).

`mtphysics` (OpenMW’s worker-thread step) is not a slice until Bullet is on the main thread and Dump shows physics as the hitch.

## Out of this hole

- Recast tile size, sqlite, Detour, F6 draw, Phase 44 detection
- Rigid-body ragdoll, vehicles, Havok parity
- 1st / 3rd person body
- Jump groups, sneak, fall damage
- Porting OpenMW tools

## NAME_MAP (when Java exists)

| OpenMW | Java | Status |
| --- | --- | --- |
| `btCollisionWorld` + dispatcher / dbvt | `BulletWorld` (empty) | rewrite |
| `btCollisionWorld` convex sweep / ray | gdx-bullet world | rewrite |
| `MovementSolver::move` | player move on that world | rewrite |
| `HeightField` | land in Bullet | rewrite |
| `BulletNifLoader` | keep `CollisionMesh`; feed Bullet | rewrite |
| `CollisionWorld` triangle tracer | **delete** after (2); NPCs only until then | — |

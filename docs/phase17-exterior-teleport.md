# Phase 17: Walk out empty-DNAM doors

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwclass/door.cpp` (`Door::activate` teleport), `apps/openmw/mwworld/cellref.cpp` (`getDestCell` / `getDoorDest`), `apps/openmw/mwworld/actionteleport.cpp` (`executeImp` / `teleport`), `apps/openmw/mwworld/worldimp.cpp` (`changeToCell`), `apps/openmw/mwworld/scene.cpp` (`changeToExteriorCell` / `changePlayerCell`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 16 grey land stays. HUD **Cell** / **Cave** / **Nix** / **Guild** / **Town** unchanged.

**E on a teleport door with empty `DNAM` unloads this cell and loads the exterior grid from that door’s `DODT` xy.** Arrival is that `DODT` (same `placeEye` as interior load doors). Named-`DNAM` interiors keep Phase 12. Doors still do not swing.

## Why this slice

Phase 16 can show Seyda Neen from HUD **Town**. Census / cave **exits** still log and sit still. OpenMW does not special-case those: empty `DNAM` is `getDestCell` → `esm3ExteriorCell(floor(x/8192), floor(y/8192))`, then `ActionTeleport` → `changeToCell` → `changeToExteriorCell`. Terrain for that one cell already exists. This slice is the missing activate path.

Still **one** cell (not OpenMW’s 3×3). Walking off the south edge of `(-2, -9)` stays expected.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Reuse Phase 11 pick (camera ray, 192, closest AABB among doors / chests / takeables).
- Teleport + **empty** `destCell`: `floor(DODT.x/8192)`, `floor(DODT.y/8192)` → `loadExterior` that grid (Phase 16). Place the eye at this door’s `DODT` + 96 eye height, yaw from `DODT` rot[2]. Do not swing.
- Same exterior grid as the cell you are in: do not rebuild; only move the camera (OpenMW `changePlayerCell` with `adjustPlayerPos`).
- Missing dest exterior: log, stay put.
- Teleport + **non-empty** `destCell`: unchanged Phase 12 interior load.
- Non-teleport: unchanged swing.
- HUD **Town** still loads `(-2, -9)` at the Census exit `DODT`.

Debug CLI: `cell` already prints empty `dest=` and `dodt=`. `exterior -2 -9` already dumps the dest cell.

```bat
gradlew.bat :core:debugCli --args="cell Seyda Neen, Census and Excise Office"
gradlew.bat :core:debugCli --args="exterior -2 -9"
```

## Out of scope

- `changeCellGrid` 3×3 (`CellGridRadius`)
- `LTEX` / water / sky / weather
- Followers, fade, `adjustPosition` floor snap
- Lock, trap, key, sounds
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Cell** (Census): look at a nord **exit** (`chargen door exit` / `ex_nord_door_01` with empty dest). **E** → Seyda Neen grey land at that door’s world `DODT`. Hide doors still swing. Desk take still works.

From **Town**, look at a door whose dest is `Seyda Neen, Census and Excise Office`. **E** → back inside the office (named `DNAM`, Phase 12).

**Guild** interior load doors still swap halls. **Cave** mouth empty-`DNAM` may load a different one-cell exterior; that is allowed, not the pass test.

```bat
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Census empty-`DNAM` exit **E** loads Seyda Neen land + buildings. Camera is at that door’s `DODT`, not an AABB center.
- Town door with dest Census **E** returns to the office.
- Hide doors still swing. Guild named dest still loads. Take still unparents. Chair HUD unfogged. `glError=0`.

Staying in Census after **E** on the closest empty-`DNAM` exit is a **fail**. Swinging that exit is a **fail**. Loading nine cells or dirt textures this phase is a **fail**. Walking off the south edge of `(-2, -9)` is **expected**, not a fail.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Activate.** Unlocked, untrapped `Door::activate` with `getTeleport()` returns `ActionTeleport(getDestCell(), getDoorDest(), true)` — not `ActionDoor`. `getDoorDest()` is the ref `DODT`. Empty `mDestCell` still uses that same `ActionTeleport` (dest id is the exterior from `DODT` xy).
2. **Player execute.** `ActionTeleport::executeImp` teleports followers when the third ctor arg is true, then `teleport(actor)`. For the player, `teleport` calls `World::changeToCell(mCellId, mPosition, true)` with the stored dest cell id and `DODT` position.
3. **Exterior change.** `changeToCell`: if `destinationCell->isExterior()` then `changeToExteriorCell(cellId, position, adjustPlayerPos, changeEvent)`. `changeToExteriorCell` looks up that cell, reads `getGridX`/`getGridY`, `changeCellGrid`, then `changePlayerCell(current, position, adjustPlayerPos)`. With `adjustPlayerPos` true, `changePlayerCell` `moveObject` + `rotateObject` to that position.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Door::activate` empty `DNAM` | **E** → `loadExterior` | rewrite |
| `CellRef::getDestCell` empty | `LandRecord.cellGrid(DODT)` | same |
| `World::changeToCell` exterior | dispose + `loadExterior` + `placeEye` | rewrite |
| `Scene::changeToExteriorCell` | one grid cell (not 3×3) | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: For an unlocked TES3 door with no trap, Door::activate
returns ActionTeleport(getDestCell(), getDoorDest(), true) when
ptr.getCellRef().getTeleport() is true, else ActionDoor.
getDoorDest is the cell-ref DODT (mDoorDest). getDestCell uses
the same function for interiors and exteriors: non-empty
mDestCell is that string; empty mDestCell computes an exterior
cell id from DODT pos[0]/pos[1].

From apps/openmw/mwclass/door.cpp Door::activate (unlocked, no trap):
    if (ptr.getCellRef().getTeleport())
        return ActionTeleport(getDestCell(), getDoorDest(), true);
    else
        return ActionDoor(ptr);

From apps/openmw/mwworld/cellref.cpp:
    getDoorDest (ESM3): return ref.mDoorDest;
    getDestCell (ESM3):
        if (!ref.mDestCell.empty())
            return ESM::RefId::stringRefId(ref.mDestCell);
        else {
            cellPos = positionToExteriorCellLocation(
                ref.mDoorDest.pos[0], ref.mDoorDest.pos[1]);
            return ESM::RefId::esm3ExteriorCell(cellPos.mX, cellPos.mY);
        }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: ActionTeleport stores cellId, position, teleportFollowers.
executeImp: if teleportFollowers, look up getCell(mCellId).isExterior()
for the follower filter, teleport each follower, then always
teleport(actor). For the player, teleport() sets
Player::setTeleported(true) and World::changeToCell(mCellId,
mPosition, true).

From apps/openmw/mwworld/actionteleport.cpp:
    ActionTeleport(cellId, position, teleportFollowers)
    executeImp:
        if (mTeleportFollowers) {
            toExterior = getCell(mCellId).isExterior();
            getFollowers(..., toExterior, ...);
            teleport each follower
        }
        teleport(actor);
    teleport (player):
        world->getPlayer().setTeleported(true);
        world->changeToCell(mCellId, mPosition, true);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: World::changeToCell looks up the dest cell. If
destinationCell->isExterior() it changeToExteriorCell(cellId,
position, adjustPlayerPos, changeEvent), else
changeToInteriorCell. changeToExteriorCell looks up that cell,
reads getGridX/getGridY, calls changeCellGrid, then
changePlayerCell(current, position, adjustPlayerPos).
changePlayerCell with adjustPlayerPos true moveObject(player,
pos.asVec3()) and rotateObject(player, pos.asRotationVec3())
then adjustPosition(player, true).

From apps/openmw/mwworld/worldimp.cpp changeToCell:
    exteriorCell = destinationCell->isExterior();
    if (exteriorCell)
        changeToExteriorCell(cellId, position, adjustPlayerPos, changeEvent);
    else
        changeToInteriorCell(destinationCell->getNameId(),
            position, adjustPlayerPos, changeEvent);

From apps/openmw/mwworld/scene.cpp changeToExteriorCell:
    current = getCell(extCellId);
    cellIndex = (current.getGridX(), current.getGridY());
    changeCellGrid(position, ExteriorCellLocation(cellIndex), ...);
    changePlayerCell(current, position, adjustPlayerPos);

From changePlayerCell:
    if (adjustPlayerPos) {
        moveObject(player, pos.asVec3());
        rotateObject(player, pos.asRotationVec3());
        player.getClass().adjustPosition(player, true);
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

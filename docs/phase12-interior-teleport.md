# Phase 12: Interior door teleport

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwclass/door.cpp` (`activate` teleport branch), `apps/openmw/mwworld/actionteleport.cpp` (`executeImp` / `teleport`), `apps/openmw/mwworld/cellref.cpp` (`getDestCell` / `getDoorDest`), `apps/openmw/mwworld/worldimp.cpp` (`changeToCell` / `changeToInteriorCell`), `apps/openmw/mwworld/scene.cpp` (`changeToInteriorCell` / `changePlayerCell`), `components/esm3/loadcell.hpp` (`isExterior`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same Path B viewer. Phase 11 swing stays. **Cell** / **Cave** / **Nix** stay Census / Addamasartus / Punsabanit.

**E on a load door whose `DNAM` is another interior unloads this cell and loads that one.** Arrival is that door’s `DODT` (not the dest cell’s world-inbound spawn). Empty-`DNAM` doors are exteriors and stay a no-op (no terrain).

## Why this slice

Phase 11 already picks doors. Teleport refs currently log and sit still. The next hole in “the cell is usable” is walking a load door into the next interior. Census / cave **exits** have empty `DNAM` and world `DODT` — those wait on terrain. Wolverine Hall’s Mage Guild has a named `DNAM` to the hall, and that door is ~140 units from inbound spawn (inside the 192 activate range).

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only.

HUD: **Cell** / **Cave** / **Nix** unchanged. Add **Guild** = `Sadrith Mora, Wolverine Hall: Mage's Guild` (~91 refs). Load door `in_impsmall_loaddoor_01` dest is `Sadrith Mora, Wolverine Hall`.

- Reuse Phase 11 pick (camera ray, 192, door AABBs). Closest hit wins.
- Non-teleport: same swing as Phase 11.
- Teleport + **empty** `destCell`: no-op (log). That is OpenMW `getDestCell`’s exterior branch (`positionToExteriorCellLocation` from `DODT` xy). Do not call `setDoorState`.
- Teleport + **non-empty** `destCell`: `ActionTeleport(dest cell, DODT, true)` for the player — skip Lua / followers / fade / `adjustPosition` snap. Unload the current interior, `loadInterior` the dest name, place the eye at `DODT` pos + 96 eye height, yaw from `DODT` rot[2] (same `placeEye` as inbound spawn, but the **activating door’s** `DODT`, not `LoadedCell.spawnPos`).
- Same dest name as the cell you are in: do not rebuild; only move the camera (OpenMW `changeToInteriorCell` when `mCurrentCell == &cell`).
- Missing dest interior: log, stay put.
- Swing doors and idle NPCs/creatures in Census / Cave / Nix must not regress.

Debug CLI: `cell` already prints `dest=` / `swing`.

```bat
gradlew.bat :core:debugCli --args="cell Sadrith Mora, Wolverine Hall: Mage's Guild"
gradlew.bat :core:debugCli --args="cell Sadrith Mora, Wolverine Hall"
gradlew.bat :core:debugCli --args="spawn Sadrith Mora, Wolverine Hall: Mage's Guild"
```

## Out of scope

- Exterior / terrain (`changeToExteriorCell`)
- Followers, fade in/out, loading-screen text
- `adjustPosition` / Bullet snap to floor
- Lock, trap, key, sounds
- Container / NPC activate
- Plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Guild** (Wolverine Hall: Mage's Guild): inbound spawn faces the connecting door. Look at `in_impsmall_loaddoor_01` and **E** → Wolverine Hall at that door’s `DODT`. **E** on that hall door returns to the guild.

**Cell** / **Cave** / **Nix**: nord / cave-mouth exits still do nothing. Hide doors still swing.

```bat
gradlew.bat :core:debugCli --args="cell Sadrith Mora, Wolverine Hall: Mage's Guild"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Guild door **E** loads Wolverine Hall (HUD name changes). Camera is at that door’s `DODT`, not the guild inbound spawn.
- Hall door **E** returns to the Mage's Guild.
- Census / Addamasartus / Punsabanit teleport exits still do not change cell. Hide doors still swing. Nix-hounds still idle.
- Chair HUD unfogged. `glError=0`.

Loading Seyda Neen is a **fail**. Staying in the guild after **E** on the hall door is a **fail**. Arriving at the dest cell’s world-inbound spawn instead of the door `DODT` is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Activate teleport.** Unlocked, untrapped `Door::activate` with `getTeleport()` returns `ActionTeleport(getDestCell(), getDoorDest(), true)` — not `ActionDoor`. `getDoorDest()` is the ref `DODT`. `getDestCell()`: if `mDestCell` is non-empty, that string is the interior id; if empty, the dest is an exterior cell from `DODT` xy.
2. **Player execute.** `ActionTeleport::executeImp` teleports followers when the third ctor arg is true, then `teleport(actor)`. For the player, `teleport` calls `World::changeToCell(mCellId, mPosition, true)`.
3. **Interior change.** `changeToCell` loads the dest `Cell`; if `isExterior()` then `changeToExteriorCell`, else `changeToInteriorCell(destinationCell->getNameId(), position, adjustPlayerPos, changeEvent)`. `ESM::Cell::isExterior()` is `!(flags & Interior)`. `Scene::changeToInteriorCell`: same store cell → `moveObject` + `rotateObject` to `position` (no unload). Different cell → unload every active cell, `loadCell` dest, `changePlayerCell` with `adjustPlayerPos` moving the player to `pos`.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Door::activate` teleport | **E** + dest `DNAM` | rewrite |
| `ActionTeleport` | load dest interior | rewrite |
| `CellRef::getDestCell` | empty `destCell` → exterior no-op | rewrite |
| `World::changeToCell` | `loadInterior` + `placeEye` at `DODT` | rewrite |
| `Scene::changeToInteriorCell` | dispose + rebuild cell | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: For an unlocked TES3 door with no trap, Door::activate
returns ActionTeleport(getDestCell(), getDoorDest(), true) when
ptr.getCellRef().getTeleport() is true, else ActionDoor. getDoorDest
is the cell-ref DODT position. getDestCell: if mDestCell is not
empty, return that string as the dest id; else compute an exterior
cell id from DODT pos[0]/pos[1] via positionToExteriorCellLocation.

From apps/openmw/mwclass/door.cpp Door::activate (unlocked, no trap):
    if (ptr.getCellRef().getTeleport())
        return ActionTeleport(getDestCell(), getDoorDest(), true);
    else
        return ActionDoor(ptr);

From apps/openmw/mwworld/cellref.cpp getDestCell (ESM3):
    if (!ref.mDestCell.empty())
        return ESM::RefId::stringRefId(ref.mDestCell);
    else {
        cellPos = positionToExteriorCellLocation(ref.mDoorDest.pos[0],
            ref.mDoorDest.pos[1]);
        return ESM::RefId::esm3ExteriorCell(cellPos.mX, cellPos.mY);
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: ActionTeleport stores cellId, position, teleportFollowers.
executeImp: if teleportFollowers, gather followers then teleport
each, then always teleport(actor). For the player, teleport() sets
Player::setTeleported(true) and World::changeToCell(mCellId,
mPosition, true).

From apps/openmw/mwworld/actionteleport.cpp:
    ActionTeleport(cellId, position, teleportFollowers)
    executeImp:
        if (mTeleportFollowers) { getFollowers(...); teleport each }
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
destinationCell->isExterior() it changeToExteriorCell, else
changeToInteriorCell(destinationCell->getNameId(), position,
adjustPlayerPos, changeEvent). ESM::Cell::isExterior is
!(mData.mFlags & Interior). Scene::changeToInteriorCell: if
mCurrentCell == &cell, moveObject + rotateObject to position and
(if adjustPlayerPos) adjustPosition, then return without unload.
Otherwise unload every active cell, loadCell the dest, then
changePlayerCell. changePlayerCell with adjustPlayerPos true
moveObject(player, pos.asVec3()) and rotateObject(player,
pos.asRotationVec3()) then adjustPosition(player, true).

From components/esm3/loadcell.hpp:
    bool isExterior() const { return !(mData.mFlags & Interior); }

From apps/openmw/mwworld/worldimp.cpp changeToCell:
    exteriorCell = destinationCell->isExterior();
    if (exteriorCell) changeToExteriorCell(...)
    else changeToInteriorCell(destinationCell->getNameId(),
        position, adjustPlayerPos, changeEvent);

From apps/openmw/mwworld/scene.cpp changeToInteriorCell:
    if (mCurrentCell == &cell) {
        moveObject(player, position.asVec3());
        rotateObject(player, position.asRotationVec3());
        if (adjustPlayerPos) adjustPosition(player, true);
        return;
    }
    unload all mActiveCells; loadCell(cell, ...);
    changePlayerCell(cell, position, adjustPlayerPos);

From changePlayerCell:
    if (adjustPlayerPos) {
        moveObject(player, pos.asVec3());
        rotateObject(player, pos.asRotationVec3());
        player.getClass().adjustPosition(player, true);
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

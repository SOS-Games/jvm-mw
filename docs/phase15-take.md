# Phase 15: Take world items

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwworld/class.cpp` (`defaultItemActivate`), `apps/openmw/mwclass/weapon.cpp` / `misc.cpp` / `light.cpp` / `book.cpp` (`activate`), `apps/openmw/mwworld/actiontake.cpp` (`executeImp`), `apps/openmw/mwworld/worldimp.cpp` (`deleteObject`), `apps/openmw/mwworld/scene.cpp` (`removeObjectFromScene`), `apps/openmw/mwrender/objects.cpp` (`removeObject`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same Path B viewer. Phase 14 VFS and container lids stay. HUD **Cell** / **Cave** / **Nix** / **Guild** unchanged.

**E on a world item (quill, ink, weapon, carryable candle) removes it from the cell.** No inventory window. The mesh unparents like `Objects::removeObject`. Doors, chests, and books keep their current activate path.

## Why this slice

Doors swing, interiors load, chests open. The next hole in “the cell is usable” is the clutter on the desks. OpenMW does not open those: `defaultItemActivate` returns `ActionTake`, which `add`s to the actor store then `World::deleteObject` → `removeObjectFromScene` → unparent the base node. This port skips the store and the take sound; the item disappears.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Pick: camera ray, 192, **closest AABB among doors, containers, and takeable items**. Same winner rule as Phase 13 (nearest distance).
- Takeable: `WEAP`, `ARMO`, `CLOT`, `MISC`, `INGR`, `ALCH`, `APPA`, `LOCK`, `PROB`, `REPA`, and `LIGH` with the **Carry** flag (`0x002`). Those classes call `defaultItemActivate` (lights only if Carry).
- **Not** takeable this slice: `STAT`, `ACTI`, `CONT`, `DOOR`, `NPC_`, `CREA`, `BOOK` (`ActionRead`). Fixture lights without Carry stay.
- Take: skip werewolf / `GW_Inventory` checks (always allow). Skip `itemTaken` crime, gold count×value, `getUpSoundId`. Unparent the instance from the cell root (OpenMW `removeChild` the base node). Drop it from the takeable pick list. If it was a Carry `LIGH`, drop its `CellLight` too.
- Books: do not take, do not open a read GUI (log `book` if that is the closest hit).
- Debug CLI `cell`: print takeable refs (`take id tes= modl= rec=`).

```bat
gradlew.bat :core:debugCli --args="cell Seyda Neen, Census and Excise Office"
```

## Out of scope

- Inventory / `GM_Container` / `pickUpObject` drag-drop
- `ActionRead` book GUI
- Take sounds, crime, ownership, gold stacking
- Activators, NPC talk
- Terrain, plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Cell** (Census): look at clutter on a desk (not the wood chest, not a door). **E** — that mesh vanishes. Chest **E** still opens/closes. Hide doors still swing. Nearby fixture candles without Carry stay.

```bat
gradlew.bat :core:debugCli --args="cell Seyda Neen, Census and Excise Office"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- A Census desk item **E** removes that mesh (not a door swing, not a lid).
- `stolen_goods` still opens/closes. Hide doors still swing. Guild load doors still teleport. Nix-hounds still idle.
- Books do not vanish. Wall/fixture lights without Carry do not vanish.
- Chair HUD unfogged. `glError=0`.

An inventory window is out of scope, not a fail. Item that stays after **E** when it is the closest takeable hit is a **fail**. Taking a hide door or the chest is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **defaultItemActivate.** If inventory GUI is not allowed, return `NullAction`. Werewolf NPC → `FailedAction`. Else `ActionTake(ptr)` and `setSound(getUpSoundId(ptr))`.
2. **Who takes.** `Weapon::activate` and `Miscellaneous::activate` return `defaultItemActivate`. `Light::activate`: inventory not allowed → `NullAction`; if Carry is unset → `FailedAction`; else `defaultItemActivate`. `Book::activate` returns `ActionRead`, not `ActionTake`.
3. **Delete from world.** `ActionTake::executeImp`: if the player is in `GM_Inventory` or `GM_Container`, `pickUpObject` and return. Else `itemTaken`, `containerStore.add`, then `World::deleteObject`. `deleteObject` (not already deleted, not in a container store): `setCount(0)`; if in an active cell and enabled, `removeObjectFromScene`. `Objects::removeObject` unparents the base node and clears it.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Class::defaultItemActivate` | **E** + item pick | rewrite |
| `ActionTake` | unparent mesh, skip inventory | rewrite |
| `World::deleteObject` | remove from cell root | rewrite |
| `Objects::removeObject` | drop instance node | rewrite |
| `ESM::Light::Carry` | `EsmObject.LIGH_CARRY` `0x002` | same |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Class::defaultItemActivate returns NullAction if inventory GUI
is not allowed. If the actor is an NPC werewolf it returns
FailedAction (optional WolfItem sound). Otherwise it constructs
ActionTake(ptr) and setSound(getUpSoundId(ptr)).

From apps/openmw/mwworld/class.cpp defaultItemActivate:
    if (!WindowManager->isAllowed(GW_Inventory))
        return NullAction();
    if (actor.getClass().isNpc() && actor.getClass().getNpcStats(actor).isWerewolf()) {
        action = FailedAction("#{sWerewolfRefusal}");
        if (sound) action->setSound(sound->mId);
        return action;
    }
    action = ActionTake(ptr);
    action->setSound(getUpSoundId(ptr));
    return action;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Weapon::activate and Miscellaneous::activate return
defaultItemActivate(ptr, actor). Light::activate returns NullAction
if inventory GUI is not allowed; if the light's Carry flag is unset
it returns FailedAction; otherwise defaultItemActivate. Book::activate
returns ActionRead(ptr), not ActionTake.

From apps/openmw/mwclass/weapon.cpp:
    Weapon::activate: return defaultItemActivate(ptr, actor);

From apps/openmw/mwclass/misc.cpp:
    Miscellaneous::activate: return defaultItemActivate(ptr, actor);

From apps/openmw/mwclass/light.cpp:
    Light::activate:
        if (!isAllowed(GW_Inventory)) return NullAction();
        if (!(ref->mBase->mData.mFlags & ESM::Light::Carry))
            return FailedAction();
        return defaultItemActivate(ptr, actor);
    Light::isItem: return mFlags & ESM::Light::Carry;

From apps/openmw/mwclass/book.cpp:
    Book::activate: return ActionRead(ptr);

From components/esm3/loadligh.hpp:
    Carry = 0x002;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: ActionTake::executeImp, when the actor is the player and the
GUI mode is GM_Inventory or GM_Container, calls pickUpObject and
returns. Otherwise it calls itemTaken, containerStore.add, then
World::deleteObject(getTarget()). deleteObject, if the ref is not
already deleted and getContainerStore is null: setCount(0); if the
ptr is in an active cell and enabled, removeObjectFromScene.
Objects::removeObject unparents the base node from its parent and
setBaseNode(nullptr).

From apps/openmw/mwworld/actiontake.cpp executeImp:
    if (actor == player) {
        mode = getMode();
        if (mode == GM_Inventory || mode == GM_Container) {
            getInventoryWindow()->pickUpObject(getTarget());
            return;
        }
    }
    itemTaken(actor, getTarget(), Ptr(), count);
    newitem = actor.getClass().getContainerStore(actor).add(getTarget(), count);
    World->deleteObject(getTarget());

From apps/openmw/mwworld/worldimp.cpp deleteObject:
    if (!ptr.mRef->isDeleted() && ptr.getContainerStore() == nullptr) {
        ptr.getCellRef().setCount(0);
        if (ptr.isInCell() && activeCells contains ptr.getCell()
            && ptr.getRefData().isEnabled())
            mWorldScene->removeObjectFromScene(ptr);
    }

From apps/openmw/mwrender/objects.cpp removeObject:
    ... removeFromScene / erase ...
    ptr.getRefData().getBaseNode()->getParent(0)->removeChild(
        ptr.getRefData().getBaseNode());
    ptr.getRefData().setBaseNode(nullptr);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

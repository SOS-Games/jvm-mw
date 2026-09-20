# Phase 11: Doors that open

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwclass/door.cpp` (`useAnim`, `insertObjectRendering`, `activate`, `setDoorState`), `apps/openmw/mwworld/actiondoor.cpp`, `apps/openmw/mwworld/worldimp.cpp` (`activateDoor`, `rotateDoor`, `processDoors`, `getMaxActivationDistance`, `getFocusObject`), `apps/openmw/mwrender/objects.cpp` (`insertModel`), `apps/openmw/mwrender/animation.cpp` (`ObjectAnimation`), `apps/openmw/mwworld/scene.cpp` (`makeDirectNodeRotation`), `apps/openmw/mwworld/doorstate.hpp`, `components/esm3/loaddoor.hpp`, `components/misc/convert.hpp` (`makeOsgQuat`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 10 creatures stay. **Cell** / **Cave** / **Nix** stay Census / Addamasartus / Punsabanit.

**Non-teleport doors already in those interiors swing open and shut.** Hide doors in Addamasartus (`door_cavern_doors00`) and Punsabanit (`door_cavern_doors10`) are the visual test. Census interior doors (`in_c_door_arched`, `chargen door hall`) also swing. Census / cave **exit** doors (`ex_nord_door_01`, `ex_cave_door_01`) have `DODT` and must **not** swing and must **not** load an exterior.

## Why this slice

Phase 4 placed `DOOR` as a static mesh. The next hole in “the cell is usable” is doors you can open without leaving the interior. OpenMW does not play an open `.kf` for that: it rotates the placed instance 90° around TES3 **Z** at 90°/s.

Teleport doors (`CellRef` `DODT`) take `ActionTeleport` and never enter `mDoorStates`. Walking through them into Seyda Neen is terrain (later). This phase is the in-cell swing only.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only.

HUD cells unchanged. Hint line: **E** activates the door under the camera crosshair (Space is already fly-up).

- Keep placing `DOOR` with `makeOsgQuat` (full XYZ, not actor yaw-only). The instance local is rewritten every swing frame from `pos` + live `rot` + scale.
- Closed Z is the **cell-ref** `rot[2]` (`minRot`). Open Z is `minRot + 90°` (`maxRot`). Live Z is `RefData` position `rot[2]` (start equal to the cell-ref).
- Activate: camera-center ray, max distance GMST `iMaxActivateDist` (**192**). OpenMW uses an OSG mesh ray; rewrite may hit **DOOR instance AABBs only** (not STAT walls, not NPCs). Closest hit wins. If nothing, no-op.
- Hit a teleport door (`CellRef.teleport` / `DODT`): no-op (log). Do not call `setDoorState` (OpenMW throws `"load doors can't be moved"`).
- Hit a non-teleport door: same toggle as `World::activateDoor(ptr)`:
  - `Idle` and live Z equals cell-ref Z → `Opening`
  - `Idle` and live Z is not cell-ref Z → `Closing`
  - `Closing` → `Opening`
  - `Opening` → `Closing`
- Each frame (`processDoors`): `rotateDoor` — Z only, `diff = dt * 90°/s` (sign +1 Opening, −1 Closing), clamp to `[minRot, maxRot]`, then `EsmTransforms.setLocal`. When the clamped Z hits `maxRot` (opening) or `minRot` (closing), set `Idle` and stop tracking that door.
- Skip lock / trap / key (`UNAM` / `TNAM` / `KNAM`). Every non-teleport door may swing.
- Skip SNAM/ANAM sounds.
- Skip Bullet: no actor collision, no undo-rotation, no `AiAvoidDoor`. The door may pass through the camera.
- Skip door `.kf` play. `Door::useAnim()` is true so OpenMW may `insertModel` / `ObjectAnimation` and `addAnimSource` if an `x*.kf` exists, but **open/close is still `rotateDoor` on the base node**, not a kf group. Keep the current STAT-like NIF pose.

Debug CLI: existing `cell` already prints `DOOR` refs and `dest=` when teleport. No new command required.

```bat
gradlew.bat :core:debugCli --args="cell Addamasartus"
gradlew.bat :core:debugCli --args="cell Punsabanit"
gradlew.bat :core:debugCli --args="nif meshes/d/door_cavern_doors00.nif"
```

## Out of scope

- Teleport / load another cell (interior or exterior)
- Terrain, plugins, Lua (`onActivate` wrapper)
- Lock, trap, key, Telekinesis glow
- Door open/close sounds
- Physics, actor collision, navmesh
- Playing door `.kf` groups / embedded nif keyframe as the swing
- Container / activator / NPC activate
- 1st person, GPU skinning / `ModelBatch`

## Test cell

**Cave** (Addamasartus): walk in, look at a hide door (`door_cavern_doors00`), press **E**. It should take about one second to open 90°. **E** again closes it. The cave mouth `ex_cave_door_01` must stay shut.

**Cell** (Census): arched / hall wood doors swing. The nord `ex_nord_door_01` exits do not.

**Nix** (Punsabanit): hide doors `door_cavern_doors10` swing. Nix-hounds still idle.

```bat
gradlew.bat :core:debugCli --args="cell Addamasartus"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Addamasartus hide doors swing ~90° in ~1 s, then stop. Second activate closes them.
- Activate while still moving reverses direction (Opening ↔ Closing).
- Census interior wood doors swing. Census / cave **teleport** doors do not move and do not change cell.
- Punsabanit hide doors swing. Nix-hounds still idle. Census NPCs still idle.
- Chair HUD unfogged. `glError=0`. Moldcave seams still closed.

Teleport doors swinging is a **fail**. Hide doors that stay welded shut after **E** is a **fail**. Loading Seyda Neen is out of scope, not a fail. Doors passing through the camera is expected (no Bullet).

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Insert vs swing.** `Door::useAnim()` is true. `insertObjectRendering` calls `Objects::insertModel`. `insertModel` may `correctActorModelPath` and construct `ObjectAnimation` (`setObjectRoot(model, false, false, false)`; `addAnimSource` only if still `animated`). Placement rotation for non-actors is `makeOsgQuat` (Z then Y then X, negated axes). The open/close **motion** is not a kf group: `rotateDoor` writes ESM `rot[2]`, then `updateObjectRotation` / `makeDirectNodeRotation` sets the base node attitude.
2. **Activate: teleport vs swing.** Unlocked `Door::activate`: `getTeleport()` → `ActionTeleport` (no rotate). Else `ActionDoor` → `World::activateDoor(target)`. `setDoorState` on a teleport door throws. (0.51 player Activate goes through Lua `onActivate` then `_runStandardActivationAction`; this port skips Lua and calls that C++ path.)
3. **rotateDoor / processDoors.** `minRot = cellRef.position.rot[2]`, `maxRot = minRot + 90°`. `diff = duration * 90°/s * (Opening ? +1 : −1)`. Clamp live Z into `[minRot, maxRot]`. `RotationFlag_none` (absolute, not adjust). `processDoors` each physics tick; when the clamp hits the end, state becomes `Idle`. `activateDoor` Idle+closed → Opening, Idle+not-closed → Closing, Closing → Opening, Opening → Closing.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `MWClass::Door::activate` | viewer **E** + door pick | rewrite |
| `MWWorld::ActionDoor` | swing toggle | rewrite |
| `World::activateDoor` / `rotateDoor` / `processDoors` | live `rot[2]` + `EsmTransforms.setLocal` | rewrite |
| `MWWorld::DoorState` | Idle / Opening / Closing | same |
| `World::getMaxActivationDistance` | 192 (`iMaxActivateDist`) | rewrite |
| `World::getFocusObject` | camera ray vs door AABB | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Door::useAnim() returns true. insertObjectRendering calls
Objects::insertModel(ptr, model). insertModel starts animated =
ptr.getClass().useAnim(); if animated and the mesh is non-empty it
correctActorModelPath's the mesh, then if the corrected path equals
the original and it still ends .nif, animated becomes false.
ObjectAnimation then setObjectRoot(model, false, false, false) and
addAnimSource(model, model) only if animated. Non-actor placement
uses makeOsgQuat (Z * Y * X on negated axes). That is the closed
pose. Open/close is not a kf group: rotateDoor assigns ESM rot[2],
then updateObjectRotation uses makeDirectNodeRotation =
makeOsgQuat for non-actors and RenderingManager::rotateObject sets
the base node attitude.

From apps/openmw/mwclass/door.cpp:
    bool Door::useAnim() const { return true; }
    insertObjectRendering:
        renderingInterface.getObjects().insertModel(ptr, model);

From apps/openmw/mwrender/objects.cpp insertModel:
    bool animated = ptr.getClass().useAnim();
    if (animated && !mesh.empty()) {
        animationMesh = correctActorModelPath(mesh);
        if (animationMesh == mesh && ciEndsWith(animationMesh, ".nif"))
            animated = false;
    }
    new ObjectAnimation(ptr, animationMesh, ..., animated, allowLight);

From apps/openmw/mwrender/animation.cpp ObjectAnimation:
    setObjectRoot(model, false, false, false);
    if (animated) addAnimSource(model, model);

From apps/openmw/mwworld/scene.cpp:
    makeDirectNodeRotation: isActor ? makeActorOsgQuat : makeOsgQuat
    addObject: insertObjectRendering then setNodeRotation(..., rotation)
    updateObjectRotation → setNodeRotation → rotateObject(ptr, quat)

From components/misc/convert.hpp makeOsgQuat:
    Quat(rot[2], (0,0,-1)) * Quat(rot[1], (0,-1,0)) * Quat(rot[0], (-1,0,0))

From apps/openmw/mwrender/renderingmanager.cpp:
    rotateObject: ptr.getRefData().getBaseNode()->setAttitude(rot);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: For an unlocked TES3 door with no trap, Door::activate
returns ActionTeleport(dest cell, door dest, true) when
ptr.getCellRef().getTeleport() is true, else ActionDoor(ptr).
ActionDoor::executeImp calls World::activateDoor(getTarget()) with
no extra state argument (the toggle overload). Door::setDoorState
throws if the door is a teleport door ("load doors can't be moved").
Player::activate in 0.51 queues Lua onActivate; the builtin
activation handler then world._runStandardActivationAction, which
calls getClass().activate()->execute. A port that skips Lua should
still run that C++ activate/execute path.

From apps/openmw/mwclass/door.cpp Door::activate (unlocked, no trap):
    if (ptr.getCellRef().getTeleport())
        return ActionTeleport(getDestCell(), getDoorDest(), true);
    else
        return ActionDoor(ptr);
    setDoorState:
        if (ptr.getCellRef().getTeleport())
            throw runtime_error("load doors can't be moved");

From apps/openmw/mwworld/actiondoor.cpp:
    void ActionDoor::executeImp(...)
        World()->activateDoor(getTarget());

From apps/openmw/mwworld/player.cpp Player::activate:
    toActivate = World()->getFocusObject();
    LuaManager()->objectActivated(toActivate, player);

From files/data/scripts/omw/activationhandlers.lua onActivate:
    world._runStandardActivationAction(obj, actor)

From apps/openmw/mwlua/worldbindings.cpp:
    _runStandardActivationAction:
        objPtr.getClass().activate(objPtr, actorPtr)->execute(actorPtr);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: World::doPhysics calls processDoors(duration) before the
Bullet step. rotateDoor uses minRot = door.getCellRef().getPosition().rot[2]
and maxRot = minRot + 90 degrees. diff = duration * 90deg/s *
(state == Opening ? 1 : -1). New Z is clamp(oldZ + diff, minRot, maxRot).
rotateObject(..., RotationFlag_none) writes that XYZ absolutely
(not +=). reached is (targetRot == maxRot && state != Idle) ||
targetRot == minRot; then processDoors setDoorState Idle and erases
the door. activateDoor(ptr) toggle: Idle and live rot[2] == cell-ref
rot[2] → Opening; Idle otherwise → Closing; Closing → Opening;
Opening → Closing.

From apps/openmw/mwworld/worldimp.cpp:
    doPhysics: processDoors(duration);
    rotateDoor:
        minRot = door.getCellRef().getPosition().rot[2];
        maxRot = minRot + DegreesToRadians(90);
        diff = duration * DegreesToRadians(90) * (Opening ? 1 : -1);
        targetRot = clamp(oldRot.z() + diff, minRot, maxRot);
        newRot.z() = targetRot;
        rotateObject(door, newRot, RotationFlag_none);
        reached = (targetRot == maxRot && state != Idle) || targetRot == minRot;
    processDoors: if reached, setDoorState Idle, erase
    activateDoor(door):
        Idle: rot[2] == cellRef.rot[2] ? Opening : Closing
        Closing → Opening
        Opening (default) → Closing
        setDoorState; mDoorStates[door] = state

From apps/openmw/mwworld/doorstate.hpp:
    Idle = 0, Opening = 1, Closing = 2

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

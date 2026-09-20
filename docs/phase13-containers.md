# Phase 13: Container lids

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwclass/container.cpp` (`useAnim`, `insertObjectRendering`, `activate`, `canBeHarvested`), `apps/openmw/mwworld/actionopen.cpp`, `apps/openmw/mwmechanics/mechanicsmanagerimp.cpp` (`onOpen`), `apps/openmw/mwmechanics/objects.cpp` (`onOpen` / `addObject`), `apps/openmw/mwmechanics/character.cpp` (`CharacterController::onOpen`), `apps/openmw/mwrender/objects.cpp` (`insertModel`), `apps/openmw/mwrender/animation.cpp` (`ObjectAnimation`), `apps/openmw/mwworld/scene.cpp` (`useAnim` → mechanics add). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same viewer. Phase 12 interior teleport stays. HUD **Cell** / **Cave** / **Nix** / **Guild** unchanged.

**E on a container runs OpenMW `onOpen`.** If the mesh has a `"containeropen"` kf group, that clip plays once (lid lifts). Vanilla `Morrowind.bsa` has no container `x*.kf`, so Census `stolen_goods` (`Contain_Com_Chest_02`) stays still — OpenMW would then `pushGuiMode(GM_Container)`; this port skips the loot window. Baskets and sacks with no that group stay still.

## Why this slice

Doors swing and load. The next hole in “the cell is usable” is furniture you open. OpenMW does not rotate a chest: `Container::useAnim()` is true, so it is an `ObjectAnimation`, and `CharacterController::onOpen` plays the kf group `"containeropen"` once (`loops = 0`) when that group exists. Then it would `pushGuiMode(GM_Container)`. This port plays the lid when the clip exists and always skips the GUI. Vanilla chests have no clip, so **E** is a no-op besides the log.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only.

- Keep placing `CONT` as now (`makeOsgQuat`). If `correctActorModelPath` finds an `x*.kf`, that mesh is the anim root (same `insertModel` path as doors).
- Pick: camera ray, 192, **closest AABB among doors and containers**. Doors keep Phase 11/12 behavior. A chest hit runs the container path, not swing.
- Unlocked / untrapped / not harvested: `ActionOpen` → `MechanicsManager::onOpen` → `CharacterController::onOpen`.
- `onOpen` for a `CONT`: if there is no `"containeropen"` group, treat as opened (OpenMW returns true and would show loot). If that group is already playing (or `"containerclose"`), ignore. Else `play("containeropen", Priority_Scripted, BlendMask_All, autodisable false, speed 1, "start"/"stop", startpoint 0, loops 0)`. If it is then playing, OpenMW delays the GUI (`return false`). This port never opens loot UI.
- Reuse Phase 9 kf sample + bone local overwrite on the **container** nif tree (not `base_anim`). Last-inserted source wins. Play once, no loop.
- Skip lock / trap / key (always allow open). Skip herbalism / `ActionHarvest`. Skip `containerclose` (no GUI to dismiss). Lid stays up.
- Skip SNAM sounds and disease.

Debug CLI: existing `cell` is enough. Optional: print `CONT` id + model like doors.

```bat
gradlew.bat :core:debugCli --args="cell Seyda Neen, Census and Excise Office"
```

Vanilla `Morrowind.bsa` has no `meshes/o/xcontain_*.kf`. A `kf` dump of that path is expected to fail.

## Out of scope

- Inventory / `GM_Container` / taking items from the chest
- `containerclose`, organic harvest, respawn
- World-item pickup (`ActionTake`)
- Lock, trap, key, sounds
- NPC / activator E
- Terrain, plugins, Lua, GPU skinning / `ModelBatch`

## Test cell

**Cell** (Census): look at the wood chest `stolen_goods` / `Contain_Com_Chest_02` (not a basket). **E** — log `cont stolen_goods no containeropen`; the mesh must not swing or teleport. Baskets/sacks the same. Hide doors still swing. Guild load doors still teleport.

```bat
gradlew.bat :core:debugCli --args="cell Seyda Neen, Census and Excise Office"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Census chest **E** does not swing or teleport. Vanilla BSA: log `no containeropen` (OpenMW would open loot). Lid lift is only required when that kf group exists.
- Second **E** while a lid clip is moving does not restart. After it finishes, the lid stays up (no `containerclose`).
- Baskets without `"containeropen"` do not teleport or swing.
- Hide doors still swing. Guild interior doors still load. Nix-hounds still idle.
- Chair HUD unfogged. `glError=0`.

A loot window is out of scope, not a fail. Chest that stays shut after **E** is a **fail** if the kf has `containeropen`.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Insert.** `Container::useAnim()` is true. `insertObjectRendering` calls `Objects::insertModel`. Same `correctActorModelPath` / `ObjectAnimation` / `setObjectRoot(model, false, false, false)` as doors. `addObject` in the scene calls `MechanicsManager::add` when `useAnim()`.
2. **Activate.** Unlocked, untrapped, not harvested: `Container::activate` returns `ActionOpen`. `ActionOpen::executeImp` (player): `onOpen(target)`; if that is false, return without GUI; if true, `pushGuiMode(GM_Container)`. `canBeHarvested` is false unless graphic herbalism, an animation, Organic flag, and a herbalism user description.
3. **onOpen play.** Non-actor `MechanicsManager::onOpen` → `Objects::onOpen` → `CharacterController::onOpen`. For `CONT` with animation: no `"containeropen"` → `true`. Already playing open or close → `false`. Else play that group `Priority_Scripted`, `BlendMask_All`, autodisable false, speed 1, `"start"`/`"stop"`, startpoint 0, **loops 0**. If it is then playing, return `false` (delay loot). Else `true`.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `Container::activate` | **E** + CONT pick | rewrite |
| `ActionOpen` | play lid, skip GUI | rewrite |
| `CharacterController::onOpen` | `containeropen` once | rewrite |
| `Container::useAnim` | object kf on chest nif | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Container::useAnim() returns true. insertObjectRendering
calls Objects::insertModel(ptr, model) when the model is non-empty.
insertModel is the same as doors: animated starts as useAnim(); if
animated and mesh non-empty, correctActorModelPath; if the corrected
path equals the original and it still ends .nif, animated becomes
false. ObjectAnimation setObjectRoot(model, false, false, false) and
addAnimSource only if animated. Scene addObject calls
MechanicsManager::add(ptr) when useAnim() is true.

From apps/openmw/mwclass/container.cpp:
    bool Container::useAnim() const { return true; }
    insertObjectRendering:
        if (!model.empty())
            renderingInterface.getObjects().insertModel(ptr, model);

From apps/openmw/mwrender/objects.cpp insertModel:
    animated = ptr.getClass().useAnim();
    if (animated && !mesh.empty()) {
        animationMesh = correctActorModelPath(mesh);
        if (animationMesh == mesh && ciEndsWith(animationMesh, ".nif"))
            animated = false;
    }
    new ObjectAnimation(ptr, animationMesh, ..., animated, allowLight);

From apps/openmw/mwrender/animation.cpp ObjectAnimation:
    setObjectRoot(model, false, false, false);
    if (animated) addAnimSource(model, model);

From apps/openmw/mwworld/scene.cpp addObject:
    insertObjectRendering(...);
    if (ptr.getClass().useAnim())
        MechanicsManager->add(ptr);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: For an unlocked TES3 container with no trap, if
canBeHarvested is false, Container::activate returns ActionOpen.
ActionOpen::executeImp returns immediately if inventory GUI is not
allowed or the actor is not the player. Otherwise it calls
MechanicsManager::onOpen(getTarget()); if that returns false, it
returns without opening loot. If true, it diseaseContact then
pushGuiMode(GM_Container, getTarget()). canBeHarvested is false when
graphic herbalism is off, or there is no animation, or the Organic
flag is unset, or the nif has no herbalism user description.

From apps/openmw/mwclass/container.cpp (unlocked, no trap):
    if (!canBeHarvested(ptr))
        return ActionOpen(ptr);
    canBeHarvested:
        if (!Settings::game().mGraphicHerbalism) return false;
        if (getAnimation(ptr) == nullptr) return false;
        return animation->canBeHarvested();

From apps/openmw/mwrender/animation.cpp ObjectAnimation::canBeHarvested:
    if type != Container return false;
    if !(mFlags & Organic) return false;
    return hasUserDescription(mObjectRoot, HerbalismLabel);

From apps/openmw/mwworld/actionopen.cpp executeImp:
    if (!isAllowed(GW_Inventory)) return;
    if (actor != getPlayer()) return;
    if (!MechanicsManager->onOpen(getTarget())) return;
    diseaseContact(...);
    pushGuiMode(GM_Container, getTarget());

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: MechanicsManager::onOpen: if the ptr is an actor, return
true; else Objects::onOpen. Objects::onOpen looks up the
CharacterController and calls onOpen(); missing object returns true.
CharacterController::onOpen: if type is Container and mAnimation is
set: if !hasAnimation("containeropen") return true; if
isPlaying("containeropen") or isPlaying("containerclose") return
false; else play("containeropen", Priority_Scripted, BlendMask_All,
false, 1.0, "start", "stop", 0.f, 0) — loops argument is 0. If
isPlaying("containeropen") after play, return false; otherwise fall
through to return true.

From apps/openmw/mwmechanics/mechanicsmanagerimp.cpp:
    onOpen: if (ptr.getClass().isActor()) return true;
            else return mObjects.onOpen(ptr);

From apps/openmw/mwmechanics/objects.cpp:
    onOpen: if found, return iter->second->onOpen();
            return true;

From apps/openmw/mwmechanics/character.cpp CharacterController::onOpen:
    if (mPtr.getType() == ESM::Container::sRecordId && mAnimation) {
        if (!mAnimation->hasAnimation("containeropen")) return true;
        if (mAnimation->isPlaying("containeropen")) return false;
        if (mAnimation->isPlaying("containerclose")) return false;
        mAnimation->play("containeropen", Priority_Scripted,
            BlendMask_All, false, 1.0f, "start", "stop", 0.f, 0);
        if (mAnimation->isPlaying("containeropen")) return false;
    }
    return true;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

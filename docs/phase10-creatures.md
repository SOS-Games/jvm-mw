# Phase 10: Creatures (CREA idle)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwclass/creature.cpp` (`insertObjectRendering`, `getModel` / `getClassModel`, `adjustScale`, `hasInventoryStore`), `apps/openmw/mwrender/objects.cpp` (`insertCreature`), `apps/openmw/mwrender/creatureanimation.cpp` (`CreatureAnimation`), `apps/openmw/mwrender/animation.cpp` (`setObjectRoot` `isCreature`, `addAnimSource`), `components/misc/resourcehelpers.cpp` (`correctActorModelPath`), `components/esm3/loadcrea.cpp`, `components/sceneutil/visitor.cpp` (`RemoveTriBipVisitor`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same Path B viewer. Phase 9 NPC idle stays. **Cell** (Census) and **Cave** (Addamasartus) must not regress — those interiors almost never place `CREA` (they use `LEVC` or none), so this phase adds a third HUD button.

**Punsabanit**: five placed `nix-hound` refs on the moldcave kit. They should stand in the `idle` loop (not T-pose, not a static NIF). Census people still breathe. Addamasartus smugglers still breathe. Chair HUD unfogged.

## Why this slice

Phase 9 finished people. The next hole in “the cell is alive” is creatures. Census and Addamasartus are the wrong rooms for that: the viewer log’s `actor=` skips are empty there, and most cave vermin in vanilla are **leveled lists**, not `CREA` refs.

Punsabanit is a moldcave interior (same kit family as Addamasartus), fog density 1.0, inbound spawn, **five** `CREA` nix-hounds and only one `LEVC` to ignore. Nix-hounds walk, are not bipedal, and have no weapon/shield flag — so they hit `CreatureAnimation`, not `CreatureWeaponAnimation`.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only.

HUD: **Cell** = Census, **Cave** = Addamasartus, **Nix** = `Punsabanit`.

- Parse `CREA` beyond the actor-id skip list: `NAME`, `FNAM`, `MODL`, `FLAG` (low byte; `Bipedal=0x01`, `Weapon=0x04`, `Swims=0x10`, `Flies=0x20`, `Walks=0x40`), `XSCL` (default `1` if the sub is missing).
- Do **not** place a creature as a STAT. `Creature::insertObjectRendering` → `Objects::insertCreature`.
- Model is `CREA.MODL` (`getClassModel`), then `correctActorModelPath` (insert `x` after the last slash if that `x*.kf` exists). If the corrected path equals the original and it still ends `.nif`, OpenMW sets `animated = false` and does not `addAnimSource` the creature kf.
- `setObjectRoot(model, false, false, true)` — not `forceskeleton`, not NPC `baseonly`. The creature NIF **is** the skeleton + skinned mesh. ESM placement is a parent of that nif root (same wrap as Phase 9 so idle can overwrite `Bip01` local). Yaw-only `makeActorOsgQuat`. Scale: ref `XSCL` then `Creature::adjustScale` (`*= CREA.XSCL`).
- After `setObjectRoot` with `isCreature`, drop drawables whose name starts with `"tri bip"` (`RemoveTriBipVisitor`). Do not strip other tris.
- Anim sources (`CreatureAnimation`, unarmed):
  1. If `Bipedal`, `addAnimSource(xbaseanim, model)` (`meshes/xbase_anim.nif` → `.kf`)
  2. If `animated`, `addAnimSource(model, model)` (the corrected creature nif → `.kf`)
- Skip `mUseAdditionalAnimSources` bone inject. Skip `CreatureWeaponAnimation` (no carried weapons/shields even if some other cell’s CREA has `Weapon`).
- Standing idle: same play as Phase 9 (`"idle"`, `BlendMask_All`, autodisable false, speed 1, `"start"`/`"stop"`, loops max, loopfallback true). Cave floor, not water, so not `idleswim`.
- Reuse Phase 9 key sampling + CPU re-skin from bind verts. Last-inserted source still wins.
- `LEVC` stays unresolved (log/skip). Do not pick a random creature from the list.

Debug CLI: dump one `CREA` (id, name, model, corrected path, flags, scale). Do not add another viewer.

```bat
gradlew.bat :core:debugCli --args="crea nix-hound"
gradlew.bat :core:debugCli --args="kf meshes/r/xnixhound.kf"
gradlew.bat :core:debugCli --args="cell Punsabanit"
```

## Out of scope

- `LEVC` resolution
- `CreatureWeaponAnimation`, inventory, shields, arrows
- `idle2`–`idle9`, walk, combat, `idleswim` / `idlesneak`, fly
- Dead / 0-HP placed corpses as a special pose
- 1st person, werewolf, extra anim-source folders
- GPU skinning / `ModelBatch`
- AI, pathgrid, dialogue, actor sounds
- Door open/close, plugins, terrain, Lua, Bullet

## Test cell

Punsabanit. Walk in from the inbound door; nix-hounds should idle in the cave. **Cell** and **Cave** still Census / Addamasartus.

```bat
gradlew.bat :core:debugCli --args="crea nix-hound"
gradlew.bat :core:debugCli --args="kf meshes/r/xnixhound.kf"
gradlew.bat :core:debugCli --args="cell Punsabanit"
gradlew.bat :core:debugCli --args="spawn Punsabanit"
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Punsabanit shows several nix-hounds, not skipped `actor=` holes.
- They idle (looping motion), not T-pose and not a frozen first frame.
- Skin stays on the body (no floor splat). Yaw-only facing, `CREA` scale 1.
- Census NPCs still idle. Addamasartus smugglers still idle. Moldcave seams still closed.
- Chair HUD unfogged. `glError=0`.

Missing creatures in Punsabanit is a **fail**. T-pose hounds is a **fail**. Resolving `LEVC` into extra random critters is out of scope, not a fail.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Insert, model, scale, animated.** `insertObjectRendering` calls `insertCreature(ptr, model, hasInventoryStore)`. `hasInventoryStore` is the `Weapon` flag. `insertCreature` always `correctActorModelPath`s the mesh; if that returns the same path and it still ends `.nif`, `animated` is false. Non-weapon creatures construct `CreatureAnimation`. Rendering scale is ref scale then `Creature::adjustScale`: `scale *= CREA.mScale` (`XSCL`, default 1). Model string is `CREA.mModel`.
2. **CreatureAnimation sources.** Constructor `setObjectRoot(model, false, false, true)`. If `Bipedal`, `addAnimSource(xbaseanim, model)`. If `animated`, `addAnimSource(model, model)`. `setObjectRoot` with `isCreature` then runs `RemoveTriBipVisitor` (node names starting `"tri bip"`). It does not `updateParts` body slots.
3. **Standing idle.** Same as Phase 9 for dry land: `idleStateToAnimGroup(CharState_Idle)` is `"idle"`. `inwater` is what switches to `idleswim`. Unarmed `refreshIdleAnims` still plays `"idle"` with `BlendMask_All`, autodisable false, speed 1, `"start"`/`"stop"`, max loops, loopfallback true.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::Creature` | `esm.EsmCreature` | same |
| `MWClass::Creature::insertObjectRendering` | `CellSceneBuilder` CREA place | rewrite |
| `MWRender::Objects::insertCreature` | creature build on mannequin path | rewrite |
| `MWRender::CreatureAnimation` | idle on creature nif root | rewrite |
| `SceneUtil::RemoveTriBipVisitor` | skip `"tri bip"` drawables | rewrite |
| `Creature::adjustScale` | `*= CREA.XSCL` | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Creature::insertObjectRendering calls
Objects::insertCreature(ptr, model, hasInventoryStore(ptr)).
hasInventoryStore is the Weapon flag (0x04). insertCreature
correctActorModelPath's the mesh; animated starts true, then if
the corrected path equals the original and it still ends .nif,
animated becomes false. weaponsShields true → CreatureWeaponAnimation,
else CreatureAnimation. Rendering scale is the cell-ref scale then
Creature::adjustScale, which does scale *= CREA.mScale. getModel is
getClassModel<ESM::Creature> = CREA.mModel. XSCL defaults to 1 when
the subrecord is absent.

From apps/openmw/mwclass/creature.cpp:
    insertObjectRendering:
        objects.insertCreature(ptr, model, hasInventoryStore(ptr));
    hasInventoryStore:
        return isFlagBitSet(ptr, ESM::Creature::Weapon);
    getModel:
        return getClassModel<ESM::Creature>(ptr);
    adjustScale:
        scale *= ref->mBase->mScale;

From apps/openmw/mwclass/classmodel.hpp:
    return ref->mBase->mModel;

From apps/openmw/mwrender/objects.cpp insertCreature:
    bool animated = true;
    animationMesh = correctActorModelPath(mesh);
    if (animationMesh == mesh && ciEndsWith(animationMesh, ".nif"))
        animated = false;
    if (weaponsShields)
        anim = new CreatureWeaponAnimation(..., animated);
    else
        anim = new CreatureAnimation(..., animated);

From components/esm3/loadcrea.cpp:
    mScale = 1.f;
    FLAG: mFlags = flags & 0xFF;
    XSCL: esm.getHT(mScale);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: CreatureAnimation (the unarmed class) calls
setObjectRoot(model, false, false, true). If the CREA Bipedal flag
is set, it addAnimSource(xbaseanim, model). If animated, it
addAnimSource(model, model). It does not call updateParts.
setObjectRoot's isCreature path then RemoveTriBipVisitor, which
removes nodes whose name starts with "tri bip". Additional-anim
skeleton inject in setObjectRoot is behind
Settings::game().mUseAdditionalAnimSources.

From apps/openmw/mwrender/creatureanimation.cpp CreatureAnimation:
    setObjectRoot(model, false, false, true);
    if (ref->mBase->mFlags & ESM::Creature::Bipedal)
        addAnimSource(Settings::models().mXbaseanim.get(), model);
    if (animated)
        addAnimSource(model, model);

From apps/openmw/mwrender/animation.cpp setObjectRoot:
    void setObjectRoot(..., bool forceskeleton, bool baseonly, bool isCreature)
    if (Settings::game().mUseAdditionalAnimSources && isActor)
        if (isCreature && Bipedal) inject defaultSkeleton = xbaseanim
    if (!forceskeleton) load model as-is (Skeleton only if the nif is one)
    if (isCreature)
        RemoveTriBipVisitor on mObjectRoot

From components/sceneutil/visitor.cpp RemoveTriBipVisitor::applyImpl:
    if (ciStartsWith(node.getName(), "tri bip"))
        mark node for remove from parent

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: A standing creature on dry land uses the same idle as an
unarmed NPC. CharacterController sets CharState_Idle unless sneak
or inwater; inwater becomes CharState_IdleSwim. idleStateToAnimGroup
(CharState_Idle) is "idle". refreshIdleAnims plays that group with
BlendMask_All, autodisable false, speed 1, keys "start"/"stop",
loops = uint32 max, loopfallback true. play() still searches
mAnimSources last-inserted first.

From apps/openmw/mwmechanics/character.cpp:
    idleStateToAnimGroup(CharState_Idle) → "idle"
    idleStateToAnimGroup(CharState_IdleSwim) → "idleswim"
    if sneak && !jump → IdleSneak
    else → Idle
    if inwater → IdleSwim
    refreshCurrentAnims(idlestate, ...)
    refreshIdleAnims / playBlendedAnimation (mLuaAnimations false):
        play(group, BlendMask_All, autodisable false, 1.0,
            "start", "stop", startpoint, max loops, loopfallback true)

From apps/openmw/mwrender/animation.cpp play():
    AnimSourceList::reverse_iterator iter(mAnimSources.rbegin());

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

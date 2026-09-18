# Phase 9: NPC idle (.kf)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwrender/npcanimation.cpp` (`updateNpcBase` `addAnimSource`), `apps/openmw/mwrender/animation.cpp` (`addAnimSource` / `addSingleAnimSource` / `play` / `reset`), `apps/openmw/mwmechanics/character.cpp` (`refreshIdleAnims`, `idleStateToAnimGroup`, `playBlendedAnimation`), `components/nifosg/nifloader.cpp` (`Loader::loadKf`), `components/nif/data.cpp` (`NiKeyframeData::read`), `components/nifosg/controller.cpp` (`KeyframeController`). Other-LLM checks of those three claims **held** (2026-09-18).

## Goal

Same Path B viewer. Phase 8 people stay. Census office NPCs should stand in the **idle** loop (weight shift / breathe), not T-pose. Addamasartus smugglers the same. Chair HUD, cave seams, fog, and clothes-on-skeleton still hold.

## Why this slice

Phase 8 put bodies on `base_anim`. They look like mannequins because we never load `xbase_anim.kf`. OpenMW plays group `idle` as soon as an NPC is inserted. That is the remaining hole in the same two test cells.

Creatures, combat, walk, and random `idle2`–`idle9` wait.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only. Test cell: **Seyda Neen, Census and Excise Office** (Cell). Cave button still Addamasartus.

- Parse Morrowind `.kf`: root `NiSequenceStreamHelper` (`NiObjectNET`: name, extra, controller). Extra list: first `NiTextKeyExtraData` (time + string, split on `\r\n`, trim, lower-case). Remaining extras pair with the controller chain: `NiStringExtraData` (bone name) + `NiKeyframeController` + `NiKeyframeData`.
- `NiKeyframeData`: quaternion keys, then translations, then scales. (XYZ euler maps only if interpolation type is XYZ — vanilla `xbase_anim.kf` uses quats.)
- After Phase 8 `setObjectRoot` / `updateParts`, load anim sources the way `NpcAnimation::updateNpcBase` does for 3rd-person non-werewolf:
  1. `xbaseanim` (`meshes/xbase_anim.nif`)
  2. the actor skeleton NIF if it is not that same path (`xbase_anim_female`, `xbase_animkna`, …)
  3. a custom `NPC_.MODL` only if it is not a default skeleton (vanilla chargen NPCs are not)
- `Animation::addAnimSource`: if the path ends `.nif`, swap the extension to `.kf`. Do **not** use the `xbaseanimkf` setting for the path (that setting is preload only). Skip extra folder sources (`mUseAdditionalAnimSources`).
- Map each controller to the actor `NodeMap` bone (case-insensitive). Missing bones: warn and skip, as OpenMW does.
- Standing idle only: equivalent of `refreshIdleAnims(CharState_Idle)` → `play("idle", BlendMask_All, autodisable=false, speed=1, "start", "stop", startpoint=0, loops=max, loopfallback=true)`. Unarmed, so no `idle1h` suffix.
- `Animation::reset` finds keys `idle: start` and `idle: stop` (group match is `name + ": "`). `loopfallback` sets loop bounds to those same times when no loop keys have been seen yet.
- Each frame: sample keyframes at the playing time, **overwrite** that bone’s local rotation / translation / scale (`KeyframeController` does not multiply rest pose). Recompute skeleton-space bone worlds. Re-run rest-pose `Skinning.apply` from **bind** verts (keep a copy). Rigid head/hair already parented to `Head` follow the bone.
- Advance time in the viewer loop. Wrap at loop stop back to loop start.

Debug CLI: dump one `.kf` (root, text-key groups, first/last time per group, bone controller count). Do not add another viewer.

```bat
gradlew.bat :core:debugCli --args="kf meshes/xbase_anim.kf"
```

## Out of scope

- `idle2`–`idle9`, sneak/swim/storm, lips, talk
- Walk, jump, combat, weapon-type idle suffixes (`idle1h`, …)
- `CREA`
- 1st person, werewolf, vampire heads, Argonian swim kf
- Additional anim-source folders
- GPU skinning / `ModelBatch`
- AI, pathgrid, dialogue, actor sounds
- Door open/close, plugins, terrain, Lua, Bullet

## Test cell

Census office. Walk in; Sellus and the chargen NPCs should idle. **Cave** smugglers idle too.

```bat
gradlew.bat :core:debugCli --args="kf meshes/xbase_anim.kf"
gradlew.bat :core:debugCli --args="kf meshes/xbase_anim_female.kf"
gradlew.bat :core:debugCli --args="npc sellus gravius"
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Census NPCs are not T-pose: arms down / slight motion in the idle loop.
- Clothes and skin stay on the body while they move (no floor splat, no detached limbs).
- They still face yaw and keep race scale.
- Chair HUD unfogged. Census furniture and Addamasartus walls unchanged. `glError=0`.

T-pose after load is a **fail**. Frozen first idle frame is a **fail** if the loop never advances.

## Other-LLM claims (held 2026-09-18)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Which `.kf` files an NPC loads.** 3rd-person non-werewolf `updateNpcBase` always `addAnimSource(xbaseanim, smodel)`. If the corrected skeleton path differs from `xbaseanim`, it adds that too (female / kna). Custom `NPC_.MODL` is a third source only when it is not a default skeleton. `addAnimSource` takes a `.nif` path and changes the extension to `.kf`; it does not read `xbaseanimkf`.
2. **`.kf` layout.** `loadKf` requires a `NiSequenceStreamHelper` root. Extra[0] is `NiTextKeyExtraData` (keys become lower-case `"group: event"`). Extra[i≥1] is `NiStringExtraData` whose string is the bone name, paired with the next `NiKeyframeController` in the helper’s controller list. `NiKeyframeData` is rotations, then translations, then scales.
3. **Standing idle.** `idleStateToAnimGroup(CharState_Idle)` is `"idle"`. Unarmed `refreshIdleAnims` plays that group with `BlendMask_All`, autodisable false, speed 1, keys `"start"`/`"stop"`, `loops = uint32 max`, `loopfallback true`. `play` searches anim sources last-inserted first. `KeyframeController` **sets** the bone’s rotation/translation/scale from interpolated keys (it does not compose with the NIF rest pose).

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `Nif::NiSequenceStreamHelper` | `nif.NiSequenceStreamHelper` | same |
| `Nif::NiTextKeyExtraData` | `nif.NiTextKeyExtraData` | same |
| `Nif::NiKeyframeController` / `NiKeyframeData` | `nif.NiKeyframeController` / `NiKeyframeData` | same |
| `NifOsg::Loader::loadKf` | `nif.KfFile` / `Skinning` helper | rewrite |
| `MWRender::Animation::addAnimSource` / `play` | idle player on `NpcMannequin` | rewrite |
| `NifOsg::KeyframeController` | sample keys → bone `SceneNode.local` | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: For a 3rd-person non-werewolf NPC, updateNpcBase always
addAnimSource(xbaseanim, smodel) after setObjectRoot + updateParts.
xbaseanim is meshes/xbase_anim.nif (3rd person). If defaultSkeleton
(the corrected getActorSkeleton path) != that xbaseanim path, it also
addAnimSource(defaultSkeleton, smodel) — so female/kna get a second
source. A custom NPC_.MODL is a third source only when
!isDefaultActorSkeleton. addAnimSource copies the model path and, if
the extension is nif, changes it to kf, then addSingleAnimSource.
It does not read Settings::models().mXbaseanimkf for the filename.

From apps/openmw/mwrender/npcanimation.cpp updateNpcBase (firstPerson=false,
isWerewolf=false):
    base = Settings::models().mXbaseanim.get().value();
    defaultSkeleton = correctActorModelPath(getActorSkeleton(...));
    smodel = defaultSkeleton;
    ... optional custom NPC_.MODL into smodel ...
    setObjectRoot(smodel, true, true, false);
    updateParts();
    if (!base.empty())
        addAnimSource(base, smodel);
    if (defaultSkeleton != base)
        addAnimSource(defaultSkeleton, smodel);
    if (isCustomModel)
        addAnimSource(smodel, smodel);

From files/settings-default.cfg:
    xbaseanim = meshes/xbase_anim.nif
    xbaseanimkf = meshes/xbase_anim.kf

From apps/openmw/mwrender/animation.cpp:
    void Animation::addAnimSource(std::string_view model, const std::string& baseModel)
    {
        VFS::Path::Normalized kfname(model);
        if (kfname.extension() == nif)
            kfname.changeExtension(kf);
        addSingleAnimSource(kfname, baseModel);
        if (Settings::game().mUseAdditionalAnimSources)
            loadAdditionalAnimations(kfname, baseModel);
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Loader::loadKf finds a NiSequenceStreamHelper root. Its extra
list’s first record must be NiTextKeyExtraData; each text is split on
CR/LF, trimmed, lower-cased, and stored at that time. Then extra[i]
(i starting at 1) walks in lockstep with seq->mController / mNext:
NiStringExtraData.mData is the bone name, NiKeyframeController supplies
the keys. NiKeyframeData::read is quaternion key map, then (if type XYZ)
three float maps, then translation Vector3KeyMap, then scale FloatKeyMap.
KeyframeController applies interpolated rotation/translation/scale by
setRotation/setTranslation/setScale on the bone MatrixTransform — it
replaces the rest-pose channels that have keys, it does not multiply
the NIF rest matrix.

From components/nifosg/nifloader.cpp loadKf:
    find root RC_NiSequenceStreamHelper
    extraList = seq->getExtraList();
    extraList[0] is NiTextKeyExtraData → extractTextKeys
    ctrl = seq->mController;
    for i = 1; i < extraList.size() && !ctrl.empty(); i++, ctrl = ctrl->mNext
        extra must be NiStringExtraData
        ctrl must be NiKeyframeController
        emplace(strdata->mData, KeyframeController(key))

From components/nif/data.cpp NiKeyframeData::read:
    mRotations->read(nif);
    if XYZ: axis order + X/Y/Z float maps
    mTranslations->read(nif);
    mScales->read(nif);

From components/nifosg/controller.cpp KeyframeController::operator():
    if (rotation) node->setRotation(*rotation);
    if (translation) node->setTranslation(*translation);
    if (scale) node->setScale(*scale);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: A standing unarmed NPC idle is CharacterController
refreshIdleAnims(CharState_Idle). idleStateToAnimGroup returns "idle".
Weapon short-group is empty so the group stays "idle" and numLoops is
uint32 max. playBlendedAnimation calls Animation::play with
BlendMask_All, autodisable false, speed 1, start "start", stop "stop",
startpoint from current if already that group else 0, loops = max,
loopfallback true. play() looks at mAnimSources in reverse
(last-inserted wins). reset() matches keys that start with
groupname + ": " and then the event; loopfallback sets
mLoopStartTime/mLoopStopTime to the start/stop key times.

From apps/openmw/mwmechanics/character.cpp:
    idleStateToAnimGroup(CharState_Idle) → "idle"
    refreshIdleAnims:
        numLoops = numeric_limits<uint32_t>::max();
        weapShortGroup empty → skip idle1h
        playBlendedAnimation(mCurrentIdle, priority, BlendMask_All, false,
            1.0f, "start", "stop", startPoint, (uint32_t)numLoops, true);
    playBlendedAnimation (mLuaAnimations false):
        mAnimation->play(groupname, priority, blendMask, autodisable,
            speedmult, start, stop, startpoint, loops, loopfallback);

From apps/openmw/mwrender/animation.cpp play():
    AnimSourceList::reverse_iterator iter(mAnimSources.rbegin());
    reset(state, textkeys, groupname, start, stop, startpoint, loopfallback);

From Animation::reset:
    group key: starts_with(groupname) && compare(size, 2, ": ") == 0
    if loopfallback: mLoopStartTime = startkey; mLoopStopTime = stopkey;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

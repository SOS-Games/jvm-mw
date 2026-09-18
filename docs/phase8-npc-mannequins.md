# Phase 8: NPC mannequins

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `apps/openmw/mwworld/scene.cpp` (`makeActorOsgQuat`), `apps/openmw/mwclass/npc.cpp` (`insertObjectRendering`, `getCorrectedModel`, `adjustScale`), `apps/openmw/mwrender/npcanimation.cpp` (`updateNpcBase`, `updateParts`, `sPartList`, `getBodyParts`), `apps/openmw/mwrender/actorutil.cpp` (`getActorSkeleton`), `apps/openmw/mwrender/actoranimation.cpp` (`attach`), `components/sceneutil/attach.cpp` (`SceneUtil::attach` / `CopyRigVisitor`), `components/nifosg/nifloader.cpp` (`NiSkinInstance` → `RigGeometry`), `files/settings-default.cfg` (`baseanim` / `baseanimfemale` / `baseanimkna`). Other-LLM checks of those three claims **held** (2026-09-18).

## Goal

Same Path B viewer. Phase 7 cave seams stay. The **Census office** should have people in it: Imperial uniforms, heads and hair, standing at their refs, facing yaw. Bind pose / T-pose is fine.

Addamasartus smugglers should appear if the same NPC path runs there. Chair HUD unchanged. Cave walls still sealed.

## Why this slice

Interiors look furnished. The remaining hole in the Census office is empty air where Sellus Gravius and the chargen NPCs stand. An NPC is not a `MODL` on the `NPC_` record — that path is `base_anim.nif` (skeleton). The visible body is **BODY** meshes hung on named bones, then clothes/armor `PartReference`s replacing skin.

`.kf` idle, creatures, and talking heads are a later chapter. This phase is “there are people,” not “they breathe.”

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only. Test cell: **Seyda Neen, Census and Excise Office** (Cell button). Cave button still Addamasartus.

- Parse `NPC_` beyond the skip list: `NAME`, `FNAM` (display name), `RNAM` (race), `BNAM` (head BODY id), `KNAM` (hair BODY id), `FLAG` female bit `0x01`, `MODL` if any, `NPCO` inventory ids. (`ANAM` is faction, not hair.)
- Parse `RACE` (`RADT`: beast flag, male/female height and weight).
- Parse `BODY` (`FNAM` race, `MODL`, `BYDT`: part, vampire, flags, type).
- Parse `CLOT`/`ARMO` `INDX` part lists (`PartReference`: part index + male/female BODY ids). World items already draw ground meshes; NPCs need the part list.
- **Do not** place an NPC by stuffing `NPC_.MODL` into the STAT path. `Npc::insertObjectRendering` calls `insertNPC` → `NpcAnimation`.
- Skeleton (3rd person, not werewolf): `getActorSkeleton` → `meshes/base_anim.nif` (male), `meshes/base_anim_female.nif` (female), `meshes/base_animkna.nif` (beast race). Then `correctActorModelPath` (x-prefix if the x-file exists — OpenMW does this; if the x-file is missing, use the non-x path).
- Root `NiNode` named `bip01` **keeps** its transform (our `NiNode::read` identity skip already). Actor files use that name.
- Placement: same cell −90° X, **yaw-only** `Quat(rot[2], (0,0,-1))`, not `makeOsgQuat`. Scale: ref `XSCL` then `Npc::adjustScale` — male `weight` on X/Y and `height` on Z (female likewise). Not 1st-person.
- `updateParts` order: inventory CLOT/ARMO into slots (OpenMW `autoEquip` then slot list robe→…→carried), then head/hair from the NPC record if those slots are free, then leftover `MT_Skin` BODY parts for that race/gender (`getBodyParts`). Robe/skirt reserve hidden skin parts.
- Attach: `sPartList` bone names (`Head`, `Chest`, `Right Hand`, …). Hair uses bone `Head` but filter `"hair"`. `ActorAnimation::attach` finds that bone on the skeleton.
- `SceneUtil::attach`: if the part NIF is a **skeleton**, copy `RigGeometry` whose node names start with the filter (or `"tri "` + filter) onto the actor skeleton. Else parent a clone under the bone; apply `BoneOffset` translation if present; if the bone name contains `"Left"`, scale `(-1,1,1)` and front-face **CLOCKWISE**.
- Skinning: parse `NiSkinInstance` / `NiSkinData` (Morrowind has no `NiSkinPartition`). Packed `NiTransform` is **rotation, translation, scale** (`NIFStream::read<NiTransform>`), not the `NiAVObject` translation-first order. `RigGeometry` uses each bone’s `mTransform.toMatrix()` as inverse bind, weights per vertex, plus `NiSkinData::mTransform`. At bind pose (no `.kf`) skin with the skeleton’s rest bone matrices so limbs stay connected.
- Lights still come from `LIGH` refs. Equipped torches wait (`mShowWeapons` starts false; carried-left lights only if you already have the slot — skip rather than fake).

Debug CLI: dump one NPC (id, race, female, head, hair, skeleton path, equipped parts). Do not add another viewer.

## Out of scope

- `.kf` / idle / walk / lips (T-pose is the pass)
- `CREA` (creature NIF + its own skeleton)
- 1st person, werewolf, vampire heads
- Weapons, ammunition, shield sheathing, enchanted glow
- Actor sounds, AI, pathgrid, dialogue
- Plugins, terrain, Lua, Bullet
- Door open/close

## Test cell

Census office. Walk in; people should be at the desks and the front counter. **Cave** still Addamasartus (smugglers if the NPC path is shared).

```bat
gradlew.bat :core:debugCli --args="nif meshes/base_anim.nif"
gradlew.bat :core:debugCli --args="cell Seyda Neen, Census and Excise Office"
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- Chargen NPCs visible in the Census office (not skipped, not a lone skeleton).
- Head + hair + Imperial clothes on Sellus Gravius (or whoever stands at the counter). Naked T-pose with a head is a **fail**.
- They face yaw (`rot[2]`), not full XYZ like furniture.
- Bind pose / arms-out is OK. Floating disconnected limbs is a **fail**.
- Chair HUD unfogged. Census furniture and Addamasartus walls unchanged. `glError=0`.

## Other-LLM claims (held 2026-09-18)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Actor insert rotation and race scale.** First-load `addObject` uses `makeDirectNodeRotation`: actors get `makeActorOsgQuat` = `Quat(rot[2], (0,0,-1))` only. NPCs are actors. Rendering scale is ref scale then `Npc::adjustScale`: male weight on X/Y, male height on Z (female the same with female stats). Collision does not get race scale.
2. **Skeleton then parts.** 3rd-person skeleton is `getActorSkeleton` (`base_anim` / `base_anim_female` / `base_animkna`). `NpcAnimation` `setObjectRoot`s that, then `updateParts`: equipped CLOT/ARMO part groups, then NPC head/hair BODY meshes, then remaining racial `MT_Skin` parts. Each part attaches to `sPartList` (hair → bone `Head`, filter `hair`).
3. **Attach two paths.** `SceneUtil::attach`: if the part template is a `Skeleton`, `CopyRigVisitor` copies matching `RigGeometry` onto the master (skinned). Otherwise clone under the attach bone; `BoneOffset` supplies translation; bone names containing `Left` get scale `(-1,1,1)` and clockwise front faces. Skinned geometry comes from `NiSkinInstance` (`invBind` = `NiSkinData` bone `mTransform.toMatrix()`).

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `makeActorOsgQuat` | `EsmTransforms.setActorLocal` | rewrite |
| `Npc::adjustScale` | race height/weight on instance scale | rewrite |
| `MWRender::NpcAnimation` | `render.NpcMannequin` | rewrite |
| `SceneUtil::attach` | part attach + left mirror | rewrite |
| `Nif::NiSkinInstance` | `nif.NiSkinInstance` | same |
| `SceneUtil::RigGeometry` | skinned mesh on `SceneNode` | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: When adding an actor the first time, addObject uses makeDirectNodeRotation,
which for isActor() is makeActorOsgQuat: Quat(position.rot[2], (0,0,-1)) only
(yaw around negated Z). Non-actors still use makeOsgQuat (Z*Y*X). NPCs are
inserted via Npc::insertObjectRendering → insertNPC, not the static mesh path.
For rendering, Npc::adjustScale multiplies the PAT scale by race male/female
weight on X and Y and height on Z. Collision (rendering=false) skips that.

From apps/openmw/mwworld/scene.cpp:
    osg::Quat makeActorOsgQuat(const ESM::Position& position)
    {
        return osg::Quat(position.rot[2], osg::Vec3(0, 0, -1));
    }
    osg::Quat makeDirectNodeRotation(...) {
        return ptr.getClass().isActor() ? makeActorOsgQuat(pos) : Misc::Convert::makeOsgQuat(pos);
    }

From apps/openmw/mwclass/npc.cpp:
    void Npc::insertObjectRendering(...) const {
        renderingInterface.getObjects().insertNPC(ptr);
    }
    void Npc::adjustScale(..., osg::Vec3f& scale, bool rendering) const {
        if (!rendering)
            return; // collision meshes are not scaled based on race height
        ...
        if (ref->mBase->isMale()) {
            scale.x() *= race->mData.mMaleWeight;
            scale.y() *= race->mData.mMaleWeight;
            scale.z() *= race->mData.mMaleHeight;
        } else {
            scale.x() *= race->mData.mFemaleWeight;
            scale.y() *= race->mData.mFemaleWeight;
            scale.z() *= race->mData.mFemaleHeight;
        }
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: A 3rd-person NPC skeleton is getActorSkeleton: werewolf wolfskin, else
beast baseanimkna, else female baseanimfemale, else baseanim. NpcAnimation
updateNpcBase setObjectRoot(smodel) then updateParts(). updateParts applies
equipped CLOT/ARMO part groups first (robe/skirt reserve extra skin slots),
then head and hair from the NPC record if those parts are still free, then
getBodyParts(race, female, ...) MT_Skin meshes for remaining slots from Neck
onward. sPartList maps PRT_Head and PRT_Hair both to bone "Head"; hair’s
filter is "hair". Weapons start hidden (mShowWeapons false in the ctor).

From apps/openmw/mwrender/actorutil.cpp getActorSkeleton (firstPerson=false):
    if (isWerewolf) return wolfskin;
    else if (isBeast) return baseanimkna;
    else if (isFemale) return baseanimfemale;
    else return baseanim;

From files/settings-default.cfg:
    baseanim = meshes/base_anim.nif
    baseanimkna = meshes/base_animkna.nif
    baseanimfemale = meshes/base_anim_female.nif

From apps/openmw/mwrender/npcanimation.cpp:
    sPartList: { PRT_Head, "Head" }, { PRT_Hair, "Head" }, ...
    NpcAnimation ctor: mShowWeapons(false); updateNpcBase();
    updateNpcBase: setObjectRoot(smodel, true, true, false); updateParts();
    updateParts: inventory slotlist first; then
        if (mPartPriorities[PRT_Head] < 1 && !mHeadModel.empty()) add head;
        if (mPartPriorities[PRT_Hair] < 1 && mPartPriorities[PRT_Head] <= 1
            && !mHairModel.empty()) add hair;
        then for part = PRT_Neck .. PRT_Count-1, if priority < 1, add skin BODY.
    addOrReplaceIndividualPart: bonefilter = (type == PRT_Hair) ? "hair" : bonename;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: ActorAnimation::attach finds bonename on the actor NodeMap and calls
SceneUtil::attach(template, master, filter, attachNode). If the template is a
SceneUtil::Skeleton, CopyRigVisitor copies RigGeometry (and parents whose names
start with the filter, or "tri "+filter) onto the master — the mesh stays
skinned to the actor skeleton. Otherwise the clone is parented under attachNode;
a child named BoneOffset contributes its matrix translation; if attachNode’s
name contains "Left", scale is (-1,1,1) and FrontFace is CLOCKWISE.
NiSkinInstance on a NiTriShape becomes RigGeometry: each bone’s inverse bind
is NiSkinData bone mTransform.toMatrix(), plus per-vertex weights and
NiSkinData::mTransform.

From apps/openmw/mwrender/actoranimation.cpp ActorAnimation::attach:
    found = nodeMap.find(bonename);
    return SceneUtil::attach(templateNode, mObjectRoot, bonefilter, found->second, ...);

From components/sceneutil/attach.cpp SceneUtil::attach:
    if (dynamic_cast<const SceneUtil::Skeleton*>(toAttach.get())) {
        CopyRigVisitor copyVisitor(handle, filter);
        ... copies RigGeometry whose names match filter ...
        master->asGroup()->addChild(...);
    } else {
        FindByNameVisitor findBoneOffset("BoneOffset");
        if (findBoneOffset.mFoundNode)
            trans->setPosition(boneOffset->getMatrix().getTrans());
        if (attachNode->getName().find("Left") != npos) {
            trans->setScale(osg::Vec3f(-1.f, 1.f, 1.f));
            frontFace CLOCKWISE;
        }
        attachNode->addChild(trans or cloned);
    }

From components/nifosg/nifloader.cpp handleNiGeometry:
    if (!niGeometry->mSkin.empty()) {
        RigGeometry rig; rig.setSourceGeometry(geom);
        boneInfo[i].mInvBindMatrix = data->mBones[i].mTransform.toMatrix();
        influences[vertex] weights;
        rig->setTransform(data->mTransform.toMatrix());
        drawable = rig;
    }

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

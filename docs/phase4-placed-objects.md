# Phase 4: placed objects (mesh only)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/loadmisc.cpp` / `loaddoor.cpp` / `loadcont.cpp` / `loadligh.cpp` / `loadacti.cpp` / `loadbook.cpp` / `loadalch.cpp` / `loadingr.cpp` / `loadweap.cpp` / `loadarmo.cpp` / `loadclot.cpp` / `loadappa.cpp` / `loadlock.cpp` / `loadprob.cpp` / `loadrepa.cpp`, `apps/openmw/mwclass/classmodel.hpp` (`getClassModel` → `mModel`), `apps/openmw/mwworld/scene.cpp` (`makeDirectNodeRotation`), `components/misc/resourcehelpers.cpp` (`isHiddenMarker`, `correctMeshPath`). Other-LLM checks of those three claims **held** (2026-09-18).

## Goal

Same Path B viewer, same Census office. Phase 3 drew **STAT** only, so desks are empty and doorways have holes. Index every TES3 record that is a **placed mesh** (NAME + MODL) and draw those refs with the Phase 3 placement path.

The office should look furnished: bottles on tables, doors in frames, chests, candles, books. Still no NPCs, no glow from lights, no open/close.

Phase 1–3 HUD meshes and the Cell button still work.

## Why this slice

Cell refs are already parsed. The skip list is the hole. OpenMW’s `getClassModel` is `ref->mBase->mModel` for these types; insertion uses the **same** `makeOsgQuat` as statics (`makeDirectNodeRotation` when `!isActor()`).

Actors (`NPC_`, `CREA`) also have MODL but need skinning / `.kf`. Leveled lists (`LEVI`, `LEVC`) have no mesh until resolved. Those wait.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` only. Same interior as Phase 3.

While scanning the ESM, keep NAME + MODL for:

| Rec | OpenMW type | Census-office examples |
| --- | --- | --- |
| `STAT` | `ESM::Static` | (already Phase 3) |
| `DOOR` | `ESM::Door` | `ex_nord_door_01`, `in_c_door_arched` |
| `CONT` | `ESM::Container` | chests, sacks, baskets, `CharGen_Bed` |
| `MISC` | `ESM::Miscellaneous` | buckets, goblets, plates, keys |
| `BOOK` | `ESM::Book` | Brief History volumes, notes, `sc_paper plain` |
| `LIGH` | `ESM::Light` | candles, lamps, chandeliers (mesh only) |
| `ACTI` | `ESM::Activator` | if any in this cell |
| `ALCH` | `ESM::Potion` | sujamma / local brew |
| `INGR` | `ESM::Ingredient` | bread, crab meat, kwama egg |
| `WEAP` | `ESM::Weapon` | chargen dagger |
| `APPA` | `ESM::Apparatus` | |
| `ARMO` | `ESM::Armor` | ground model, not biped |
| `CLOT` | `ESM::Clothing` | ground model, not biped |
| `LOCK` | `ESM::Lockpick` | apprentice pick |
| `PROB` | `ESM::Probe` | |
| `REPA` | `ESM::Repair` | |

Implementation can be one `id → {model, rec}` map. Do not port inventory, scripts, sounds, enchantments, or LHDT light data.

Placement (unchanged from Phase 3):

- Same `FRMR` + `NAME` + `XSCL` + `DATA` refs.
- `TexturePaths.normalizeMeshPath` on `MODL`.
- `EsmTransforms.setLocal` (`makeOsgQuat`).
- Cell root **one** −90° X. Instance NIFs `build(false)`.
- Skip `DELE`. Skip empty `MODL` (log it).
- Skip hidden markers: `prisonmarker`, `divinemarker`, `templemarker`, `northmarker` (`isHiddenMarker`).

Door `DODT` / `DNAM` on the **ref** stay skipped (teleport dest). Draw the closed door NIF.

HUD status: `placed=` total, plus skip buckets: unknown id / empty model / actor. Optional per-rec counts in the log (`door=`, `misc=`, `ligh=`, …).

NAME_MAP rows below when the types exist.

## Coordinate trap

Same as Phase 3. Do not add a second −90°. Do not use actor yaw-only quat (`makeActorOsgQuat`) or inverse-order (`makeInversedOrderObjectOsgQuat`) — those are not first-load object insert.

## Out of scope

- `NPC_`, `CREA`, `BODY`, skinning, `.kf`
- `LEVI` / `LEVC` resolve
- Plugin override, Tribunal/Bloodmoon
- Door open/close animation, container contents, picking up items
- Real `LIGH` (radius / color / flicker). Candle **meshes** yes; the room stays Phase 2 directional light. OpenMW still inserts a light node when MODL is empty — we do **not**.
- `AMBI` fog, water, pathgrid, terrain
- Lua, Bullet, MyGUI

## Test cell

Same as Phase 3: `Seyda Neen, Census and Excise Office`. Walk inside.

Phase 3 baseline on that cell: **268** refs, **94** STAT placed, **174** skipped. After this phase most of those 174 should be meshes (misc/door/cont/ligh/book/…). Leftover skip should be chargen NPCs (`chargen captain`, `chargen class`, …) and empty-model lights (`Flame Light`, `dark_128` if they have no MODL).

## Pass / fail

- Desks have bottles, bowls, books; doorways have door meshes; chests/sacks/baskets sit on the floor.
- Candles/lamps visible as objects, **not** lighting the room.
- Chargen NPCs still missing (no floating error meshes).
- STAT count still 94. Total placed ≫ 94. `glError=0`.
- Chair / Cell HUD switch still works (HUD −90° vs cell-root −90°). Collision hidden. Esc still unlocks the mouse for HUD clicks.

## Other-LLM claims (held 2026-09-18)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **NAME + MODL.** TES3 `ACTI` `ALCH` `APPA` `ARMO` `BOOK` `CLOT` `CONT` `DOOR` `INGR` `LIGH` `LOCK` `MISC` `PROB` `REPA` `WEAP` `load()` stores id from `NAME` and mesh from `MODL` (`mId` / `mModel`), same as STAT. Extra subs are not required to draw. `LIGH` `MODL` may be empty.
2. **Same quat as STAT.** First insert: `makeDirectNodeRotation` → `makeOsgQuat` when `!isActor()`. `getModel` is `getClassModel` → `mBase->mModel`. Not actor yaw-only, not inverse-order.
3. **Empty mesh / markers.** `isHiddenMarker` is exactly `prisonmarker`, `divinemarker`, `templemarker`, `northmarker`. `Light::insertObjectRendering` still inserts when `model` is empty (for the light). We skip drawing; we do not emulate the light. Empty-model skip matches OpenMW **meshes**, not OpenMW dynamic lights.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::Miscellaneous` / Door / Container / Light / … | one `esm.EsmObject` (id + model + rec) or thin per-type types | rewrite |
| `MWClass::getClassModel` | `CellSceneBuilder` lookup | rewrite |
| `Misc::ResourceHelpers::isHiddenMarker` | skip list on those four ids | same |

# Phase 37: Leveled creatures (wild spawns)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/esm3/loadlevlist.hpp` / `loadlevlist.cpp` (`CreatureLevList`, `CNAM` entries), `apps/openmw/mwmechanics/levelledlist.cpp` (`getLevelledItem`), `apps/openmw/mwclass/creaturelevlist.cpp` (`insertObjectRendering`). One other-LLM check: the creature `AllLevels` flag (easy to copy from `ItemLevList`). Parse layout and “place a CREA at the marker” are in the pin.

## Goal

Same viewer. Town / Cave / Zain / walk stay. **Wilderness spawn markers become idle creatures.**

Town’s 5×5-minus-corners currently places **3** hardcoded `CREA` and skips **115** `LEVC` refs (`ex_bittercoast_lev+0`, `h2o_all_lev+2`, mudcrabs, cliff racers, …). After this slice those markers roll a creature the same way OpenMW does on cell insert. They stand and idle. They do not wander, swim, or fight.

HUD buttons unchanged (including **Zain**). Chair HUD unfogged.

## Why this slice

Phase 10 placed only real `CREA` refs (Punsabanit nix-hounds) and left `LEVC` unresolved. Almost all outdoor vermin are lists, not named creatures. Walking Town looks empty except for those three.

## In scope

TES3 `Morrowind.esm` + `Morrowind.bsa` + existing extra data folders. No new plugins.

- Parse `LEVC` (not `LEVI`): `NAME`, `DATA` flags (`AllLevels = 0x01`), `NNAM` chance-none (0–100), `INDX` count, then `count` pairs of `CNAM` id + `INTV` uint16 level. Keep a map by lowercased id. Load it on the same ESM pass as `CREA` (interior and exterior).
- On cell place, a ref whose id is a `LEVC` is **not** a mesh. `CreatureLevList::insertObjectRendering` → `getLevelledItem(list, creature=true)`. If that returns a `CREA` id, place it with the existing `NpcMannequin.buildCreature` path at the **list ref’s** position, yaw, and `XSCL`. If the id is another `LEVC`, recurse with the same player level. Empty id → spawn nothing (chance-none or no legal entry). Missing id → log and skip, do not throw (OpenMW ignores nonexistent list members).
- **Player level is 1.** The viewer has no stats; that matches chargen. Lists named `+2` are different records, not a level offset. Entries with `mLevel > 1` stay out of the roll until we have a real level.
- Chance-none: `roll0to99` is `[0, 99]`. If `roll < NNAM`, return empty. `NNAM == 0` always tries a creature; `100` never does.
- Candidate set: every entry with `mLevel <= playerLevel`. If `AllLevels` is unset, keep only those at the **highest** such level. Pick one uniformly (`rollDice(n)` → `[0, n)`). Then recurse if that pick is itself a list.
- Creature-list `AllLevels` is **`0x01`**. Item lists use `0x02` for the same idea — do not reuse the item mask.
- Unseeded `Random` per cell build (OpenMW’s world PRNG is not cell-stable). Reload Town → different mix. Dump does not assert specific ids.
- Interiors too (Addamasartus vermin, Punsabanit’s leftover list). Same code path as Town.
- Debug CLI: `levc <id>` prints flags, chance-none, and `level id` rows. `exterior -2 -9` prints `crea=` (hardcoded) and `levc=` / `levcNone=` after a dry roll. Do not add another viewer.

```bat
gradlew.bat :core:debugCli --args="levc ex_bittercoast_lev+0"
gradlew.bat :core:debugCli --args="exterior -2 -9"
gradlew.bat lwjgl3:run
```

## Out of scope

- `LEVI` (leveled loot in crates)
- Player level-up, a HUD level slider, or using NPC level
- Respawn, corpse delay, save of which actor a marker last spawned
- Wander / fly / `idleswim` / combat
- Actor collision (still walk through people and creatures)
- `CreatureWeaponAnimation`, inventory
- Plugins, Lua, Bullet

## Test cell

**Town** (app default). Walk the Bitter Coast west of Census and look at the bay. Mudcrabs, rats/scribs, slaughterfish, and the odd cliff racer should appear at those markers — not three creatures in the whole 5×5.

**Nix** still has its five placed hounds; a sixth from the cave’s `LEVC` is fine.

**Cave** / **Zain** / **Cell** must not lose kit seams or spawn.

## Pass / fail

- Town shows many idle creatures, not three. Water markers can sit in the bay (no swim).
- A reload of Town can change which creature a given marker picked.
- Hardcoded `CREA` (Punsabanit hounds, any Town named creature) still place.
- Census NPCs still idle. Walk, fog, water, **Zain** halls, and Chair HUD unchanged. `glError=0`.

Still only the three hardcoded Town `CREA` is a **fail**. Spawning a STAT mesh for the list id is a **fail**. Resolving `LEVI` into extra loot is out of scope, not a fail. Empty rolls from chance-none or `mLevel > 1` are vanilla, not a fail.

## Other-LLM claims

One prompt (flag swap + pick). Skip parse/`insertObjectRendering` — those are mechanical.

## NAME_MAP rows to add when implementing

| OpenMW | Java | Status |
| --- | --- | --- |
| `ESM::CreatureLevList` | `esm.EsmLevc` | same |
| `MWMechanics::getLevelledItem` (creature) | roll helper | rewrite |
| `MWClass::CreatureLevList::insertObjectRendering` | `CellSceneBuilder` LEVC → `buildCreature` | rewrite |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: getLevelledItem for a creature list (creature=true) does:
1. If roll0to99(prng) < list.mChanceNone, return empty.
2. Find the highest mLevel among entries with mLevel <= playerLevel.
3. AllLevels: for creatures this is list.mFlags & CreatureLevList::AllLevels
   (0x01). ItemLevList::AllLevels is 0x02 and is the default mask before
   the creature branch overrides it. When AllLevels is set, every entry
   with mLevel <= playerLevel is a candidate; otherwise only entries at
   that highest level.
4. Pick one candidate with rollDice(size) in [0, size). If that id is
   another CreatureLevList (or ItemLevList), recurse with the same
   player level. Missing store ids log a warning and return empty.

Player level comes from the player actor unless an explicit level
argument is passed. roll0to99 is rollDice(100) → [0, 99].

From apps/openmw/mwmechanics/levelledlist.cpp getLevelledItem:
    if (Misc::Rng::roll0to99(prng) < levItem->mChanceNone)
        return {};
    highestLevel from entries with mLevel <= playerLevel
    bool allLevels = (levItem->mFlags & ESM::ItemLevList::AllLevels) != 0;
    if (creature)
        allLevels = levItem->mFlags & ESM::CreatureLevList::AllLevels;
    candidates: playerLevel >= mLevel && (allLevels || mLevel == highestLevel)
    pick rollDice(candidates.size())
    if pick is ItemLevList / CreatureLevList, recurse

From components/esm3/loadlevlist.hpp:
    CreatureLevList::AllLevels = 0x01
    ItemLevList::AllLevels = 0x02
    CreatureLevList sRecName = "CNAM"

From components/misc/rng.hpp:
    rollDice: [0, max)
    roll0to99: rollDice(100)

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

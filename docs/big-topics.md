# Big topics

Large holes. **Do not spec these as a small phase.** Small slices live in [next-topics.md](next-topics.md).

OpenMW 0.51.0 still does not do general scene occlusion; its “occlusion” is sun glare and rain. Rows here are missing from the viewer, not a promise they exist in the pin.

## Look

| Topic | Why |
| --- | --- |
| **Occlusion** | Frustum + small-feature only. Docks behind a hill still draw. Not OpenMW’s default; a real occluder pass is its own project. |
| **Shadows** | No shadow maps. Town is flat-lit. |
| **Distant land / object paging** | Fog + 5×5 grid only. No far terrain or batched distant STAT. |
| **Grass / groundcover** | No `grass` / groundcover paging. |
| **GPU skinning** | CPU reskin per actor. `ModelBatch` stays forbidden. |
| **Postprocessing** | No OpenMW shader chain (AA, AO, etc.). |
| **Full weather** | Clear hour slider only. Climate, rain meshes, thunder, and weather *types* as a set. |
| **Water extras** | No rain ripples, raindrop particles, or shader-off simple water fallback as a mode. |
| **1st person / 3rd person body** | Fly-cam only. No player mesh or arms. |

## World

| Topic | Why |
| --- | --- |
| **Combat** | No hit, health, or weapon groups. |
| **Dialogue / journal** | NPCs are mannequins. No talk, topics, or journal. |
| **AI packages** | Follow, travel, duration, idle2–9. Cheap XY wander is [phase 38](phase38-cheap-wander.md); walkforward is [phase 39](phase39-walk-cycle.md). |
| **Navmesh** | No pathfinding. Pathgrid (`PGRD`) lives here too. |
| **Saves** | No `.ess` / OpenMW save. |
| **Plugins / Lua / MWScript** | Extra data folders only. No ESP load-order UI, no Lua, no script VM. |
| **Full physics** | Stay-on-land is a small slice. No actor collision, projectiles, or Havok-like sim. |
| **Swimming** | Underwater fog exists. No swim move, drown, or water current. |
| **Crime / factions / rest** | No bounty, jail, bed rest, or wait. |
| **Magic / alchemy / enchant** | No spells, effects, or ingredient use. |
| **Leveling / stats** | No attributes, skills, or level-up. |
| **Fast travel** | No world map travel. |

## UI and audio

| Topic | Why |
| --- | --- |
| **Full Morrowind UI** | Scene2D HUD is debug + load buttons. No HUD bars, menus, or MyGUI port. |
| **Map** | No local or world map. |
| **Loot / container GUI** | Chests play a lid. No take-all pane. |
| **Inventory / magic / stats sheets** | Take unparents a mesh. No paper doll. |
| **Sound / music** | libGDX audio is available; no region music, FX, or voice. |

When a topic is split into a small slice and that slice ships, add the small row to [next-topics.md](next-topics.md) and leave the rest here.

# Big topics

Large holes. **Do not spec these as a small phase.** Small slices live in [next-topics.md](next-topics.md). A breakdown of a hole (work order, not a phase) may live as a linked doc; split **one** row into next-topics before writing Java.

OpenMW 0.51.0 still does not do general scene occlusion; its “occlusion” is sun glare and rain. Rows here are missing from the viewer, not a promise they exist in the pin.

## Look

| Topic | Why |
| --- | --- |
| **Occlusion** | Frustum + small-feature only. Docks behind a hill still draw. Not OpenMW’s default; a real occluder pass is its own project. |
| **Shadows** | No shadow maps. Town is flat-lit. |
| **Distant land / object paging** | Fog + 5×5 grid only. Work order: [distant-land.md](distant-land.md). |
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
| **AI packages** | A front wander ends after its duration and can play idle2–idle9 while standing ([phase 54](phase54-wander-duration.md)). A front travel walks to its point when it is within 7168 ([phase 55](phase55-travel.md)). Follow and escort are not run. Work order: [ai-packages.md](ai-packages.md). |
| **Navmesh** | Overlay is [phase 42](phase42-navmesh-bake.md). Straight wander dests use Detour ([phase 43](phase43-navmesh-path.md)). Leftover of [nav-paths.md](nav-paths.md) **6**. |
| **Saves** | No `.ess` / OpenMW save. |
| **Plugins / Lua / MWScript** | Extra data folders only. No ESP load-order UI, no Lua, no script VM. |
| **Full physics** | Player WASD, NPC floors, and live doors are Bullet. Leftover: actor capsules. Recast/Detour stay. Work order: [physics.md](physics.md). |
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

When a topic is split into a small slice and that slice ships, check it off on the breakdown if there is one, add the small row to [next-topics.md](next-topics.md) only if more of that slice remains, and leave the rest here.

# Next topics

Pick one, then say **proceed** to spec that slice. This is the queue.

Do **not** spec combat, dialogue, AI packages, navmesh, plugins, Lua, saves, or a full GUI as a small phase.

## Strong next slices

These match what the viewer already is (walk Town, look, Dump) and were explicitly left out of recent phases.

| Topic | Why |
| --- | --- |
| **Keep overlapping tiles** | Walk still rebuilds land and the scene graph for cells you already have. Textures/static NIFs intern; live tiles do not. Biggest remaining swap hitch. |
| **Moons** | Night is stars only. Masser and Secunda. |
| **Softer ground blends** | Land mix is still blocky 2× nearest. Noticeable on Town dirt/grass. |
| **Collision / stay on land** | Fly-cam only. Heightfield + simple object bounds so walking the harbor does not clip through docks. Not full physics. |
| **NPC / creature walk cycles** | Mannequins idle in place. Walk/idle groups on the same skeletons when you move or when they have a wander. |
| **Sunglare** | Midday sun disc is there; OpenMW glare/flash is not. |
| **One extra weather** | Clear only. Overcast or rain on the existing hour slider would test weather without a full climate system. |

## Interact

| Topic | Why |
| --- | --- |
| **Books** | **E** on a book logs only. A simple read pane (no inventory). |
| **Inventory for taken items** | Take unparents the mesh. A list of what you picked up. |
| **Sacks / baskets open** | Chests with extra-data kf already lid; sacks do not. |
| **More activate** | Beds, levers, and other `E` types beyond doors, chests, and take. |

## Speed (after overlapping tiles)

| Topic | Why |
| --- | --- |
| **Merge land draws** | Many blend layers per tile. Fewer terrain submits. |
| **Occlusion / small-feature cull** | Frustum only. Docks behind a hill still draw. |
| **Skinned bound updates** | NPC AABB is rest-pose. Idle motion can pop in/out of cull. |

## Later / not a small phase

Weather *types* as a set, loot GUI, shadows, distant land, grass, map, sound/music, GPU skinning, full Morrowind UI.

When a topic ships, delete its row here.

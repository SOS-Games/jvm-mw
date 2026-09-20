# Next topics

Pick one, then say **proceed** to spec that slice. This is the **small** queue.

Big holes (occlusion, combat, shadows, full UI, …) live in [big-topics.md](big-topics.md). Do not spec those here.

## Strong next slices

These match what the viewer already is (walk Town, look, Dump) and were explicitly left out of recent phases.

| Topic | Why |
| --- | --- |
| **Keep overlapping tiles** | Walk still rebuilds land and the scene graph for cells you already have. Textures/static NIFs intern; live tiles do not. Biggest remaining swap hitch. |
| **Moons** | Night is stars only. Masser and Secunda. |
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
| **Skinned bound updates** | NPC AABB is rest-pose. Idle motion can pop in/out of cull. |

When a topic ships, delete its row here.

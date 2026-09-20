# Delayed features

Small polish we skipped so a phase could ship. **Not** the next-phase queue. Big holes (weather types, loot GUI, sunglare) stay out of this file.

Add a row when a slice deliberately leaves a minor visual. Do not start these until the user asks.

## Open/close on sacks

Phase 13 plays `containeropen` / `containerclose` when that kf group exists. Vanilla `Morrowind.bsa` has no container `x*.kf`, so Census chests stay still. The extra data folder (`Containers Animated`) supplies chest lids; **sacks and baskets still do not open**. Same **E** path, same groups — they just never bind a clip.

## Extra blending on ground textures

Phase 20 mix is TES3 blendmaps upscaled **2× nearest** (OpenMW `getBlendmaps`). Dirt/sand/grass squares no longer have a hard 4×4 edge, but the blend is still blocky. Further softening (more upsample, or a softer filter than that 2×2) was left out.

## Moons

Phase 26/27 draw atmosphere, clouds, sun, and stars. OpenMW also instances Masser and Secunda (`SkyManager` moon nodes / `setMasserState` / `setSecundaState`). Night is starfield-only until someone asks for the moons.

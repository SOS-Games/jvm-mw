# Phase 14: Extra data folders (VFS)

OpenMW pin: **openmw-0.51.0** (`f4bec41444214a7903bebd178389ca22ca13f646`).

Align with `components/vfs/registerarchives.cpp` (`registerArchives`), `components/vfs/manager.cpp` (`buildIndex` / `exists`), `components/vfs/filesystemarchive.cpp` (`FileSystemArchive`), `components/vfs/bsaarchive.hpp` (`BsaArchive::listResources`), `components/misc/resourcehelpers.cpp` (`correctActorModelPath`). Other-LLM checks of those three claims **held** (2026-09-19).

## Goal

Same Path B viewer. Phase 13 container **E** stays. HUD **Cell** / **Cave** / **Nix** / **Guild** unchanged.

**Loose files from extra data folders join the VFS after `Morrowind.bsa`, last folder wins.** Census `stolen_goods` then finds `meshes/o/xcontain_com_chest_02.kf` from OpenMW Containers Animated, so **E** lifts the lid.

## Why this slice

Phase 13’s `onOpen` path is done. Vanilla `Morrowind.bsa` has no container `x*.kf`. The lid clips live in a mod folder your OpenMW list already loads (`FurnitureandClutter\OpenMWContainersAnimated\Containers Animated`). OpenMW does not special-case that mod: `registerArchives` adds BSAs, then each `data=` directory as a `FileSystemArchive`, then `buildIndex` so a later archive overwrites the same VFS path. `correctActorModelPath` already looks for `x*.kf`; it only needs `vfsExists` to see those loose files.

This is **not** the whole modlist. Do not parse `openmw.cfg`. Do not load Tamriel Rebuilt, texture packs, or plugins.

## In scope

Still **one** ESM: `Morrowind.esm`. Still **one** BSA: `Morrowind.bsa`. Extra folders are optional filesystem archives.

- Keep `jvmmw.data` (or `JVMMW_DATA` / `-Djvmmw.data`) as the Morrowind `Data Files` root (ESM + BSA).
- Add `jvmmw.data.extra` in gitignored `local.properties` (and `JVMMW_DATA_EXTRA` / `-Djvmmw.data.extra`): one or more directories, `;` separated. Missing / empty = Phase 13 behavior.
- VFS order, matching `registerArchives` + `buildIndex`: `Morrowind.bsa`, then loose files under `jvmmw.data`, then each extra dir in listed order. **Last archive wins** the same normalized path (`meshes/o/foo.nif`).
- Extra dirs: recursive loose files; VFS key is the path under that folder (`Meshes\o\xcontain_com_chest_02.kf` → `meshes/o/xcontain_com_chest_02.kf`). Skip duplicate extra paths.
- `TestData.vfsExists` / `ensureNif` use that index. Do not let a stale `testdata/` BSA extract hide an extra-dir file of the same path. Extra-dir files may be read in place (do not copy them into git).
- Phase 13 `correctActorModelPath` / `ContainerOpen` unchanged aside from VFS seeing the `x*.kf`. Place the x-nif when that kf exists.
- Debug CLI `cell` `kf=` and `kf <path>` must resolve extra-dir files.

Test extra dir (read-only; do not edit):

`D:\morrowind_mods\morrowind-starter-pack\FurnitureandClutter\OpenMWContainersAnimated\Containers Animated`

```properties
jvmmw.data.extra=D:\\morrowind_mods\\morrowind-starter-pack\\FurnitureandClutter\\OpenMWContainersAnimated\\Containers Animated
```

```bat
gradlew.bat :core:debugCli --args="cell Seyda Neen, Census and Excise Office"
gradlew.bat :core:debugCli --args="kf meshes/o/xcontain_com_chest_02.kf"
```

Census `stolen_goods` should print `kf=meshes/o/xcontain_com_chest_02.kf`. The kf dump should include group `containeropen`.

## Out of scope

- Parsing `openmw.cfg` / the rest of the starter pack
- Plugins (`.esp` / `.esm` besides `Morrowind.esm`), including `Containers Animated.esp` (sounds only)
- Tribunal / Bloodmoon BSA
- `containerclose`, loot GUI, container sounds
- Lua, GPU skinning / `ModelBatch`, terrain

## Test cell

**Cell** (Census) with `jvmmw.data.extra` set as above: look at `stolen_goods` / `Contain_Com_Chest_02`. **E** — lid lifts (not a 90° Z swing). Second **E** plays `containerclose` (loot-window dismiss). Hide doors still swing. Guild load doors still teleport.

Unset extra: same chest logs `no containeropen` (Phase 13).

```bat
gradlew.bat :core:debugCli --args="kf meshes/o/xcontain_com_chest_02.kf"
gradlew.bat lwjgl3:run
```

F3 still writes `build/debug-snapshot.txt`.

## Pass / fail

- With extra dir: Census chest lid opens on **E**. `cell` shows `kf=meshes/o/xcontain_com_chest_02.kf` for `stolen_goods`.
- Second **E** plays `containerclose` (does not restart open). While closing, **E** is ignored.
- Without extra dir: chest still does not swing or teleport.
- Hide doors still swing. Guild interior doors still load. Nix-hounds still idle.
- Chair HUD unfogged. `glError=0`.

Loading Tamriel Rebuilt or Seyda Neen terrain is a **fail**. Committing files from `D:\morrowind_mods` or `testdata/` is a **fail**. Chest that stays shut **with** the extra dir and a `containeropen` group is a **fail**.

## Other-LLM claims (held 2026-09-19)

Pin: `openmw-0.51.0` (`f4bec41444214a7903bebd178389ca22ca13f646`).

1. **Register order.** `registerArchives` adds each named BSA from `Files::Collections` in list order (last BSA highest priority). If `useLooseFiles`, each unique data directory is a `FileSystemArchive` (last data dir highest priority; duplicates skipped). Then `buildIndex()`.
2. **Index overwrite.** `buildIndex` clears `mIndex`, then each archive `listResources` in add order. Both `BsaArchive` and `FileSystemArchive` assign `out[key] = file`, so a later archive replaces the same path. `FileSystemArchive` keys are paths relative to that data dir (prefix stripped), then normalized.
3. **x-mesh.** `correctActorModelPath` inserts `x` after the last slash. If that name ends `.nif`, it looks for the `.kf`. Missing kf → original path. Found kf → the x-prefixed nif path.

## NAME_MAP rows

| OpenMW | Java | Status |
| --- | --- | --- |
| `VFS::registerArchives` | extra dirs after `Morrowind.bsa` | rewrite |
| `VFS::Manager::buildIndex` | last archive wins path | rewrite |
| `VFS::FileSystemArchive` | loose files under a data folder | rewrite |
| `Misc::ResourceHelpers::correctActorModelPath` | `TexturePaths.correctActorModelPath` (VFS now sees extra) | same |

## Prompts for another model

Do **not** web-search. Use only the pin and the excerpts. Answer **holds** / **fails** and quote the function names.

### Prompt 1

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: registerArchives adds each named BSA from Files::Collections
in list order when collections.doesExist is true; a comment says
the last BSA has the highest priority. If useLooseFiles is true, it
then adds each unique data directory as a FileSystemArchive; a
comment says the last data dir has the highest priority, and
duplicate data dirs are ignored. After those adds it calls
buildIndex().

From components/vfs/registerarchives.cpp registerArchives:
    for (archives) {
        if (collections.doesExist(*archive)) {
            // Last BSA has the highest priority
            vfs->addArchive(makeBsaArchive(archivePath, encoder));
        } else {
            throw runtime_error("Archive '" + *archive + "' not found");
        }
    }
    if (useLooseFiles) {
        set seen;
        for (const auto& dataDir : dataDirs) {
            if (seen.insert(dataDir).second) {
                // Last data dir has the highest priority
                vfs->addArchive(make_unique<FileSystemArchive>(dataDir));
            } else
                Log << "Ignoring duplicate data directory " << dataDir;
        }
    }
    vfs->buildIndex();

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 2

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: Manager::buildIndex clears mIndex, then for each archive in
add order calls listResources(mIndex). BsaArchive::listResources
assigns out[Normalized(path)] = file. FileSystemArchive::listResources
assigns out[k] = &v. A later archive therefore replaces the same
VFS path. FileSystemArchive builds keys by taking the file path
under the data directory (strip a prefix equal to the dir path,
plus a separator if the dir path had no trailing slash).

From components/vfs/manager.cpp:
    void Manager::buildIndex() {
        mIndex.clear();
        for (const auto& archive : mArchives)
            archive->listResources(mIndex);
    }

From components/vfs/bsaarchive.hpp BsaArchive::listResources:
    for (auto& resource : mResources) {
        path = getUtf8(resource.mInfo->name(), buffer);
        out[VFS::Path::Normalized(path)] = &resource;
    }

From components/vfs/filesystemarchive.cpp FileSystemArchive ctor:
    prefix = mPath.u8string().size();
    if (prefix > 0 && last char is not '\\' or '/')
        ++prefix;
    for each non-directory entry:
        searchable = Normalized(filePath string.substr(prefix));
        mIndex.emplace(searchable, file);

From FileSystemArchive::listResources:
    for (auto& [k, v] : mIndex)
        out[k] = &v;

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

### Prompt 3

```
OpenMW pin openmw-0.51.0 (f4bec41444214a7903bebd178389ca22ca13f646).
Do not use web search. Use only this excerpt.

Claim: correctActorModelPath copies the mesh path and inserts 'x'
immediately after the last slash (or at the start if there is no
slash). It then builds a kf name from that x-path: if the extension
is .nif, change it to .kf. If vfs->exists(kfname) is false, it
returns the original resPath. If true, it returns the x-prefixed
nif path (mdlname), not the kf path.

From components/misc/resourcehelpers.cpp correctActorModelPath:
    mdlname = resPath;
    p = mdlname.find_last_of('/');
    if (p != npos) mdlname.insert(p + 1, 'x');
    else mdlname.insert(begin, 'x');
    kfname = mdlname;
    if (kfname.extension() == nif)
        kfname.changeExtension(kf);
    if (!vfs->exists(kfname))
        return Normalized(resPath);
    return Normalized(mdlname);

Does the claim hold? If it fails, say exactly which sentence is wrong.
```

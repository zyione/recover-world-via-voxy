# Recover World via Voxy

A Windows desktop tool that reconstructs **Minecraft Java 1.21.11 Overworld blocks** from a Voxy cache. Select a cache folder, choose an area, and export a separate Minecraft save.

**Beta software.** A recovered sample has been opened successfully in Minecraft by the project owner. Automated checks verify saved block states, biomes, heightmaps and block-entity serialization. Large-area performance and every possible in-game behavior are not yet validated.

## Requirements

- Windows 10/11 and a **Java 21 JDK**, available through `JAVA_HOME` or `PATH`.
- Internet access for the first launch, which downloads Gradle, Minecraft development dependencies and libraries.
- A Voxy cache using the tested **Voxy 0.2.13-alpha / SaveLoadSystem3** format. Other versions are not guaranteed compatible.
- Free disk space for a full verified copy of the selected cache plus the recovered world.
- Minecraft Java **1.21.11** to open the result. No backport is performed.

This distribution builds locally. It is not a standalone EXE and does not bundle Minecraft, Java, private caches or recovered worlds.

## Quick start

1. Download this repository using **Code → Download ZIP**, then extract the ZIP. Alternatively, clone it with Git.
2. Install a Java 21 JDK if needed, then double-click **Launch Converter.bat** in the extracted folder. Keep the launcher open while working.
3. **Close Minecraft and any program writing to the cache.** Click **Browse and scan Voxy folder…** and select your `.voxy` folder. The `saves` folder inside it or an individual database `storage` folder also works.
4. Wait while the application copies and checks the files. It never opens the original database; only the verified copy is opened, read-only.
5. Select the correct **Overworld candidate** from the database list. Folder names can be hashes; dimension labels are inferred from saved biome names, not guaranteed identities. If several candidates exist, try a small known area in each.
6. Enter minimum and maximum X, Y and Z, and a **new output folder**. Bounds are inclusive. Use **Use small test area** to try the project's original sample coordinates, or enter a small area you know is present in your own cache.
7. Click **Recover selected area** and wait for **Done**. Do not close the launcher or shut down the computer during conversion; resume is not implemented.
8. Use **Open output folder**. Keep this folder as your master and copy it into `%APPDATA%\.minecraft\saves` (or the `saves` folder of your launcher instance). Open the copy in Minecraft **1.21.11**.

New worlds start in spectator mode. Look at `recovery-report.json` for missing data and incomplete block entities. **Never open an output containing `INCOMPLETE.txt`.** A failed run also produces `failure.txt`; choose a new output folder when retrying.

### Where is my Voxy folder?

Look for `.voxy` in the Minecraft instance's game directory. With separate launcher instances, check that instance rather than only the default `%APPDATA%\.minecraft` directory. A supplied backup may already contain `.voxy`. The application expects RocksDB databases with a `CURRENT` file, often below `.voxy/saves/<server>/<world>/storage`.

There is no hard-coded server address, world hash or personal path in the application. It scans the folder you choose. Close Minecraft first so the copy represents a stable cache.

### Default recovery area

| Axis | Minimum | Maximum |
|---|---:|---:|
| X | -2217 | 1783 |
| Y | 56 | 319 |
| Z | -3140 | 860 |

These are the original project's requested borders, not universal coordinates. Change them for your world. This selection covers **63,001 chunk columns**. The desktop beta permits at most 100,000 columns per run, and Y must stay within -64 through 319. No reliable full-area runtime estimate is available yet.

## What can be recovered?

| Data | Result |
|---|---|
| Stored level-0 block states | Preserves block names and properties, with native save/read-back comparison |
| Biomes | Uses surviving unambiguous biome evidence; otherwise explicitly reports synthetic plains cells |
| Missing sections | Empty space, counted separately in the report |
| Blocks outside the selected borders | Empty space, including below the minimum Y |
| Containers and other block entities | New empty shells where supported; no original contents or text |
| Moving pistons | Stored state retained and coordinates flagged; moved blocks/progress cannot be inferred |
| Entities, inventories, advancements, statistics, maps and scheduled ticks | Not recovered from this cache |
| Original world seed | Not recovered; the new world uses placeholder seed **0** and a void generator |

Missing player files and advancements do not prevent a new world from being created. The converter supplies new `level.dat` metadata and region files; this does not restore the original player's progress.

Cached air cannot reliably be distinguished from never-observed zero voxels. Level-0 storage also lacks provenance guarantees. This tool does not expand coarse LOD records into invented detail. Lighting is marked for recalculation. Normal game simulation can change unsupported blocks, fluids or machinery, particularly at crop boundaries; preserve the master and play on a copy.

## Local files and privacy

- Working copies and generated job settings are stored under `%USERPROFILE%\VoxyRecovery\jobs`.
- Suggested output folders are under `%USERPROFILE%\VoxyRecovery\worlds`.
- Each scan creates a fresh working copy; copies are not automatically deleted. Remove old job folders yourself only after their conversions finish and you no longer need them.
- The application does not upload your cache or recovered world. The first build downloads dependencies.
- Reports and job settings contain local paths; inspect them before sharing.
- Git ignores source caches, recovered worlds, research downloads, private configs and build outputs.

## Build and test

From the `converter` directory with a Java 21 JDK configured:

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat desktop --console=plain
.\gradlew.bat sourceRelease --console=plain
```

The source release ZIP appears in `converter/build/distributions`. It uses the same launcher and first-run build as a repository checkout. Minecraft/game JARs are deliberately excluded.

Public tests generate a synthetic RocksDB cache and exercise source selection, copying, integrity checks, conversion, native save/read-back, overwrite protection and tamper rejection. Additional forensic tests are skipped when the author's private fixtures are absent. CI runs the public workflow on Windows.

For automated conversions, the desktop's prepared job JSON can be passed to:

```powershell
.\gradlew.bat recover '-PrecoveryArgs=C:/path/to/job.json' --console=plain
```

Choose a fresh `output` path before reusing a job. Advanced jobs reference the original folder, verified snapshot, SHA-256 manifest, selected database and coordinate bounds. `config/defaults.json` controls only the desktop's initial bounds.

## Limitations and planned work

No cancellation/resume, higher-LOD approximation, original-seed recovery or standalone installer yet. The writer reopens each region for verification, prioritizing validation over full-area throughput. The converter currently supports the Overworld and Minecraft 1.21.11 only. Corrupt sections or unsupported block mappings stop the export rather than silently substituting blocks.

See [CHANGELOG.md](CHANGELOG.md) and [THIRD_PARTY.md](THIRD_PARTY.md). This is an independent project, not an official Minecraft or Voxy product.

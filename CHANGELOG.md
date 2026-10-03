# Changelog

## 0.1.0-beta.1

- First public source distribution for Windows and Java 21.
- Select Voxy cache folders and discover databases without hard-coded world IDs.
- Copy and hash-verify inputs before opening databases read-only.
- Configurable inclusive XYZ bounds; Minecraft Java 1.21.11 native output.
- Reopen region files and compare block states, biomes, heightmaps and block entities.
- Report missing data, synthetic biomes, empty block entities and incomplete moving pistons.
- Synthetic public integration tests, Windows CI and source release packaging.

Known limitations: no resume or standalone installer; no original seed, inventory, entity or advancement recovery. Only the Overworld is supported. Keep recovered masters separate from gameplay copies.

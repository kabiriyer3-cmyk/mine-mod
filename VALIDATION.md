# Validation, 0.4.0-rc1

## Done in the authoring sandbox (no internet, no Gradle, no Minecraft)

- `GameRocketTest`: 9 standalone tests pass with a JUnit-compatible runner, including the new buried-start test (round leaves the 7-block shaft vertically, 500 blocks reached in 54 ticks, peak about 26 blocks).
- All Java sources compile (javac 17 syntax/type check) against **private API doubles** written from Yarn 1.21.1 / Fabric API 1.21.1 signatures. This catches typos and type errors only, not real Minecraft API mismatches.
- The three TMSH v2 meshes are read successfully by the shipped Java `TMesh.read` (silo 10 parts / 4300 tris, missile 9 / 4596, designator 1 / 11074).
- `tools/validate_assets.py`: JSON/mcmeta parse, matching en/ru keys, mesh structure and bounds, texture LOD sizes and blur metadata, mono 44.1 kHz Ogg sounds, no vanilla particle/explosion/sound calls in the source, CC BY credits present.
- A software renderer using vanilla entity lighting produced the preview in `docs/silo-preview.jpg` from the same converted meshes and textures.

## Not verified

- Real Gradle/Loom build (happens in GitHub Actions).
- Client and dedicated-server runtime, networking, saves.
- In-game look: lighting, texture filtering, item display transforms of the 3D designator, VFX density and sorting, sound balance.
- Compatibility with Sodium/Iris/shader packs.

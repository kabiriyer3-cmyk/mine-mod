# Third-party notices

## 3D models (CC BY 4.0, modified)

The meshes in `src/main/resources/assets/topol/models/tmsh/` and the textures `textures/entity/tomahawk_*`, `textures/entity/designator_*` are derived from the following works. Changes: converted from glTF to the mod's TMSH format, split into animated parts, rescaled, reoriented, recoloured (Tomahawk repainted service grey, VLS cell given a military palette and new tiled materials), ambient occlusion baked into vertex colours, normal/occlusion maps baked into albedo, textures resized. The silo is a single cell cut from the VLS model and combined with an original concrete pad.

- This work is based on "BGM-109 Tomahawk missile" (https://sketchfab.com/3d-models/bgm-109-tomahawk-missile-da71eb1c023b444aba15ff37710e9613) by RickSlash (https://sketchfab.com/RickSlash) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/)
- This work is based on "Vertical Launch System Missile Canisters" (https://sketchfab.com/3d-models/vertical-launch-system-missile-canisters-b5d0cba8bc2f4cdfab0da7c355c3a742) by DenisGLabrecque (https://sketchfab.com/DenisGLabrecque) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/)
- This work is based on "Zenitco Perst 4 laser pointer" (https://sketchfab.com/3d-models/zenitco-perst-4-laser-pointer-6e5f5c85bb424bd5acb9421e6a3fecad) by ikoval31 (https://sketchfab.com/ilya07082016) licensed under CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/)

The licensors do not endorse this mod. Trademarks or markings visible on the source textures (for example manufacturer names) belong to their owners and are part of the original artworks; their presence implies no affiliation. If you redistribute the JAR or these assets, keep this file (or the same credit lines) with them.

The raw Sketchfab downloads are not included in this ZIP. To rebuild the derived assets, download the three models (glTF) into `tools/models/src/{tomahawk,vls,perst4}/` and run the generators listed in `README.md`.

## Original project assets

The concrete pad geometry and decal, painted steel / grip / concrete textures, all FX sprite sheets (smoke, fire, glow, plume, sparks, debris, scorch, laser), item icons (rendered from the meshes above) and all synthesized sounds were created for this project. No extracted Minecraft textures, sounds or models, no recordings of real weapons and no decompiled Minecraft source are distributed.

Project code and original assets are provided under the MIT license in `LICENSE`. The derived model assets above remain under CC BY 4.0.

## Fonts used to rasterize labels

The asset generator uses DejaVu Sans to rasterize the pad stencil text into an original texture. Font binaries are not bundled. See `docs/DEJAVU_LICENSE.txt`.

## Build and runtime dependencies

Minecraft is a separately obtained game and is not bundled. Fabric Loader, Fabric API, Yarn, Loom, Gradle, Java, JUnit, Gson, JOML and Mixin retain their respective licenses; their binaries are not embedded in this source ZIP. Optional artwork tools (NumPy, Pillow, SciPy, FFmpeg with libvorbis) are not bundled and are not needed for the Gradle build.

This is an unofficial Minecraft mod, not affiliated with Mojang or Microsoft.

# LuminGraphics-MC 1.0.0

LuminGraphics-MC bridges supported Minecraft client rendering paths to
LuminGraphics for Minecraft 26.1.2 and 26.2 on Fabric and NeoForge. Start with
the [documentation index](docs/README.md) and treat
[the bridge matrix](docs/bridge-matrix.csv) as the capability source of truth.

The final mod artifacts are local-Maven-only loader JARs. Each is a direct
shadow JAR: its LuminGraphics and required PrismRHI classes and resources are
merged into the loader artifact rather than nested as dependency JARs.

The loader artifacts are:

- `lumin-graphics-mc-fabric-26.1.2`
- `lumin-graphics-mc-neoforge-26.1.2`
- `lumin-graphics-mc-fabric-26.2`
- `lumin-graphics-mc-neoforge-26.2`

Use the loader/version match, not either common JAR, as a consumer mod. There
is no public registry workflow. OpenGL matrix rows support the stated borrowed,
rebuilt, or adapter modes; Vulkan zero-copy is unsupported.

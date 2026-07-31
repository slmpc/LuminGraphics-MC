# Minecraft 26.1.2 Common Notes

## Scope

This module implements the 26.1.2 bridge in
`com.github.slmpc.lumingraphics.mc.v2612` for both loaders.

## Entrypoints

| Area | Location |
| --- | --- |
| Bridge adapters | `src/main/java/.../v2612/bridge/Blaze3DBridge2612.java` |
| Access contracts | `src/main/java/.../v2612/access/` |
| Mixin access | `src/main/java/.../v2612/mixin/GlAccess2612.java` |
| Borrowed wrappers | `src/main/java/.../v2612/mixin/*BorrowedMixin.java` |
| Smoke writer | `src/main/java/.../v2612/smoke/RealClientBridgeSmoke2612.java` |
| Text adapter | `src/main/java/.../v2612/text/` |

## Tests And Assets

Module tests live in `src/test/java`. Loader resources supply the real access
metadata: `../fabric/src/main/resources/lumin_graphics_mc_2612.accesswidener` and
`../neoforge/src/main/resources/META-INF/accesstransformer.cfg`.

Run `..\\..\\gradlew.bat :mc-26.1.2:common:test` for targeted common validation.

## Pitfalls

- Do not substitute 26.2 names or constructor signatures into this module.
- Accessors/invokers must match the corresponding Fabric AW and NeoForge AT.
- A borrowed Minecraft GL resource invalidates its lease but must never be
  destroyed by Lumin cleanup.
- Smoke receipts include JSON on positive/negative paths and an 8x8 PNG only
  for positive paths; preserve that contract.

## Integration

Loader modules package this common implementation. Its public bridge behavior
must agree with the `MC26.1.2` rows in `../../docs/bridge-matrix.csv`.

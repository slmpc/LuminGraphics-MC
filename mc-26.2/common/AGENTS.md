# Minecraft 26.2 Common Notes

## Scope

This module implements the 26.2 bridge in
`com.github.slmpc.lumingraphics.mc.v262`, including its changed access targets.

## Entrypoints

| Area | Location |
| --- | --- |
| Bridge adapters | `src/main/java/.../v262/bridge/` |
| Access contracts | `src/main/java/.../v262/access/` |
| Mixin access | `src/main/java/.../v262/mixin/GlAccess262.java` |
| Config filtering | `src/main/java/.../v262/mixin/SmokeMixinConfigPlugin262.java` |
| Borrowed wrappers | `src/main/java/.../v262/mixin/*BorrowedMixin262.java` |
| Verification | `src/main/java/.../v262/verify/` |

## Tests And Assets

Tests are under `src/test/java`. The versioned target inventory is
`src/main/resources/lumin-graphics-mc-262.access-targets.properties`; loader
metadata is in `../fabric/src/main/resources/lumin_graphics_mc_262.accesswidener`
and `../neoforge/src/main/resources/META-INF/accesstransformer.cfg`.

Run `..\\..\\gradlew.bat :mc-26.2:common:test` for targeted common validation.

## Pitfalls

- Do not copy 26.1.2 access descriptors: 26.2 constructor and target details
  are intentionally version-specific.
- Keep `SmokeMixinConfigPlugin262` aligned with any optional DSA path changes.
- Map every new private Minecraft touchpoint through the versioned target
  inventory and the matching loader access mechanism.
- Respect exact context/thread/ownership rules in the `26.2` matrix rows;
  `unknown` and Vulkan rows remain unsupported.

## Integration

Fabric and NeoForge package this module separately. Update the `26.2` rows in
`../../docs/bridge-matrix.csv` when bridge mode or provenance changes.

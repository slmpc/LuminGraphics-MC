# LuminGraphics-MC Consumer And Bridge Guide

## Consumer Setup And Packaging

Build and consume from a local Maven repository only. Select exactly one final
loader artifact matching both version and loader:
`lumin-graphics-mc-fabric-26.1.2`,
`lumin-graphics-mc-neoforge-26.1.2`,
`lumin-graphics-mc-fabric-26.2`, or
`lumin-graphics-mc-neoforge-26.2`. The `bridge-contract` and versioned common
JARs are implementation dependencies, not final mods.

## Bridge Contract And Matrix

`bridge-contract` owns `BridgeMatrix`, `BridgeLease`, `BridgeMode`, and
`BridgeOwnership`. The [matrix](bridge-matrix.csv) has 168 rows across version,
loader, backend, object, and direction. OpenGL supports borrowed zero-copy
texture/buffer/shader wrappers, rebuilt sampler/pipeline values, and in-place
encoder/render-pass adapters. Vulkan rows are unsupported because zero-copy is
unsafe; `unknown` backend rows are also unsupported.

Borrowed leases observe exact context/owner-render-thread/current-GL checks and
become unusable after their owner token invalidates. Closing a borrowed lease
must not destroy Minecraft's native resource; rebuilt values have owned close
behavior stated in their row.

## Access And Versions

Fabric uses the versioned access wideners:
`mc-26.1.2-fabric/src/main/resources/lumin_graphics_mc_2612.accesswidener` and
`mc-26.2-fabric/src/main/resources/lumin_graphics_mc_262.accesswidener`.
NeoForge uses the matching `META-INF/accesstransformer.cfg` files. Mixins are
versioned in each common module; 26.2 additionally inventories targets in
`lumin-graphics-mc-262.access-targets.properties`. Do not use reflection.

## Smoke Receipts

`runAllBridgeSmokes` and `runAllBridgeNegativeSmokes` produce four positive and
four negative JSON receipts; each positive variant also emits an 8x8 PNG with
a pixel hash. Override the configured output directory with
`LUMIN_MC_SMOKE_EVIDENCE_DIR` when required. The default points to a sibling
LuminGraphics migration attempt and is not a consumer install location.

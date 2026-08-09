# LuminGraphics-MC Consumer And Bridge Guide

## Consumer Setup And Packaging

Build and consume from a local Maven repository only. Select exactly one final
loader artifact matching both version and loader:
`lumin-graphics-mc-fabric-26.1.2`,
`lumin-graphics-mc-neoforge-26.1.2`,
`lumin-graphics-mc-fabric-26.2`, or
`lumin-graphics-mc-neoforge-26.2`. The `bridge-contract` and versioned common
JARs are never embedded as separate consumer dependencies; their required
classes are directly merged into the matching final loader artifact.

Every final loader artifact directly shadows the bridge-contract, LuminGraphics,
and required PrismRHI payload. It contains their classes and shaders directly,
with no Fabric JIJ or NeoForge jarJar dependency archives.

## Bridge Contract And Matrix

`bridge-contract` owns `BridgeMatrix`, `BridgeLease`, `BridgeMode`, and
`BridgeOwnership`. The [matrix](bridge-matrix.csv) has 168 rows across version,
loader, backend, object, and direction. OpenGL supports borrowed zero-copy
texture/buffer/shader wrappers, rebuilt sampler/pipeline values, and in-place
encoder/render-pass adapters. Vulkan rows are unsupported because zero-copy is
unsafe; `unknown` backend rows are also unsupported.

## Minecraft 26.1.2 Runtime

`MinecraftGraphicsRuntime2612.bindCurrent(CreationConfig)` is the public,
loader-neutral binding API for a current Minecraft OpenGL render context. It
owns the Prism instance, external device, graphics queue, command pool and
command buffer, plus `Blaze3DBridge2612` and `LuminGraphicsContext`. Call
`beginFrame(id)` and exactly one of `endFrame()` or `abortFrame()` on the render
thread. The runtime borrows the current Blaze3D main target on demand and
automatically replaces its wrappers when the color/depth views or dimensions
change. The loader lifecycle submits recorded 2D commands after
`GameRenderer.render` and before Minecraft blits the main target to the window.
`invalidateRenderTargets(reason)` is only valid outside an active
frame. `invalidateContext()` rejects all later access, and `close()` is
idempotent and never native-closes Minecraft textures or views.
`MinecraftGraphicsRuntime2612.current()` returns the runtime already bound by
the active loader, so common consumers do not import loader entrypoint types.

Fabric and NeoForge entrypoints only bind this common runtime and close it at
client shutdown. No loader API is present in the common implementation.

### UI fonts and glyph atlases

`MinecraftUiRuntime2612` owns resource-pack font loading, glyph atlas upload,
Minecraft texture registration, resource-reload invalidation, and the adapter
used by `net.minecraft.client.gui.Font`. Consumers register business font IDs
with `registerFont`, select the default with `useDefaultFont` or
`useCustomDefaultFont`, and use `font`, `textMetrics`, `minecraftFont`, or
`systemEmojiAtlas` directly. Glyphs returned by `minecraftFont()` require a
`MinecraftFontAdapter2612.RenderOptions` because the render pipelines and
antialiasing policy remain application state.

Returned font and atlas objects are borrowed from the runtime. Resource reload
and `MinecraftUiRuntime2612.close()` release them. Every TTF atlas page is fixed
at `1024 x 1024` pixels; `UiConfig` rejects any other dimensions. The default UI font rasterization is capped at
`48` pixels per glyph with `4` pixels of SDF padding, and `UiConfig` rejects larger values. The configured glyph height
includes the SDF padding on both sides. Atlas revisions keep image, descriptor, sampler, and Minecraft texture ownership
only while referenced; staging buffers and retired
revisions are released after the frame command buffer has submitted, rather
than accumulating until runtime shutdown.

`MinecraftUiRuntime2612.setFontGlyphsPerFrame(int)` limits the total number of
real glyphs written by all runtime-owned fonts in one Minecraft frame.
Unsupported code points and supported glyphs deferred by this budget use the
built-in hollow-box glyph. Layouts containing deferred glyphs retry on later
frames instead of caching the placeholder permanently.

Each UI runtime uses one dedicated daemon thread for STB glyph rasterization.
Rasterized pixels enter a shared queue; atlas mutation and GPU upload are drained
from that queue on the Minecraft render thread at the configured per-frame limit.
Resource reload cancels stale font requests, and runtime shutdown also stops the
rasterizer thread.

Each 2D frame uses a top-left-origin orthographic projection derived from the
current framebuffer size and an effective UI scale. The runtime defaults to
Minecraft's GUI scale; an application such as Epsilon can call
`MinecraftUiRuntime2612.setProjectionScale` before submitting its scene to use
its own scale. The projection scale changes the coordinate-to-framebuffer
mapping, not font layout or text measurement. Consumers submit logical
coordinates through `UiTree`; the runtime owns the projection uniform and
descriptor.

For 2D HUD blur, construct a `MinecraftBlurRegion2612` in GUI coordinates and
call `MinecraftUiRuntime2612.applyBlur(region)` during an active Minecraft
graphics frame. The runtime copies the current main target to a feedback target
before sampling, owns the blur sampler and shader bindings, retires per-frame
GPU resources after frame completion, and releases all remaining resources on
`close()`. This API intentionally does not expose generic post-processing or
glow-mask behavior.

## Local release publishing

The current development version uses LuminGraphics-MC `1.2.4-SNAPSHOT`, LuminGraphics `1.2.4-SNAPSHOT`, and
PrismRHI `0.2.2`. Publish PrismRHI first, then LuminGraphics, then these
matching loader artifacts:

```powershell
cd D:\Dev\ChenMeng\LuminGraphics
.\gradlew.bat publishToMavenLocal
cd D:\Dev\ChenMeng\LuminGraphics-MC
.\gradlew.bat publish
```

The MC `publish` task defaults to Maven Local. Pass
`-PpublishRepository=D:\Dev\ChenMeng\maven-repository` only for an explicit
shared file-based Maven repository. The same property is used as the first
dependency repository; pass `-PlocalRepository=...` when resolution and
publication use different paths.

Borrowed leases observe exact context/owner-render-thread/current-GL checks and
become unusable after their owner token invalidates. Closing a borrowed lease
must not destroy Minecraft's native resource; rebuilt values have owned close
behavior stated in their row.

## Access And Versions

Fabric uses the versioned access wideners:
`../mc-26.1.2/fabric/src/main/resources/lumin_graphics_mc_2612.accesswidener` and
`../mc-26.2/fabric/src/main/resources/lumin_graphics_mc_262.accesswidener`.
NeoForge uses the matching `META-INF/accesstransformer.cfg` files. Mixins are
versioned in each common module; 26.2 additionally inventories targets in
`lumin-graphics-mc-262.access-targets.properties`. Do not use reflection.

## Smoke Receipts

`runAllBridgeSmokes` and `runAllBridgeNegativeSmokes` produce four positive and
four negative JSON receipts; each positive variant also emits an 8x8 PNG with
a pixel hash. Override the configured output directory with
`LUMIN_MC_SMOKE_EVIDENCE_DIR` when required. The default points to a sibling
LuminGraphics migration attempt and is not a consumer install location.

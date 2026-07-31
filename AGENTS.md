# LuminGraphics-MC Knowledge Base

## Overview

LuminGraphics-MC 1.0.0 bridges Minecraft client rendering to LuminGraphics
through a loader/version matrix. It targets Java 25 and publishes local Maven
artifacts only; no public registry workflow is documented.

## Structure

```text
bridge-contract/          shared bridge ownership/matrix contracts
mc-26.1.2/{common,fabric,neoforge}/ Minecraft 26.1.2 bridge and loader modules
mc-26.2/{common,fabric,neoforge}/   Minecraft 26.2 bridge and loader modules
docs/bridge-matrix.csv    168-row capability/provenance matrix
reference/                checked source references for version comparison
```

## Where To Look

| Task | Location | Notes |
| --- | --- | --- |
| Contract types | `bridge-contract/src/main/java/.../bridge` | Ownership, mode, matrix and leases. |
| Capability matrix | `docs/bridge-matrix.csv` | Source of supported/unsupported combinations. |
| 26.1.2 bridge | `mc-26.1.2/common/src/main/java/.../v2612` | Access, bridge, mixin, smoke, text. |
| 26.2 bridge | `mc-26.2/common/src/main/java/.../v262` | Version-specific access target changes. |
| Loader metadata | versioned Fabric/NeoForge `src/main/resources` | AW, AT, mixin and mod descriptors. |
| Smoke wiring | `build.gradle` | Positive and negative matrix smokes. |

## Code Map

| Symbol/area | Location | Role |
| --- | --- | --- |
| `BridgeMatrix` | bridge-contract | Validates matrix rows and provenance. |
| `BridgeLease` | bridge-contract | Borrowed/rebuilt bridge lifecycle. |
| `Blaze3DBridge2612` | 26.1.2 common | Versioned Minecraft adapter. |
| `RealClientBridgeSmoke2612` | 26.1.2 common | JSON/PNG smoke receipt producer. |
| `GlAccess262` | 26.2 common | Versioned mixin access boundary. |

Reference centrality is explicitly unmeasured: no LSP or codegraph service is
configured for this repository.

## Conventions

- Update the contract, version bridge, loader metadata, and matrix together.
- Treat access wideners, access transformers, and Mixins as loader/version
  specific, never shared by assumption.
- Preserve bridge ownership, invalidation token, exact context, and thread
  rules from the matrix row.

## Anti-Patterns

- Do not use reflection to bypass Minecraft internals.
- Do not enable zero-copy Vulkan paths: matrix rows mark them unsupported.
- Do not use a common JAR as a final mod artifact; consume the matching loader
  JAR for Fabric or NeoForge and Minecraft version.
- Do not hard-code the default external smoke path in consumer instructions;
  set `LUMIN_MC_SMOKE_EVIDENCE_DIR` when overriding it.

## Commands

```powershell
.\gradlew.bat verifyMinecraftSources
.\gradlew.bat runAllBridgeSmokes
.\gradlew.bat runAllBridgeNegativeSmokes
```

## Documentation

The [root README](README.md) and [docs index](docs/README.md) link all current
consumer and matrix documents. Keep the wording local-Maven-only.

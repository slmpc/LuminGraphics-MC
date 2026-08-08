# Bridge Contract Notes

## Scope

`bridge-contract` owns loader-neutral compatibility, ownership, mode, lease,
unsupported-detail, and CSV matrix validation contracts.

## Entrypoints

| Area | Location |
| --- | --- |
| Matrix parser/validator | `src/main/java/.../bridge/BridgeMatrix.java` |
| Compatibility result | `src/main/java/.../bridge/BridgeCompatibilityAudit.java` |
| Lease lifecycle | `src/main/java/.../bridge/BridgeLease.java` |
| Direction/mode/ownership | `src/main/java/.../bridge/BridgeDirection.java`, `BridgeMode.java`, `BridgeOwnership.java` |
| Unsupported detail | `src/main/java/.../bridge/BridgeUnsupported*.java` |

## Tests And Assets

Tests in `src/test/java` validate matrix structure and contract semantics.
`build.gradle.kts` packages `../../docs/bridge-matrix.csv` as
`bridge/bridge-matrix.csv`; do not create another matrix source.

Run `..\\gradlew.bat :bridge-contract:test` for contract-only changes.

## Pitfalls

- Keep the exact CSV header and every version/loader/backend/object/direction
  row coherent with `BridgeMatrix` validation.
- A matrix row defines ownership, invalidation, close behavior, and thread
  context together; do not change one field in isolation.
- Preserve provenance hashes and NeoForm origin fields when regenerating rows.
- The contract must stay free of Fabric, NeoForge, and versioned Minecraft
  implementation classes.

## Integration

Both common modules consume this contract. Put adapter implementation and
access mechanisms in version modules, not in the shared contract.

# LuminGraphics-MC Documentation

This repository documents local Maven artifacts only; it does not publish a
public registry consumer flow.

## Index

- [Repository README](../README.md): artifact selection and capability summary.
- [Consumer and bridge guide](guide.md): loader setup, ownership, access, and smokes.
- [Bridge matrix](bridge-matrix.csv): 168 supported/unsupported and provenance rows.

The matrix's `minimum_mode`, `thread_context_rule`, `owner`, `invalidation`,
and `close_behavior` fields form one contract. Read them together.

## Build Configuration

All Gradle build and shared module scripts use Kotlin DSL. Minecraft-specific
version-catalog aliases carry an explicit version prefix, for example
`minecraft-v2612`, `fabric-loader-v2612`, and `neoforge-v262`.

Locally developed `com.github.slmpc.lumingraphics` and
`com.github.slmpc.prismrhi` dependencies resolve exclusively from Maven Local.
The default `publish` task publishes this repository's artifacts to Maven Local.
Pass `-PpublishRepository=<path>` only when an explicit shared file-based Maven
repository is required.

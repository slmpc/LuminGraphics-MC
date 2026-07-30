# LuminGraphics-MC Documentation

This repository documents local Maven artifacts only; it does not publish a
public registry consumer flow.

## Index

- [Repository README](../README.md): artifact selection and capability summary.
- [Consumer and bridge guide](guide.md): loader setup, ownership, access, and smokes.
- [Bridge matrix](bridge-matrix.csv): 168 supported/unsupported and provenance rows.

The matrix's `minimum_mode`, `thread_context_rule`, `owner`, `invalidation`,
and `close_behavior` fields form one contract. Read them together.

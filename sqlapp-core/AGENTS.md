# sqlapp-core development instructions

These instructions supplement the repository root `AGENTS.md`.

## Compatibility

`sqlapp-core` is shared by every dialect and command and is
compatibility-sensitive.

Before modifying a common abstraction:

1. Search for subclasses, implementations and callers.
2. Check use in affected `sqlapp-core-{db}` modules.
3. Prefer backward-compatible default behavior or extension points.
4. Keep database-specific conditionals out of core.
5. Add common behavior only when multiple consumers need it.

For a proposed core change, identify:

- The missing model or extension point
- Why it cannot remain local to a dialect or command
- Affected modules and dialects
- Default behavior and possible overrides
- Public API, serialization and XML compatibility risks
- Required common and dependent-module tests

Do not implement a core change when the request asks only for impact analysis.

## Schema model

- Preserve Catalog, Schema, Table, Column, Row and relationship ownership.
- Prefer resolving identifiers through the Schema model over duplicating names.
- Consider catalog/schema ambiguity, case sensitivity and quoted identifiers.
- Keep serialized XML compatible unless a format change is explicitly approved.
- Shared logical concepts such as viewpoints belong here; YAML I/O does not.

## Testing

- Run focused core tests first.
- Run affected command and dialect tests after shared behavior changes.
- Use structural assertions for Schema transformations.
- Test ambiguous, missing and duplicate identifier failure paths.

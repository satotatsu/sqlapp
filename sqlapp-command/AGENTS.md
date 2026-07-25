# sqlapp-command development instructions

These instructions supplement the repository root `AGENTS.md`.

## Command design

- Keep commands usable independently of Gradle.
- Treat command properties and configuration files as public APIs.
- Put business logic in commands or shared services, not Gradle tasks.
- Validate required files, unsupported values, ambiguous names and unsafe
  combinations with actionable messages.
- Use UTF-8 and atomic replacement for generated configuration where practical.
- Do not silently ignore migration, import, export or SQL execution failures.
- Update `sqlapp-gradle-plugin` when public command properties need task access.

## Schema and configuration

- Read database structure through the shared Schema model.
- Keep YAML concise and avoid repeating resolvable Schema information.
- Accept unqualified names only when unique; reject ambiguity.
- Separate reusable logical selection, such as viewpoints, from
  command-specific execution policies.
- Preserve fingerprints and resolved IDs in generated migration artifacts.

## Migration and data commands

Explicitly consider:

- Transaction and commit boundaries
- Rollback and partial failure
- Restart and repair behavior
- Batch sizes and JDBC parameter limits
- Cursor behavior across commits
- Parent-child closure and load order
- Non-transactional DDL
- Up/down symmetry where applicable

XML containing row data must not be transformed unless the command explicitly
supports data conversion.

## HTML documentation

- Preserve existing menus, table pages and relationship display modes.
- Escape generated HTML text and attributes.
- Reuse the Schema model for tables, relationships and viewpoints.
- Test generated structure and links rather than platform-dependent geometry.

## Testing

- Add successful and invalid-input tests.
- Prefer command-level tests before Gradle task tests.
- Use HSQL or another embedded database only when actual SQL execution is part
  of the behavior.
- Clearly identify tests that require an external database.

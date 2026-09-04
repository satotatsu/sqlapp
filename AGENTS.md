# AGENTS.md

## Project overview

sqlapp is a Java 21 and Gradle multi-project database utility.

- `sqlapp-core`: shared Schema model, JDBC access and SQL generation
- `sqlapp-command`: documentation, data handling and migration commands
- `sqlapp-gradle-plugin`: Gradle tasks backed by commands
- `sqlapp-core-{db}`: database-specific dialects
- `sqlapp-elk-svg`: current ELK-based SVG ER diagrams
- `sqlapp-graphviz`: legacy Graphviz ER diagrams
- `sqlapp-core-test`: shared test utilities

Read the nearest module `AGENTS.md` in addition to this file.

## Environment

- Use Java 21 and the repository Gradle Wrapper.
- Use UTF-8 for source files and generated text.
- Do not modify `build/`, `bin/`, `.gradle/`, generated output, dependency
  versions, credentials, production data or local settings unless required.
- Do not access an external or production database without explicit
  authorization.

## Working rules

- Read relevant implementation and tests before changing code.
- Keep changes limited to the requested task and preserve unrelated work.
- Preserve public APIs and configuration formats unless an incompatible change
  is explicitly requested.
- Follow existing naming, formatting, package and test conventions.
- Add or update tests for behavior changes and documentation for public changes.
- Do not silently ignore invalid configuration or execution failures.
- Clearly distinguish verified facts from assumptions.
- Do not claim a build or test passed unless it was executed.
- For review-only requests, do not modify files.

Before implementation, identify affected modules, similar implementations,
public API risks, the smallest implementation location and required tests.

## Architecture

- Design every user-facing feature to be simple for the common case and
  extensible through optional configuration for advanced requirements.
- Provide one clear high-level entry point with safe defaults before exposing
  lower-level planners, stores, listeners, dialect hooks or execution phases.
- Keep advanced controls available without making them mandatory for ordinary
  use. Prefer progressive disclosure: common settings first, optional
  per-table or vendor-specific overrides second, and low-level APIs last.
- Do not duplicate advanced behavior in a facade. Compose the existing shared
  model, validators, planners and executors so simple and advanced paths retain
  the same semantics and safety checks.
- Treat the sqlapp Schema model as the canonical database representation.
- Prefer the Schema model and sqlapp-core SQL factories for DDL and DML generation.
  Build SQL strings directly only when the shared generator cannot represent required
  vendor-specific behavior, and keep that SQL in the owning dialect module where possible.
- Cross-cutting schema features should consume the shared Schema model.
- Shared abstractions belong in `sqlapp-core`.
- Command behavior and YAML/file handling belong in `sqlapp-command`.
- Gradle task classes should only expose properties and invoke commands.
- Database-specific behavior belongs in the corresponding `sqlapp-core-{db}`.
- ER layout and SVG rendering belong in `sqlapp-elk-svg`.
- Do not add module dependencies without explaining the reason and impact.

## Configuration design

- The minimum valid configuration should express user intent rather than
  internal execution mechanics. Infer values only when the result is
  unambiguous and safe.
- Defaults must be useful and safe. When no honest safe default exists, require
  the specific value with an actionable error instead of guessing; data
  fingerprints, destructive behavior and transaction guarantees are examples.
- Adding an advanced option must not complicate the default path. Group related
  options and preserve a concise high-level API or configuration example.
- Results from high-level APIs must retain access to detailed plans, status,
  verification and failure information so convenience does not hide
  correctness-relevant facts.
- Keep user-authored configuration and Schema XML as simple and concise as
  practical.
- Do not require information that can be resolved unambiguously from the shared
  Schema model; fail clearly when resolution is ambiguous.
- Avoid unnecessary nesting, wrappers and duplicated identifiers.
- Prefer sensible defaults and optional configuration.
- Generated migration artifacts must retain enough resolved identifiers and
  fingerprints for reproducibility and validation.

## Schema viewpoints

- A viewpoint is a flat, named logical view containing a list of Schema tables.
- A table may belong to multiple viewpoints.
- Resolve table names against catalog, schema and table identity.
- Allow short names only when they resolve uniquely.
- Viewpoints are reusable by HTML documentation and Loader generation.
- Loader-specific hierarchy closure and safety validation belong to the Loader,
  not to the viewpoint model.
- Migration artifacts using a viewpoint must preserve its ID, configuration
  fingerprint and final resolved table or data-set IDs.

## Database dialects

For `sqlapp-core-{db}` changes:

- MetadataReader must continue to operate correctly on past supported database
  versions.
- Exact compatibility of previously read fields or Schema model representations
  is not required when improving dialect metadata behavior.
- Isolate version-specific catalog columns and SQL syntax in the reader for the
  database version that supports them.
- State the database product and affected version range.
- Check `DialectResolver` and preserve older supported behavior.
- Inherit from the nearest compatible version and override only differences.
- Keep vendor catalog queries in the dialect module.
- Consider identifiers, quoting, case, data types and version boundaries.
- Separate metadata-reading changes from SQL-generation changes.
- Prefer a body or query in `statement` plus separately modeled components such
  as procedure arguments or view columns when both can be read reliably and can
  recreate the object together.
- When that split representation cannot be read reliably, store the complete,
  executable CREATE DDL in `definition`; components already embedded in that DDL
  are then optional supplemental metadata rather than mandatory duplicates.
- Test object recreatability from either `statement` plus modeled components or
  from `definition`, according to the representation selected by the reader.
- Add relevant resolution, type, quoting, SQL, metadata and boundary tests.
- Clearly separate tests requiring a real database.

## Testing

Run the smallest relevant scope first:

1. Changed test class
2. Changed module
3. Directly dependent modules
4. Wider tests for shared changes

Do not run external database tests unless the target, credentials and
destructive behavior are explicitly authorized.

Report commands run, passes, failures, unexecuted tests, external databases and
intentional generated-file changes.

## Completion

Report the change summary, modified modules, public API and compatibility
impact, tests, documentation, database impact and remaining limitations.

For investigations and reviews, cite relevant files, separate confirmed
findings from recommendations and prioritize correctness risks.

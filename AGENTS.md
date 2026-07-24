# AGENTS.md

## Project overview

sqlapp is a Java and Gradle multi-project database utility.

Main modules:

- `sqlapp-core`
  - Shared database models such as Catalog, Schema, Table, Column and Row
  - JDBC access
  - Metadata abstractions
  - Common SQL-generation infrastructure
- `sqlapp-command`
  - Schema documentation
  - Test-data generation
  - Data import and export
  - Database migration
  - Schema and data synchronization
- `sqlapp-gradle-plugin`
  - Gradle tasks backed by `sqlapp-command`
- `sqlapp-core-{db}`
  - Database-specific dialect implementations
- `sqlapp-elk-svg`
  - ELK-based SVG ER diagram generation
- `sqlapp-graphviz`
  - Legacy Graphviz-based ER diagram generation
- `sqlapp-core-test`
  - Shared test utilities

## Development environment

- Use Java 21.
- Use the Gradle Wrapper included in this repository.
- Use UTF-8 for source files and generated text.
- Do not modify files under `build/`, `bin/`, or `.gradle/`.
- Do not commit database credentials, production data, or local environment settings.
- Do not connect to an external or production database unless explicitly authorized.

## General working rules

- Read the relevant implementation and tests before making changes.
- Keep changes limited to the requested task.
- Do not include unrelated formatting, cleanup, dependency updates, or refactoring.
- Preserve existing public APIs unless an incompatible change is explicitly requested.
- Prefer backward-compatible additions.
- Follow existing naming, formatting, package, and test conventions.
- Add or update tests for every behavior change.
- Update documentation when public behavior or configuration changes.
- Clearly distinguish verified facts from assumptions.
- Do not claim that a build or test passed unless it was actually executed.
- Do not modify dependency versions unless required by the task.
- Do not modify generated output merely to make a test pass without explaining why the output changed.

## Before implementation

Before changing code:

1. Identify the affected modules.
2. Find similar implementations and tests.
3. Describe the intended behavior.
4. Identify public API and compatibility risks.
5. Determine the smallest appropriate implementation location.
6. List the tests that should be added or updated.

For database-related work, also identify:

- Target database
- Target database versions
- JDBC driver and version
- Metadata behavior affected
- SQL-generation behavior affected
- Whether `sqlapp-core` must change
- Possible impact on other dialects

## Module dependency rules

- `sqlapp-core` must not depend on a database-specific dialect module.
- Database-specific modules may depend on `sqlapp-core`.
- Database-specific behavior belongs in `sqlapp-core-{db}`.
- Shared abstractions and extension points belong in `sqlapp-core`.
- Command behavior belongs in `sqlapp-command`.
- Gradle-specific integration belongs in `sqlapp-gradle-plugin`.
- Business logic must not be implemented only in Gradle task classes.
- ER-diagram layout and SVG generation belong in `sqlapp-elk-svg`.
- Do not add new dependencies between modules without explaining the reason and impact.

## sqlapp-core rules

Treat changes to `sqlapp-core` as compatibility-sensitive.

Before modifying a common abstraction:

1. Search for all subclasses, implementations, and callers.
2. Check its use in every affected `sqlapp-core-{db}` module.
3. Prefer adding an extension point with backward-compatible default behavior.
4. Avoid database-specific conditional logic in `sqlapp-core`.
5. Add common behavior only when it is useful to multiple dialects or required by the common model.

When proposing a `sqlapp-core` change for a dialect enhancement, report:

- The missing common model or extension point
- Why the dialect cannot implement the feature locally
- Affected classes and modules
- Proposed default behavior
- Dialects that may need an override
- Compatibility risks
- Required tests

Do not implement the `sqlapp-core` change when the task asks only for an impact analysis.

## Database dialect rules

For changes under `sqlapp-core-{db}`:

- State the database product and affected version range.
- Check the corresponding `DialectResolver`.
- Preserve behavior for older supported database versions.
- Inherit from the nearest compatible version-specific dialect where appropriate.
- Override only behavior that differs.
- Separate metadata-reading changes from SQL-generation changes.
- Keep vendor system-catalog queries inside the relevant dialect module.
- Do not assume that JDBC `DatabaseMetaData` exposes every vendor-specific object.
- Consider identifier quoting, case sensitivity, reserved words, catalogs and schemas.
- Consider new, changed and removed data types.
- Add version-boundary tests when behavior differs by database version.
- Check whether generated SQL changes for existing database versions.
- Record the JDBC driver used for verification.

Dialect tests should cover relevant areas:

- Dialect resolution
- Data types
- Identifier quoting
- SQL generation
- Metadata reading
- Version boundaries
- Database-specific objects

Tests that require a real database must be clearly separated from tests that do not.

## sqlapp-command rules

When modifying `sqlapp-command`:

- Check whether the behavior is exposed through `sqlapp-gradle-plugin`.
- Keep command logic usable independently of Gradle.
- Treat command properties and configuration files as user-facing APIs.
- Preserve existing configuration formats where practical.
- Validate invalid input and failure paths.
- Consider file encoding and line-ending behavior.
- Consider transaction, rollback, batch and partial-failure behavior.
- Do not silently ignore migration, import, export, or SQL execution failures.
- Document new or changed command properties.
- Add tests for both successful and unsuccessful execution.

For migration changes, explicitly consider:

- Transaction boundaries
- Commit behavior
- Version table state
- Repair behavior
- Up and down symmetry
- Partially executed files
- Non-transactional DDL

## sqlapp-gradle-plugin rules

- Treat Gradle task names and task properties as public user-facing APIs.
- Do not rename or remove a task or property without an explicit compatibility plan.
- Keep business logic in `sqlapp-command`.
- Gradle task classes should primarily map configuration and invoke commands.
- Check whether new command properties need corresponding Gradle properties.
- Use Gradle TestKit for plugin behavior when appropriate.
- Consider supported Gradle versions when using new Gradle APIs.

## sqlapp-elk-svg rules

When changing ER-diagram or SVG behavior:

- Preserve valid XML and SVG output.
- Escape XML text and attributes correctly.
- Test both logical and physical names when relevant.
- Test `SIMPLE` and `NORMAL` drawing modes when relevant.
- Consider Japanese and other wide characters.
- Avoid platform- and font-dependent output where practical.
- Add structural assertions in addition to full SVG string comparisons.
- Explain every intentional expected-SVG change.
- Do not replace expected SVG output without inspecting the difference.

Changes to edges or layout should consider:

- Self-referencing foreign keys
- Composite foreign keys
- Multiple foreign keys between the same tables
- Long table and column names
- Multiple schemas
- Isolated tables
- Cyclic relationships
- Special XML characters
- Large schemas

## Testing rules

Run the smallest relevant test scope first:

1. Specific changed test class
2. Tests for the changed module
3. Tests for directly dependent modules
4. Wider tests for changes to shared code

Typical test scope:

- `sqlapp-core` change:
  - Core tests
  - Affected dialect tests
  - Affected command tests
- Dialect change:
  - Dialect unit tests
  - Version-resolution tests
  - SQL-generation tests
  - Relevant integration tests
- `sqlapp-command` change:
  - Command tests
  - Relevant Gradle Plugin tests
- `sqlapp-elk-svg` change:
  - ELK-SVG tests
  - Relevant HTML-document generation tests

Do not run tests requiring an external database unless:

- The task explicitly authorizes it
- The connection target is confirmed to be a test database
- Required credentials are supplied safely
- Destructive test behavior is understood

After testing, report:

- Commands executed
- Tests that passed
- Tests that failed
- Tests not executed and why
- External databases used
- Generated expected files intentionally updated

## Documentation rules

Update relevant documentation when changing:

- Public Java APIs
- Gradle tasks or properties
- Configuration file formats
- Supported database versions
- JDBC driver requirements
- Migration behavior
- Generated documentation
- ER-diagram or SVG behavior
- Build or test requirements

Code comments should explain non-obvious reasons and database-specific constraints.
Do not add comments that merely restate the code.

## Completion report

At the end of an implementation task, report:

- Summary of the change
- Modified files
- Affected modules
- Public API impact
- Database and version impact
- `sqlapp-core` impact
- Compatibility considerations
- Tests executed and results
- Tests not executed
- Documentation updated
- Remaining limitations or follow-up work

## Review-only requests

When asked to investigate, review, enumerate impacts, or propose a design:

- Do not modify files.
- Do not implement a fix unless explicitly requested.
- Cite relevant files and classes.
- Separate confirmed findings from recommendations.
- Prioritize findings by correctness and compatibility risk.
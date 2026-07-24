# AI development instructions

## Project overview

sqlapp is a Java and Gradle multi-project database utility.

Main modules:

- sqlapp-core:
  Shared database models, JDBC access, metadata abstractions,
  SQL generation foundations, and common utilities.
- sqlapp-command:
  Commands for documentation, migration, data transfer,
  schema synchronization, and test-data generation.
- sqlapp-gradle-plugin:
  Gradle tasks wrapping sqlapp-command.
- sqlapp-core-{db}:
  Database-specific dialect implementations.
- sqlapp-elk-svg:
  ELK-based SVG ER diagram generation.
- sqlapp-graphviz:
  Legacy Graphviz-based ER diagram generation.

## General rules

- Use Java 21 and the repository Gradle Wrapper.
- Preserve existing source encoding and formatting.
- Do not modify generated files under build/ or IDE output under bin/.
- Do not make unrelated changes.
- Do not change public APIs unless the task explicitly requires it.
- Prefer backward-compatible additions over replacements.
- Add or update tests for every behavior change.
- Do not update dependency versions unless required by the task.
- Do not access a real database unless explicitly authorized.
- Never place database credentials or production data in the repository.
- Clearly distinguish verified behavior from assumptions.

## Before implementing

1. Identify all affected modules.
2. Locate existing implementations and tests with similar behavior.
3. Describe the intended behavior.
4. List compatibility risks.
5. For a dialect change, state whether sqlapp-core must change.
6. For a command change, check the Gradle Plugin exposure.
7. For an SVG change, identify affected expected-output fixtures.

## sqlapp-core rules

- Treat public model, metadata, dialect and SQL APIs as compatibility-sensitive.
- Check usages in all sqlapp-core-{db} modules before changing an abstraction.
- Do not add database-specific behavior to sqlapp-core.
- Add an extension point when multiple dialects require the same capability.
- Prefer default behavior that preserves existing dialect output.
- Document which dialects override new behavior.

## Dialect rules

For changes under sqlapp-core-{db}:

- Record the database product and affected versions.
- Confirm the appropriate DialectResolver behavior.
- Separate SQL-generation changes from metadata-reader changes.
- Cover identifier quoting, case sensitivity and reserved words.
- Consider catalogs, schemas and database-specific object types.
- Consider new and removed data types.
- Add tests for version boundaries when behavior differs by version.
- Preserve behavior for older supported database versions.
- Do not assume JDBC DatabaseMetaData exposes vendor-specific objects.
- Use vendor system catalogs only within the relevant dialect module.

Before proposing a sqlapp-core change, report:

- Missing common model or extension point
- Dialects that could be affected
- Proposed default behavior
- Required overrides
- Compatibility risk
- Required tests

## sqlapp-command rules

- Check whether a corresponding Gradle task or property exists.
- Keep command behavior independent of Gradle where possible.
- Validate file, encoding, transaction and failure behavior.
- Avoid silent partial migrations or imports.
- Add tests for invalid input and failure paths.
- Keep configuration formats backward compatible when practical.
- Document newly introduced properties.

## sqlapp-gradle-plugin rules

- Treat task names and property names as user-facing APIs.
- Do not rename or remove tasks without an explicit compatibility plan.
- Keep business logic in sqlapp-command rather than Gradle task classes.
- Test task configuration with Gradle TestKit when appropriate.

## sqlapp-elk-svg rules

- Preserve valid XML and SVG escaping.
- Test both logical-name and physical-name modes when relevant.
- Test SIMPLE and NORMAL drawing modes when relevant.
- Consider Japanese and other wide characters.
- Avoid depending on platform-specific font metrics where possible.
- Add focused structural assertions in addition to full SVG snapshots.
- Explain intentional snapshot changes.
- Cover self-references, composite foreign keys and multiple schemas
  when changing edge or layout behavior.

## Testing rules

Start with the smallest relevant scope:

1. Specific test class
2. Changed module tests
3. Directly dependent module tests
4. Wider verification when shared code changes

A task is not complete until the response reports:

- Tests run
- Tests passed or failed
- Tests not run and why
- External databases used, if any

Do not claim that the build passes if it was not executed.

## Documentation rules

Update documentation when changing:

- Public APIs
- Gradle tasks or properties
- Configuration files
- Supported database versions
- JDBC driver requirements
- Migration behavior
- Generated document or SVG behavior

## Change report

At completion, report:

- Summary
- Modified files
- Affected modules
- Compatibility impact
- sqlapp-core impact
- Tests
- Remaining limitations
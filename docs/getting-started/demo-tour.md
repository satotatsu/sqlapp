# Try three sqlapp workflows

[Documentation index](../README.md) · [Getting started](README.md)

Run one command to see how the shared Schema model connects database
documentation, change review, and verified data migration. These repository
demos use fictional customer/order data and require no external database.

## Run once and open the results

With Java 21, run from the repository root:

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

Windows PowerShell:

```powershell
.\gradlew.bat --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

Open `build/docs/demo/index.html`. This page links the generated outputs and
reads the migration results from the actual verification reports. It is
written after all three demos complete successfully.

The first build may download Gradle and dependencies. Add `--offline` when
they are cached. No external database is used: documentation and SQL generation
read XML, and the migration creates two private HSQL memory databases inside
its own process. The demo accepts no external database connection settings.

## What each step demonstrates

| Step | Open or compare | What to notice |
|---|---|---|
| Understand and share | HTML reference, table DDL, and ER diagram | The same model produces pages, SQL, and relationship diagrams |
| Review a change | Before/after HTML and `change.sql` | Adding `CUSTOMER.EMAIL` changes both the documented model and generated HSQL SQL |
| Move and verify | Execution, matching verification, and mismatch reports | Five rows transfer successfully; changing one name produces a mismatch even though counts remain equal |

The mismatch is intentional and asserted by the demo. A successful Gradle
result means the expected mismatch was detected, not that the deliberately
changed target was repaired. No repair or generated change SQL is executed.
The examples do not demonstrate production readiness, performance, or crash
recovery. A multi-table job is not one atomic transaction.

## Explore or adapt one step

- [Documentation demo](offline-demo.md): sample XML, HTML structure, and diagram navigation.
- [Change-review demo](offline-demo.md#review-a-schema-change): desired model and generated SQL.
- [Migration demo](offline-migration.md): fixture rows, actual reports, and validation checks.
- [Choose a workflow](use-cases.md): operational boundaries and integration choices.

Each demo can be run independently using the task in its guide. Re-running
the combined tour replaces generated demo artifacts and creates new memory
databases. It does not modify the XML fixtures, resume a previous migration,
or add tasks to ordinary builds; the init script is opt-in.

To share the results, keep `build/docs/demo`, `build/docs/offline-demo`,
`build/docs/offline-change`, and `build/docs/offline-migration` together under
one directory so the overview's relative links continue to work.

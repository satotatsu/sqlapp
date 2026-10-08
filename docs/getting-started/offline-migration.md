# Try verified data migration with in-memory databases

[Documentation index](../README.md) · [Getting started](README.md)

This repository demo transfers five fictional rows between two private HSQL
memory databases, verifies the result, and detects a deliberately changed
target value. It uses the same customer/order Schema XML as the
[documentation demo](offline-demo.md).

No server, credentials for an existing database, or connection configuration
is needed. The code creates its own `jdbc:hsqldb:mem:` databases with unique
names for each run. It accepts no database URL and never connects to an external
database. Both databases disappear when the demo process exits.

## Run the demo

Use Java 21 and the repository Gradle Wrapper from the repository root:

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoVerifiedDataMigration
```

Windows PowerShell:

```powershell
.\gradlew.bat --init-script docs/examples/offline-html/demo.init.gradle demoVerifiedDataMigration
```

Add `--offline` if build dependencies are already cached. Without it, Gradle
may download dependencies; the data transfer itself stays inside the process.
The optional init script leaves ordinary builds unchanged.

## What to expect

| Stage | Source | Target | Verification |
|---|---|---|---|
| Setup | Two customers and three orders | Empty customer/order tables | Not yet run |
| Migration | Five rows | Five transferred rows | MATCH |
| Deliberate target change | Original five rows | Same five rows, one customer name changed | MISMATCH for CUSTOMER |

The foreign key connects `CUSTOMER_ORDER.CUSTOMER_ID` to
`CUSTOMER.CUSTOMER_ID`. The facade plans the parent before its child and performs
UPSERT followed by verification. The second verification is read-only: it
detects the changed value and leaves it in place for inspection. It does not
re-run migration or repair the target.

Expected summary:

```text
Initial migration: MATCH; 2 tables; 5 source rows; 5 target rows
After changing CUSTOMER name: MISMATCH; 1 table; 5 source rows; 5 target rows
Detection uses ordered row hashes as well as counts. No repair was executed.
```

This is the useful distinction to inspect: row counts still match after the
change, while ordered chunk hashes identify a data mismatch.

## Inspect the artifacts

| File under `build/docs/offline-migration/` | Contents |
|---|---|
| `summary.txt` | The two outcomes above |
| `execution.json` | Committed migration result and plan identity |
| `verification-match.json` | Successful verification after transfer |
| `verification-mismatch.json` | Verification after the target-only value change |

These are real reports written by the existing migration facade, not hand-made
JSON examples. The demo asserts the row counts and the mismatched table; an
unexpected result makes the task fail. Re-running creates new memory databases
and replaces the demo reports. It does not resume the previous run.

## How it works and where to go next

[MigrateDemo.java](../examples/offline-migration/MigrateDemo.java) loads the
shared XML model and removes the fictional catalog label from its in-memory
model so SQL uses the connection's catalog with the explicit `PUBLIC` schema.
It creates fixture tables through dialect SQL factories and seeds rows through
the shared bulk-insert API. It invokes `BulkMigration.run()`
for the ordinary transfer-and-verify path. The builder adds execution and
verification report destinations. A shared UPSERT call then changes one target
name, and `verify()` performs the separate non-throwing comparison.

This small fixture demonstrates result visibility and value-level verification.
It is not a performance benchmark or proof of production transaction guarantees.
It does not demonstrate crash recovery, resume, leases, concurrent source writes,
or repair approval. For those requirements, start with the
[migration entry point and options](../migration/verification-and-recovery.md)
and [migration workflow index](../migration/README.md). A multi-table job is
not one atomic transaction, and independent databases do not automatically
provide a common point-in-time snapshot.

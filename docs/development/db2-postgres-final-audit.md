# Db2 / PostgreSQL parity audit

Audit date: 2026-10-27. This is a working-tree audit, not a claim that parity is
complete. The comparison uses sqlapp's current Db2_1215 and Postgres180 dialects,
Java 25, their inherited factories and shared Schema model. Existing Db2 live
evidence covers disposable LUW 11.5.8.0 and 12.1.5.0; historical readers remain
subject to the existing compatibility requirement.

## Conclusion and completion boundary

The critical remainder is **two implementation packages and one verification
package**, below. The prior completion checklist mixed these with a much larger
Db2-specific expansion backlog. That made both the remaining size and the
meaning of completion unclear. It also put substantial vendor-specific work
ahead of defects in ordinary ALTER operations.

For parity of ordinary shared features, completion means:

1. Read supported objects into the canonical model and recreate the documented
   common cases, including names, types, constraints, identity/generated columns,
   comments and grants, without silent loss.
2. Generate executable changes for the supported ordinary cases; reject
   unsupported changes explicitly before returning an apparently successful plan.
   A non-empty meaningful diff must not silently become an empty operation list.
3. Exercise direct object, Schema and Catalog entry points with the same semantics.
4. Pass the existing unit suite, the two current Db2 live targets and the focused
   regression scenarios below. Preserve historical resolver/reader boundaries;
   do not claim unexecuted historical-server compatibility tests passed.

This does not mean implementing PostgreSQL syntax/features absent from Db2, or
fully modeling every Db2-only administrative object. It does not waive supported
historical-version compatibility or the requirement to report its unverified
boundaries. Broader vendor coverage is retained as a separate backlog.

## Reproduced evidence

Run the offline, opt-in audit:

```powershell
./gradlew.bat :sqlapp-core-dialect-test:db2PostgresAudit --console=plain
```

The [UTF-8 TSV](db2-postgres-audit-evidence.tsv) contains 64 observations: 32 per
dialect, combining reader/factory dispatch and generated SQL. `GENERATED` means
only that generation returned; `[]` is deliberately recorded rather than treated
as support. `RESOLVED` does not prove execution or metadata-field fidelity.
**No database connection is made and no generated statement is executed.**

The [audit source](../../sqlapp-core-dialect-test/src/dockerTest/java/com/sqlapp/data/db/dialect/test/audit/Db2PostgresCapabilityAudit.java)
uses the actual registry and model diffs. It intentionally records failures and
empty results. It is an evidence tool, not a green parity test. Reader-null cases
also need interpretation: PostgreSQL procedures are represented through its
Function reader and routine-kind metadata, not the standalone Procedure reader.

### Confirmed blocking findings

| ID | Reproduction | Confirmed result | Owning implementation |
| --- | --- | --- | --- |
| B1 | Db2 `column.default.add/drop`, `column.notNull.add`, `column.type.widen` | Emits full column declarations after ALTER COLUMN, e.g. `ALTER TABLE APP.T ALTER COLUMN V INTEGER DEFAULT 1`; dropping a default emits only `... V INTEGER` | [Db2AlterTableFactory](../../sqlapp-core-db2/src/main/java/com/sqlapp/data/db/dialect/db2/sql/Db2AlterTableFactory.java) inherits [AbstractAlterTableFactory.addAlterColumn](../../sqlapp-core/src/main/java/com/sqlapp/data/db/sql/AbstractAlterTableFactory.java) |
| B1 | Db2 `column.comment` and `table.comment` | Column comment emits `... ALTER COLUMN V INTEGER` without COMMENT; table comment returns `[]` | Same factory; shared behavior has not been replaced for these changes |
| B2 | Db2 `function.comment`, `function.body`, `procedure.comment` | Direct ALTER resolves EmptySqlFactory and returns `[]` | [Db2SqlFactoryRegistry](../../sqlapp-core-db2/src/main/java/com/sqlapp/data/db/dialect/db2/sql/Db2SqlFactoryRegistry.java), inherited [SimpleSqlFactoryRegistry](../../sqlapp-core/src/main/java/com/sqlapp/data/db/sql/SimpleSqlFactoryRegistry.java) |
| B2 | Db2 `schema.function.comment`, `catalog.function.comment` | Both higher-level entry points also return `[]` | [Db2AlterSchemaFactory](../../sqlapp-core-db2/src/main/java/com/sqlapp/data/db/dialect/db2/sql/Db2AlterSchemaFactory.java), [Db2AlterCatalogFactory](../../sqlapp-core-db2/src/main/java/com/sqlapp/data/db/dialect/db2/sql/Db2AlterCatalogFactory.java) |

The column output conflicts with the native ALTER forms: `SET DATA TYPE`,
`SET/DROP DEFAULT`, and `SET/DROP NOT NULL`. This grammar finding is supported by
[IBM's LUW ALTER TABLE reference](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-alter-table).
The offline audit did not execute the malformed output against a server.

The comparator itself is not a perfect gold standard. The same probe finds
PostgreSQL column ALTER output without TYPE, and a NOT NULL statement missing
the column identifier; direct View ALTER resolves EmptySqlFactory. These are
PostgreSQL/shared-path findings, not behavior to copy into Db2. PostgreSQL's
dedicated routine factory does generate FUNCTION comments and explicitly rejects
body replacement without verified catalog DDL; that is a useful safety contrast.

## Required remaining packages

| Package | Bounded work | Acceptance evidence |
| --- | --- | --- |
| P1: ordinary table/column ALTER (B1) | DEFAULT add/change/drop, NOT NULL add/drop, supported type/length changes, table/column comments and their removal; explicit handling of unsupported column attributes | Exact native clauses; direct Table and nested Schema/Catalog diffs; live reread on both targets; existing rows preserved. Report required REORG/pending states rather than silently pretending type changes are immediately usable; do not add implicit backfill |
| P2: routine changes and no-op safety (B2) | FUNCTION/PROCEDURE comments and supported changes; native body replacement where identity/header and preservation guarantees can be established, otherwise explicit actionable rejection; registry state routing | No supported meaningful routine diff becomes `[]`; direct and nested entry points agree; live comments/replay and identity/grants checks; unsupported cases fail before a plan is returned |
| P3: closing verification | Re-run the common-case fixture suite and historical resolver/SQL-resource boundary tests; add ordinary FK/table/view dependency recreation and generated DML checks; run applicable existing bulk/tree suites | Native SQL executes, model rereads agree, DML results and existing data are checked; all executed/unexecuted targets reported. Re-run the offline audit so B1/B2 observations have no unexplained silent outcome |

P1 and P2 are implementation work; P3 is a verification package and can expose
additional concrete fixes. This is a planning estimate of **three work packages,
not three chat turns or a reliable person-day estimate**. It is smaller and more
bounded than the old all-vendor backlog. No numerical completion percentage is
justified by test counts or factory-file counts.

Existing relevant verification includes
[Db2BulkInsertTest](../../sqlapp-core-dialect-test/src/dockerTest/java/com/sqlapp/data/db/dialect/test/db2/Db2BulkInsertTest.java),
[Db2BulkUpsertTest](../../sqlapp-core-dialect-test/src/dockerTest/java/com/sqlapp/data/db/dialect/test/db2/Db2BulkUpsertTest.java)
and [Db2JdbcTreeDataSessionTest](../../sqlapp-core-dialect-test/src/dockerTest/java/com/sqlapp/data/db/dialect/test/db2/Db2JdbcTreeDataSessionTest.java).
Their existence is not a claim that they were executed during this audit or that
their bulk-loader path validates every generic SQL factory.

## Capability inventory

This inventory distinguishes implementation/selected evidence from exhaustive
server conformance. Prior unit/live evidence is catalogued in
[the implementation history](db2-dialect-parity.md).

| Shared capability | Current Db2 status | Remaining classification |
| --- | --- | --- |
| Tables/columns, native data types, identity/generated fields | Versioned readers and native CREATE factories; representative metadata and XML/DDL fixtures | Ordinary ALTER is P1; broad field permutations remain validation, not an absent-reader claim |
| PK/unique/check/FK | Dedicated metadata readers, shared/native generation, representative composite/FK reads | Ordinary dependent-object recreation belongs in P3 |
| Ordinary indexes | Metadata/native recreation, compression/comment ALTER with data-preservation fixture | Supported scope is implemented; uncommon XML/physical options are vendor expansion |
| Views and MQTs | Catalog statement/DDL, view columns and native recreation; view comments; unsupported body change explicitly rejected | Explicit replacement plans retained; do not equate rejection with silent loss |
| Sequences | Configuration/native ALTER, allocation-state separation and live progression/recreation | Implemented documented scope |
| Functions/procedures | Dedicated readers, args/return handling, creation context, SPECIFIC binding and replay | Modification routing/comments/body handling is P2 |
| Triggers | Native definition/context replay and explicit change boundaries | Broad trigger ALTER is additional work unless a common-case regression is demonstrated |
| Roles and object/column/routine/schema/database grants | Typed readers/plans; live grants, specific methods and distinct future scopes | Supported scopes implemented; LBAC/instance authorities are separate vendor expansion |
| ROW/distinct/array/cursor types | Canonical Type/Domain recreation and mixed dependency order | Structured/anchored/opaque cases retain explicit definitions/plans |
| Schema/Catalog composition | Object recreation, typed metadata, RCAC/grants composition; acyclic whole-schema type dependencies | Ordinary dependency regression is P3; arbitrary SQL-body/interleaved graph planning is advanced work |
| Comments | CREATE preservation and several native comment ALTER paths | Table/column and routine modification gaps are B1/B2 |
| DML/identity/returning | Registry paths exist; unit/bulk/tree verification sources exist | Dispatch is not execution evidence; closing checks are P3 |
| Temporary tables, truncate and locks | Dedicated Db2 factories and targeted unit tests | Include applicable common-case checks in P3; not identified as absent implementations |
| Quoting/case and versions | Named components, cursor/module qualification and resolver boundaries have targeted tests | Preserve ordinary quoted identifiers and older reader routes; old-server live runs remain unexecuted unless separately run |

## Separate expansion backlog

Keep these visible, but do not count all of them as prerequisites for ordinary
shared-feature parity: PL/SQL package completeness; wider module declaration/
anchoring graphs; structured types and module/global reverse type dependencies;
interleaved cross-schema planning; uncommon XML/text/physical index forms;
complex federation and external credential repositories; LBAC labels/policies;
instance authorities and vendor-specific administrative objects.

PostgreSQL-only operators/operator classes, exclusion constraints, rewrite rules,
extensions, publications/subscriptions and PG-version SQL additions do not become
Db2 requirements merely because there are PostgreSQL files for them. Where an
equivalent Db2 feature is requested later, define its native scope explicitly.

## What ran in this audit

The offline task compiled and completed, writing 64 observations. While extending
it, compilation initially used a schema-only type for Schema/Catalog and then a
package-private base class; both were corrected to the existing public DbObject
interface. No production dialect behavior was changed or defect fixed here.

The previously executed results remain 345 Db2 unit tests and fourteen real
tests across 11.5.8/12.1.5. They are **previous validation**, not new runs in this
audit and not proof that B1/B2 work. No database was opened, no container started,
and no production data/settings or dependency versions changed. The TSV is an
intentional generated documentation artifact and is reproducible with the task.

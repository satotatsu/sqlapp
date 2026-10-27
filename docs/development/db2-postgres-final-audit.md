# Db2 / PostgreSQL parity audit

Audit and remediation date: 2026-10-27. The comparison uses sqlapp's current Db2_1215 and Postgres180 dialects,
Java 25, their inherited factories and shared Schema model. Existing Db2 live
evidence covers disposable LUW 11.5.8.0 and 12.1.5.0; historical readers remain
subject to the existing compatibility requirement.

## Conclusion and completion boundary

The audit identified **two implementation packages and one verification
package**, below. P1/P2 are now implemented with the explicit supported boundaries
described below; P3 covers the final executed regressions. The prior completion checklist mixed these with a much larger
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

### Reproduced blockers and remediation

| ID | Reproduction | Current result after remediation | Owning implementation |
| --- | --- | --- | --- |
| B1 | Db2 `column.default.add/drop`, `column.notNull.add`, `column.type.widen` | Native SET/DROP DEFAULT, SET/DROP NOT NULL and SET DATA TYPE; explicit maintenance notes; unsupported conversions rejected | [Db2ColumnAlterSql](../../sqlapp-core-db2/src/main/java/com/sqlapp/data/db/dialect/db2/sql/Db2ColumnAlterSql.java), Db2AlterTableFactory |
| B1 | Db2 `column.comment` and `table.comment` | Native COMMENT ON; removal uses `IS ''`, verified on both targets | Db2AlterTableFactory, Db2AlterCommentSql |
| B2 | Db2 `function.comment`, `function.body`, `procedure.comment` | SPECIFIC comments generated; the audit's unverified statement-only model remains rejected; real SQL bodies with retained catalog templates or complete DDL support verified unchanged-header replacement | Db2AlterFunctionFactory, Db2AlterProcedureFactory |
| B2 | Db2 `schema.function.comment`, `catalog.function.comment` | Both generate the same native SPECIFIC comment; explicit Db2 registry version survives detached model differences | Db2SqlFactoryRegistry, Db2AlterSchemaFactory, Db2AlterCatalogFactory |

The original column output conflicted with the native ALTER forms: `SET DATA TYPE`,
`SET/DROP DEFAULT`, and `SET/DROP NOT NULL`. This grammar finding is supported by
[IBM's LUW ALTER TABLE reference](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-alter-table).
The offline audit does not execute SQL. Separate live fixtures now execute the
replacement clauses and reread metadata and populated rows. The first live run
also exposed `IS NULL` comment removal as invalid; all shared native comment
removals now use a character literal, matching [IBM's COMMENT grammar](https://www.ibm.com/docs/en/db2-as-a-service?topic=statements-comment).

The comparator itself is not a perfect gold standard. The same probe finds
PostgreSQL column ALTER output without TYPE, and a NOT NULL statement missing
the column identifier; direct View ALTER resolves EmptySqlFactory. These are
PostgreSQL/shared-path findings, not behavior to copy into Db2. PostgreSQL's
dedicated routine factory does generate FUNCTION comments and explicitly rejects
body replacement without verified catalog DDL; that is a useful safety contrast.

## Completion packages and evidence

| Package | Bounded work | Acceptance evidence |
| --- | --- | --- |
| P1: ordinary table/column ALTER (B1) | DEFAULT add/change/drop, NOT NULL add/drop, supported type/length changes, table/column comments and their removal; explicit handling of unsupported column attributes | Exact native clauses; direct Table and nested Schema/Catalog diffs; live reread on both targets; existing rows preserved. Report required REORG/pending states rather than silently pretending type changes are immediately usable; do not add implicit backfill |
| P2: routine changes and no-op safety (B2) | FUNCTION/PROCEDURE comments and supported changes; native body replacement where identity/header and preservation guarantees can be established, otherwise explicit actionable rejection; registry state routing | No supported meaningful routine diff becomes `[]`; direct and nested entry points agree; live comments/replay and identity/grants checks; unsupported cases fail before a plan is returned |
| P3: closing verification | Re-run the common-case fixture suite and historical resolver/SQL-resource boundary tests; add ordinary FK/table/view dependency recreation and generated DML checks; run applicable existing bulk/tree suites | Native SQL executes, model rereads agree, DML results and existing data are checked; all executed/unexecuted targets reported. Re-run the offline audit so B1/B2 observations have no unexplained silent outcome |

P1/P2 are implemented for Db2 LUW 11.1 and later, inherited by the current
11.5/12.1 routes. Historical factory registrations remain on their previous paths.
The continuation also adds native SQL routine body replacement from complete
matching CREATE DDL with preserved creation context; unsupported or changed
headers remain explicit errors. The two live targets check grants, SPECIFIC
names, untouched overload behavior and dependent-view revalidation.
Default SQL-body reads also retain the fetched native header as supplemental
metadata, allowing statement-only edits after XML round-trip when both source
identity and body can be verified. Independent snapshots validate both headers;
missing/corrupt templates remain explicit errors. The original offline body
probe intentionally has no catalog template and therefore remains rejected.
Native unqualified declaration names are now accepted when the retained creation
schema proves their model identity. The replacement declaration is explicitly
quoted/qualified; body text and its original SQL PATH are preserved. Ambiguous
or mismatched creation schemas still require an explicit plan.
The supported contract and actual executed commands are recorded in
[the completion gates](db2-dialect-completion.md). No numerical completion
percentage is justified by test counts or factory-file counts.

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
| Tables/columns, native data types, identity/generated fields | Versioned readers/CREATE; native ordinary ALTER with explicit conversion boundaries | Broad field permutations and historical live targets remain unverified |
| PK/unique/check/FK | Dedicated metadata readers, shared/native generation, representative composite/FK reads | Ordinary dependent-object recreation belongs in P3 |
| Ordinary indexes | Metadata/native recreation, compression/comment ALTER with data-preservation fixture | Supported scope is implemented; uncommon XML/physical options are vendor expansion |
| Views and MQTs | Catalog statement/DDL, view columns and native recreation; view comments; unsupported body change explicitly rejected | Explicit replacement plans retained; do not equate rejection with silent loss |
| Sequences | Configuration/native ALTER, allocation-state separation and live progression/recreation | Implemented documented scope |
| Functions/procedures | Dedicated readers, args/return handling, context, SPECIFIC binding/replay; native comments and verified SQL body replacement from complete DDL | Signature/options changes and unproven DDL require explicit plans |
| Triggers | Native definition/context replay and explicit change boundaries | Broad trigger ALTER is additional work unless a common-case regression is demonstrated |
| Roles and object/column/routine/schema/database grants | Typed readers/plans; live grants, specific methods and distinct future scopes | Supported scopes implemented; LBAC/instance authorities are separate vendor expansion |
| ROW/distinct/array/cursor types | Canonical Type/Domain recreation and mixed dependency order | Structured/anchored/opaque cases retain explicit definitions/plans |
| Schema/Catalog composition | Object recreation, typed metadata, RCAC/grants composition; acyclic whole-schema type dependencies | Ordinary dependency regression is P3; arbitrary SQL-body/interleaved graph planning is advanced work |
| Comments | CREATE preservation; native table/column/routine and other object comment ALTER/removal | Supported B1/B2 paths now covered by unit and live regressions |
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

The offline task was rerun after remediation, writing 64 observations. B1/B2
now generate native statements or explicitly reject unsupported changes; no
probed meaningful Db2 diff returns an unexplained empty plan.
Live verification uses only the explicitly authorized disposable Community
11.5.8/12.1.5 containers. No external/production data, credentials, local settings
or dependency versions were changed. The TSV is an intentional generated
documentation artifact reproducible with the task. Exact run counts and commands
are in the completion record.

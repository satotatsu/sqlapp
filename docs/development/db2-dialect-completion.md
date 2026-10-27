# Db2 dialect completion gates

The target is comparable reliability and coverage for shared Schema features,
plus faithful recreation of Db2-specific objects. It does not mean implementing
PostgreSQL-only SQL constructs in Db2. Passing unit tests alone does not close
the live compatibility gate. See [implementation evidence](db2-dialect-parity.md).

The [2026-10-27 PostgreSQL comparison audit](db2-postgres-final-audit.md) now
separates ordinary shared-feature completion from vendor expansion. Its reproduced
ordinary column ALTER and silent routine-change gaps are now fixed within the
supported scope below. The three mandatory packages P1/P2/P3 are closed by the
2026-10-27 implementation and verification record.
The offline evidence task does not execute SQL or assert parity.

## Current implementation

Supported logical XML indexes retain pattern/type/value-mode metadata and native
recreation; database-managed physical XML indexes are excluded. Conflicting or
unsupported XML forms still require complete DDL.

The DB2 module now has bulk metadata and native recreation coverage for ordinary
tables/columns/constraints/indexes, views/MQTs, routines, triggers, sequences,
aliases, roles/grants, native types, variables, module headers/member inventories and condition/variable/type recreation, federation
snapshots and row/column access control. Coverage is versioned; a modern feature
does not imply support in every historical Db2 release. Complete executable DDL
is the supported fallback for forms whose components cannot be read reliably.

Native ALTER now covers modeled sequence configuration without implicit restart,
regular index compression without replacement, ordinary column defaults,
nullability and supported widening, and comment changes on tables, columns,
functions, procedures, sequences, indexes, views, triggers and aliases.
Structural replacement remains explicit
where preserving dependencies, privileges or execution order is required.

Weak and named-row strong cursor types now round-trip globally and in supported
module dependency chains. Proven generated type-cast functions remain in the
inventory but are recreated by CREATE TYPE rather than duplicated. Routine DDL
read from the catalog now binds an otherwise implicit SPECIFIC identity so grants
continue to target the recreated routine. Specific method grants and future
function/procedure/method scopes now retain their distinct identities.

Global named ROW fields, arrays of named types and strong cursors now share a
dependency order across the Type and Domain collections. Schema and Catalog
creation use that same order, reject cycles/duplicate identities, and preserve
the source models. Global strong cursors may target published module ROW types;
modules precede the global type group. Catalog creation also orders schemas by
these modeled type prerequisites when an acyclic whole-schema order exists.
Type cycles and schemas requiring interleaved generation fail clearly. General
cross-schema dependencies and the reverse direction (module members requiring
global types) remain explicit work.

Published SQL function/procedure prototypes now retain their unimplemented state.
Module recreation emits proven independent declarations first, retains the
dependent member order, then installs
routine bodies with ADD while restoring each routine's schema/path. This supports
published SQL routine cycles without changing private routines into public ones.
Bodies and their original options remain intact; unsupported declaration forms
still require a complete parent definition.
Default/constant expressions also regain the parentheses omitted by native
catalog text, through the shared global/module variable renderer.
Literal-only defaults can precede implemented routine timestamps; defaults with
routine references are also supported when all later implemented public routines
have proven independent declarations (built-in signature types, no parameter
defaults or anchoring). Unproven declaration dependencies still require complete
parent DDL. Procedure
declarations retain result-set counts, NULL-input and COMMIT ON RETURN clauses.

Current verification: Java 25, 367 DB2 unit tests and 35 real database tests passed
with zero failures/errors/skips. Real targets were disposable DB2 Community
11.5.8.0 and 12.1.5.0 containers: twelve metadata/recreation tests on each target,
seven bulk insert/upsert tests on 12.1.5 and four hierarchical JDBC tests on 11.5.8.
Both owned containers were
removed after verification. Common and dialect-test builds now both use Java 25;
the earlier Java-version mismatch no longer applies.

## Completed mandatory gates

| Gate | Implemented scope | Completion evidence |
| --- | --- | --- |
| P1: ordinary table/column ALTER | Native DEFAULT, nullability, supported widening and table/column comment changes/removals; unsupported fields rejected | Direct Table and nested Schema/Catalog tests; both live targets reread correctly; populated rows/default behavior retained; explicit REORG notes |
| P2: routine changes and no-op safety | Native SPECIFIC comments/removals; verified complete-DDL and retained-template statement body replacement; unsupported headers rejected | Comment-only changes retain IDs/times; both body-edit paths retain SPECIFIC names, grants and usable dependants on both live targets |
| P3: closing verification | FK/table/view XML recreation; generated prepared INSERT/UPDATE/DELETE; bulk and hierarchical writes; resolver/resource boundary unit suite | 367 unit and 35 real tests passed; offline evidence has native B1/B2 SQL or explicit errors |

Closing verification exposed an additional ordinary recreation defect: the
generated plan tried to CREATE a constraint's automatic index in reserved SYSIBM.
Modern index metadata now bulk-joins constraint dependencies, preserves the
constraint identity through XML, and lets the logical constraint recreate its
system backing index. Ordinary user indexes remain explicit. Unit and both live
FK/view/DML fixtures cover the fix. There are no added metadata round trips.

This completion is for the documented common shared-feature scope and explicit
unsupported-case contract. It does not certify every object/option or historical
server version.

## Native ALTER contract (Db2 LUW 11.1 and later)

Use the existing registry's ALTER factory and `createDiffSql(original.diff(target))`
for Table, Schema or Catalog. The model is not modified. Native SQL uses quoted
identifiers; explicit Db2 registry version semantics also survive detached diffs
whose model dialect was generic. Existing public APIs/configuration formats and
older resolver routes remain available.

Ordinary columns support DEFAULT add/change/drop, SET/DROP NOT NULL, comments,
rename, and widening SMALLINT → INTEGER → BIGINT. Same-type VARCHAR/NVARCHAR/
VARBINARY lengths may increase with unchanged scale; DECIMAL/NUMERIC precision
may increase with unchanged valid scale and maximum precision 31. Narrowing,
other conversions, generated/identity changes, unsupported column/table attributes
and implicit replacement fail with an actionable error. REORG requirements are
returned as `SqlType.COMMENT` operations; the caller must handle maintenance
explicitly. Plans do not backfill rows or invoke ADMIN_CMD. SET NOT NULL still
requires existing data to satisfy the constraint; server validation is retained.

Routine comments use the resolved schema and SPECIFIC identity, preserving
overloads and existing grants. COMMENT removal uses `IS ''`; Db2 rejects `IS NULL`.
SQL routine body replacement is supported when both models contain complete
CREATE DDL, the resolved routine name and explicit SPECIFIC identity match their
models, and the identifying header/signature/options are unchanged. Change the
authoritative `definition`; optional supplemental `statement` metadata is retained.
Load with `reader.getReaderOptions().setReadDefinition(true)` when necessary.
For ordinary SQL bodies that the default reader can split reliably, editing
`statement` is also supported. Modern readers retain the already fetched native
DDL as UTF-8 Base64 in `specifics.DB2_ROUTINE_DDL_BASE64`; this supplemental
template survives Schema XML without new model fields or extra catalog queries.
Ordinary callers do not need to construct or configure this metadata.

The ALTER factory checks that the original statement exactly matches the body
of its retained template. It reuses that header with the target BEGIN/RETURN body.
When comparing independently read snapshots, both templates must match their own
bodies and have identical identifying headers/options. Corrupt UTF-8/Base64,
stale bodies, conflicting authoritative definitions and modified headers fail
explicitly. Source models and their templates are not mutated. A statement-only
model from older XML without a verified template still requires rereading or
complete authoritative DDL.
The resolved `QUALIFIER` and `FUNC_PATH` metadata must be present and are replayed
before CREATE OR REPLACE. The caller's connection must have the server-required
replacement privileges. The plan reapplies the target comment, including removal.

An unqualified routine name in native DDL is accepted when the retained
`QUALIFIER` exactly matches the model's schema name. The generated replacement
explicitly quotes and qualifies that declaration name. It does not rewrite names
inside the body or change the retained SQL PATH. A different/missing creation
schema is rejected instead of binding the routine to the caller's current schema.
This applies to both authoritative complete definitions and verified statement
templates, including quoted names containing dots or escaped quotes. Explicitly
qualified DDL retains the existing creation-context behavior.

This uses [Db2's native replacement semantics](https://www.ibm.com/docs/en/db2/11.5.x?topic=objects-creating-maintaining-database):
grants survive and dependent objects are invalidated/revalidated. Internal routine
IDs and creation timestamps are not promised to remain unchanged by replacement.
The live fixture checks the stable SPECIFIC names, grants, untouched overload,
updated procedure output and dependent view result. Comment-only changes still
retain the existing IDs/timestamps, as verified separately.

Missing/ambiguous DDL identity, missing creation context, signature/options changes,
external/wrapped routines, CLP scripts, module routines and simultaneous independent
model-header changes require an explicit native plan. Body forms that the metadata
reader cannot split reliably retain their authoritative complete DDL path. Model-only
`createSql` on a routine ALTER factory fails and directs callers to `createDiffSql`.

## Executed commands (2026-10-27)

The subsequent default-reader/statement continuation reran `Db2RoutineAlterTest`
and `Db2RoutineReplayTest`, the full unit module (367 tests), the full `db2.*`
Docker suite (35 tests), and the offline comparison task (64 observations).
All executed tests passed without failures/errors/skips. The new live pair uses
default body reading and an XML round-trip before editing a compiled SQL function
and procedure through Catalog ALTER. Grants, SPECIFIC names, comments, another
overload and the dependent view remain usable with the new bodies. Supplemental
DDL is derived from an already selected catalog column; no SQL resource or extra
query was added. A unit boundary confirms Db2 10.5 retains its previous behavior;
historical-server live runs remain unexecuted. The TSV was intentionally
regenerated and the two owned containers removed after verification.

The SQL routine replacement continuation reran the focused `Db2RoutineAlterTest`
class, then the full Db2 unit module (363 tests) and the entire `db2.*` Docker
suite (33 tests). All passed with no failures/errors/skips. Both new live
replacement tests use a disposable schema and verify the native CREATE OR REPLACE
operations through Catalog composition. The existing direct-object and Schema
routes remain covered by unit/comment regressions. The two owned containers were
removed afterward. Only `sqlapp-core-db2`, `sqlapp-core-dialect-test` and these
development documents changed for this continuation; no shared API, configuration
format, dependency version, historical metadata query or external database changed.

```powershell
./gradlew.bat :sqlapp-core-db2:test --console=plain
./gradlew.bat :sqlapp-core-db2:test :sqlapp-core-dialect-test:dockerTest --tests 'com.sqlapp.data.db.dialect.test.db2.*' -PtestcontainersReuse=true --console=plain
./gradlew.bat :sqlapp-core-dialect-test:db2PostgresAudit --console=plain
```

Focused ordinary-column, routine and FK/DML tests ran first. During development,
the nested-dialect test initially failed, COMMENT removal failed live with -104,
and automatic backing-index recreation failed with -553; those defects were
fixed and the full suites above passed. A test-only call to a nonexistent Options
setter also failed compilation and was replaced with the existing schema replay
API. The audit TSV was intentionally regenerated (64 observations). No build
output was manually edited. No dependency versions or module dependencies changed.

PL/SQL completeness, structured/anchored types, interleaved/general dependency
graphs, exotic XML/physical indexes, complex federation, LBAC and instance
authorities remain in the [separate expansion backlog](db2-postgres-final-audit.md#separate-expansion-backlog).
They were previously mixed into the mandatory checklist. Explicit native-DDL
fallbacks and unsupported-case errors remain part of the documented contract.

## Validation boundary

Unit tests use synthetic catalog rows and Schema models. The user authorized real
database verification for this continuation. The selected Docker suites actually
executed against owned 11.5.8/12.1.5 instances; no external or production database
was accessed. Earlier sections in the implementation history that say
"compile-only" describe their historical validation state.

The ALTER live fixture uses an isolated schema, checks generated SQL contains no
implicit CREATE/DROP/RESTART, rereads configuration/comments, and checks sequence
progress after changing increment/cache/order. Index compression changes only
the catalog setting; physical compression still requires a separate REORG and
is not performed by the generated ALTER plan.

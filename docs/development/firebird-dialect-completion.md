# Firebird dialect completion scope

Status: completed for the fixed scope (2026-10-27, Java 25).

## Fixed completion criteria

The PostgreSQL comparison concerns the quality of sqlapp's shared Schema model,
versioned readers, executable SQL factories and regression coverage. It does not
require Firebird to acquire PostgreSQL-only database objects. The implementation
scope is Firebird 2.5, 3.0, 4.0 and 5.0 and the object/operation matrix in
[the coverage report](firebird-dialect-parity.md). Older reader behavior is
protected by unit boundaries; native certification of earlier/future engines is
outside this completion scope.

- [x] Version resolution, native types, names/quoting and version-specific catalog columns.
- [x] Bulk metadata reads; no additional per-object/argument/index SELECTs for the added paths.
- [x] Schema XML/clone retention and executable reconstruction of modeled and native objects.
- [x] Declared routine domain/TYPE OF references, constraints, defaults, nullability and text attributes.
- [x] Dependency-ordered Schema CREATE and safe Schema ALTER additions/removals/reference changes.
- [x] Circular foreign-key phases without disabling enforcement or rebuilding unrelated objects.
- [x] In-place routine/native-exception changes, native comments and dependency-checked native removal/replacement.
- [x] Unit coverage for direct named PK/UNIQUE/FK/CHECK DROP, owner resolution and invalid identities.
- [x] Native direct-constraint DROP/recreate/enforcement tests on all four engines.
- [x] Final executed core/command/PostgreSQL/Firebird unit regression and Docker matrix.
- [x] Final verification counts, compatibility notes and removal of owned verification scripts/logs.

The ordinary entry points remain the dialect catalog/schema reader and
`createSqlFactoryRegistry().createSql(schema, SqlType.CREATE)` or the Schema
ALTER factory with `before.diff(after)`. Read user-object exports with
`ReaderOptions.excludeSystemObjects=true`. Type references and dependency
manifests are populated by metadata export; ordinary callers do not have to
construct a planner or provide low-level execution phases.

## Intentional boundaries, not pending automatic migrations

- Routine/body cycles fail with an explicit staged-migration error; temporary
  stub bodies are not inferred. Circular foreign keys are supported separately.
- Ambiguous renames, incompatible signatures/types and destructive conversions
  are not guessed. A surviving dependency can require explicit staging.
- Replacing an in-use collation/filter requires released dependencies; automatic
  dependent rebuilding is outside scope. Missing/unknown dependency graphs are
  not assumed empty.
- Filtered exports require their omitted prerequisites to be installed already.
- External UDF/UDR/filter library deployment/execution certification, ownership
  reassignment, security-database account/password lifecycle and replication
  transport/cutover are separate operational concerns.
- PostgreSQL-specific extensions, policies, FDWs, operators and partitions have
  no direct Firebird parity requirement.

Completion is assessed against the acceptance checks above within these
documented boundaries.

## API and compatibility

Existing public method signatures are retained. Vendor additions include typed
native-object/publication/dependency manifests and `FirebirdRoutineTypeReference`.
The shared Schema XML structure remains unchanged; new specifics carry the
additional information. An old export cannot recover a discarded reference, and
new enriched exports require the updated Firebird dialect for correct restoration.

Named constraint DROP emits `ALTER TABLE <owner> DROP CONSTRAINT <name>` for
PRIMARY KEY, UNIQUE, FOREIGN KEY and CHECK. It needs only a constraint name and
an owning table object/name, including a column CHECK's owner. It does not need
a duplicate CREATE definition. Firebird has no schema-qualified constraint/table
identity, so logical schema labels are not emitted. Missing names and NUL
identifiers fail before SQL is returned. No CASCADE is inferred; referenced
keys remain protected by the database. The existing foreign-key DROP factory
class/API remains available and uses the same implementation.

## Verification

### Follow-up: legacy external functions (2026-10-27)

The function DROP factory distinguishes legacy `DECLARE EXTERNAL FUNCTION`
declarations from 3.0+ PSQL/UDR functions. Legacy declarations use
`DROP EXTERNAL FUNCTION`, including Schema ALTER removals; modern functions
retain `DROP FUNCTION`. Firebird 2.5 function comments use `COMMENT ON EXTERNAL
FUNCTION`, including declarations represented only by their name.

`createSql(before.diff(after), SqlType.ALTER)` supports legacy entry-point,
module-name and comment changes through `ALTER EXTERNAL FUNCTION`, retaining
the function identity and callers. It rejects renames, signature changes,
legacy/modern conversion, unsupported specifics and inconsistent changed
definition caches. Public method signatures and Schema XML are unchanged.
No new metadata queries or module dependencies are introduced.

Legacy text return declarations retain `FunctionReturning.characterSet` in
CREATE, including CSTRING, VARCHAR and CHAR. Previously only input argument
character sets were emitted, so the reader's cached declaration and model-based
restoration could lose an explicitly declared return character set. The argument
reader also now applies charset/collation after type-name resolution, which can
reset these attributes, and uses the existing catalog character length instead
of the encoded byte length for text declarations. CSTRING retains its logical
VARCHAR type after setting its vendor type name. These changes prevent UTF8
declarations from growing on repeated reconstruction. SQL-generation changes
belong to the legacy CREATE factory and metadata changes to the argument reader;
public APIs and catalog queries are unchanged. Regression fixtures declare UTF8 input/return
values, read metadata, retain it through Schema XML and recreate from modeled
components with the cached definition cleared. They compare the reread return
type, length and character set. Fixtures never invoke the missing external
library. The syntax follows the official
[external-function grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-extfunc.html).

During this follow-up, all four reconstruction cases passed but the separate
JDBC-tree fixture stalled in the initial connection handshake. Its worker was
terminated after a thread dump confirmed that it had not entered dialect code.
The fixture now waits for the image health check, uses the Testcontainers host
and sets `connectTimeout=30` (seconds) and `soTimeout=60000` (milliseconds).
The isolated JDBC-tree rerun passed in 59 seconds. These are test-only settings;
application connection defaults are unchanged. Timeout units follow the
[Jaybird manual](https://firebirdsql.github.io/jaybird-manual/jaybird_manual.html).

The final charset follow-up verified 243 Firebird unit tests and 13 Docker
compatibility tests, with zero failures/errors/skips. The focused metadata and
SQL lifecycle classes passed before the full unit suite; the final full Docker
rerun completed in 6 minutes 6 seconds using Java 25, the repository wrapper and
`:sqlapp-core-dialect-test:firebirdCompatibilityTest --offline --no-parallel`.
The unit suite executed after the production changes; the final rerun reused
those unchanged unit results while executing all Docker tests. Build outputs
and the project cache were isolated in the owned temporary verification folder.
Shared core/command/PostgreSQL suites were not rerun for this dialect-only
change. Only dedicated disposable local databases were used. No application
connection settings, dependency versions or repository-generated files changed.

Modeled legacy CREATE declarations now reject negative/out-of-range return
parameter positions, invalid names, NUL-containing module/entry-point literals
and missing/nonpositive CSTRING byte lengths. A negative return position must
not silently select the scalar-return branch. Zero still denotes a separate
return value; positive positions are one-based and refer to an existing argument.
Complete caller-supplied definitions remain authoritative and do not require
duplicated supplemental fields. This validation changes invalid-input behavior
only, without changing public signatures or metadata retrieval.

The validation follow-up passed its focused lifecycle class, then 241 Firebird
unit tests and 13 Docker compatibility tests, with no failures/errors/skips.
Java 25 and the wrapper ran
`:sqlapp-core-dialect-test:firebirdCompatibilityTest --offline --no-parallel`
with temporary output/cache isolation (5 minutes 44 seconds). Valid declaration,
metadata, XML recreation and ALTER paths passed on disposable local Firebird
2.5/3.0/4.0/5.0 containers. Shared core/command/PostgreSQL suites were not rerun
for this dialect-only validation change; production/external databases were
not accessed and no dependency or generated repository files were changed.

The subsequent reconstruction audit also unified CREATE's legacy classification
with DROP/ALTER/comment generation. A complete `DECLARE EXTERNAL FUNCTION`
definition suffices without a duplicated `firebird.udf.module` marker. Its
supplemental argument annotations do not generate modern `COMMENT ON PARAMETER`
statements, which are unavailable for legacy UDF arguments. Native coverage
removes the duplicated function specifics before reconstruction and retains an
argument remark to exercise this boundary on all four supported engines.

This reconstruction follow-up passed the focused lifecycle class first, then
the full Firebird unit suite (238 tests) and Docker compatibility suite (13 tests;
2.5/3.0/4.0/5.0), with zero failures, errors or skips. The Java 25 wrapper command
was `:sqlapp-core-dialect-test:firebirdCompatibilityTest --offline --no-parallel`
with isolated temporary outputs; it completed in 6 minutes 40 seconds. No shared
modules changed, so their suites were not rerun. Only disposable local containers
were used; no production/external database or library deployment was involved.

The native regression covers metadata-read snapshots, forward/reverse Schema
ALTER, stable function identity, XML reconstruction and factory DROP on all
four engines. Firebird 2.5/3.0 also check a bundled executable UDF and its PSQL
caller. Missing-library declarations on 4.0/5.0 are never executed; external
library installation remains outside scope. Syntax follows the official
[2.5 external-function reference](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref25/fblangref25-ddl-extfunc.html)
and [5.0 external-function reference](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-extfunc.html).

The legacy-UDF follow-up executed `:sqlapp-core-firebird:test` (237 tests,
zero failures/errors/skips) and `:sqlapp-core-dialect-test:firebirdCompatibilityTest`
(13 Docker tests, zero failures/errors/skips) with Java 25 and the wrapper,
`--offline --no-parallel`. Focused lifecycle/comment classes passed first.
The final Docker rerun completed in 5 minutes 47 seconds. During verification,
an obsolete comment expectation was corrected and the test-only identity check
was changed from the 3.0+ `RDB$FUNCTION_ID` column to `RDB$DB_KEY` for 2.5
compatibility. Shared core/command/PostgreSQL suites were not rerun for this
dialect-only follow-up; their earlier results below remain separately recorded.

### Fixed-scope completion run

Focused class: `FirebirdConstraintLifecycleTest`, followed by
`FirebirdSchemaForeignKeyOrderTest` and `FirebirdConstraintValidationSqlTest`.
The final Java 25 wrapper tasks are:

```text
:sqlapp-core:test :sqlapp-command:test :sqlapp-core-postgres:test
:sqlapp-core-dialect-test:firebirdCompatibilityTest --offline --no-parallel
```

The final run forces these unit suites to execute with a temporary initialization
script, while isolating build outputs and the project cache in an owned temporary
directory. Repository build settings and dependency versions are not changed.
Docker checks use dedicated disposable 2.5/3/4/5 containers only. No external or
production database is a target.

The final run completed successfully in 6 minutes 36 seconds. All four unit test
tasks executed, rather than reusing up-to-date test results. XML results:

| Task/module | Tests | Failures/errors | Skipped |
|---|---:|---:|---:|
| sqlapp-core | 1,232 | 0 | 0 |
| sqlapp-command | 627 | 0 | 0 |
| sqlapp-core-postgres | 305 | 0 | 0 |
| sqlapp-core-firebird | 231 | 0 | 0 |
| firebirdCompatibilityTest (Docker) | 13 | 0 | 0 |

This is 2,395 unit tests plus 13 Docker tests. The native 2.5/3/4/5 cases now
round-trip named constraints through XML, reject referenced-primary-key DROP,
drop FK/PK/UNIQUE/CHECK using their public factories, demonstrate that the
constraints are absent, recreate them and verify uniqueness/check/referential
enforcement again. No cascading DROP or table/column reconstruction is inferred.

Focused tests first reproduced invalid generic `DROP UNIQUECONSTRAINT` and
`DROP CHECKCONSTRAINT` output and missing owner/identifier validation. These
paths were fixed in the Firebird module. Fixture-constructor and temporary
included-build configuration errors were corrected before the successful final
run. No requested verification remains unexecuted within this scope. Dedicated
temporary reports are retained; owned root verification scripts/logs were removed.
There are no intentional generated-file changes in the repository, dependency
updates or production-data changes from this close-out.

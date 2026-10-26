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

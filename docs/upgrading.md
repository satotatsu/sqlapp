# Upgrading sqlapp

Upgrade all sqlapp modules as one tested unit. A change in the Schema model,
metadata reader, dialect, command layer, or Gradle task can affect generated
XML and SQL even when application source still compiles.

This guide describes consumer-side verification. It does not require a live
production database and does not replace database-vendor upgrade guidance.

## 1. Record the current inputs

Before changing versions, record:

- the current sqlapp plugin and module version;
- Java and Gradle or Maven versions;
- the database product and exact server version;
- the JDBC driver coordinate and resolved version;
- the resolved sqlapp dialect class;
- relevant task or command configuration; and
- a reviewed Schema XML snapshot from the current version.

Keep credentials and row data out of the record. For a metadata-only baseline,
export with `dumpRows = false`. The Schema snapshot captures what the current
metadata reader understood, not just what dependencies were declared.

## 2. Align every sqlapp version

Use one version for the Gradle plugin, core, command, renderer, test support,
and dialect artifacts.

```groovy
plugins {
    id 'com.sqlapp.db' version '0.80.0'
}

def sqlappVersion = '0.80.0'

dependencies {
    implementation "com.sqlapp:sqlapp-core:${sqlappVersion}"
    runtimeOnly "com.sqlapp:sqlapp-core-postgres:${sqlappVersion}"
}
```

Gradle's `plugins` block requires a literal or settings-provided plugin version,
so keep it visibly aligned with the dependency property.

```xml
<properties>
  <sqlapp.version>0.80.0</sqlapp.version>
</properties>
```

Use `${sqlapp.version}` for every Maven sqlapp dependency. sqlapp does not
currently provide a BOM, so a shared property prevents an old dialect or
command JAR from remaining on the runtime classpath.

## 3. Keep the JDBC driver decision explicit

Declare the driver directly instead of letting a dialect's current transitive
dependencies silently choose it. Decide whether to upgrade the driver in the
same change.

For a sensitive database, consider verifying separately:

1. new sqlapp with the existing driver;
2. existing sqlapp with the new driver; and
3. the final combined versions.

The [compatibility matrix](compatibility.md) records versions used by repository
tests. It is evidence, not an automatic guarantee for another environment.

## 4. Inspect dependency resolution

Gradle:

```shell
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency sqlapp-core --configuration runtimeClasspath
```

Maven:

```shell
./mvnw dependency:tree "-Dincludes=com.sqlapp:*"
```

Confirm that one sqlapp version is selected and that the intended dialect and
driver are present. Check the packaged application as well as the compile
classpath; dialects are discovered at runtime through service descriptors.

## 5. Verify without changing data

Start with read-only or file-only operations:

1. compile the build configuration and resolve dependencies;
2. connect to a disposable or authorized non-production database;
3. confirm the resolved dialect;
4. export metadata with `dumpRows = false`; and
5. generate HTML or SQL from saved XML without executing it.

Do not begin verification with migration, SQL execution, import, repair, or
bulk-load tasks.

## 6. Compare Schema XML

Export the same database with old and new versions using identical filters.
Keep the outputs separate, for example `verification/old/Catalog.xml` and
`verification/new/Catalog.xml`.

Review changes in:

- catalog, schema, table, and column identity;
- type, length, precision, scale, and nullability;
- primary, unique, foreign-key, and check constraints;
- indexes, sequences, defaults, and generated values;
- views, routines, and dialect-specific objects; and
- product metadata used for offline dialect selection.

Formatting or ordering can create a large textual diff. Use schema comparison
or targeted object inspection to separate semantic changes from serialization
order. A metadata improvement may legitimately change representation; verify
that the new model can document or recreate the intended object correctly.

## 7. Review generated SQL

Generate SQL into a new empty directory. Compare every `SqlOperation`, including
statements before or after the main table DDL.

Check identifier quoting, type mapping, defaults, generated values,
identity/sequence behavior, constraints, indexes, vendor clauses, transaction
operations, and statements absent because a dialect has no factory.

Apply the result only to a disposable database first, then read its metadata
back and compare it with the intended model.

## 8. Verify database-changing workflows

For migrations, imports, synchronization, loaders, and bulk operations, test
both success and forced failure. Confirm:

- transaction boundaries and partial commits;
- retry and resume behavior;
- checkpoint, lease, fingerprint, and approval validation;
- rollback or restoration behavior;
- idempotency of an intentional rerun; and
- reports after success and failure.

Do not copy old checkpoints or approvals into a new-version test unless their
compatibility is what the test is intended to prove.

## 9. Update reviewed baselines deliberately

Replace committed Schema XML, generated SQL, or approval artifacts only after
their semantic changes are understood. Keep the old artifact in source history
for review.

Do not automatically feed a fresh metadata export into a production migration.
Keep fresh metadata, reviewed desired state, generated plan, and execution
approval as separate stages.

## Rollback plan

Retain the previous pinned coordinates, reviewed XML and SQL, application
artifact, migration reports, and database backup/restore procedure. Rolling
back application dependencies does not reverse DDL or migrated data; database
rollback must be designed and verified separately.

## Upgrade checklist

- [ ] Java and build-tool versions satisfy the new baseline.
- [ ] All sqlapp artifacts resolve to one version.
- [ ] The JDBC driver version is explicit.
- [ ] The expected dialect resolves from target server metadata.
- [ ] Old and new Schema XML were compared.
- [ ] Generated SQL was reviewed on a disposable database.
- [ ] Outputs were written to separate directories.
- [ ] Success, failure, and rerun behavior were tested.
- [ ] Credentials and production row data are absent from artifacts.
- [ ] Application and database rollback procedures are ready.

See [Published artifacts](artifacts.md), [Compatibility](compatibility.md), and
[Logging and diagnostics](logging-and-diagnostics.md) for supporting details.

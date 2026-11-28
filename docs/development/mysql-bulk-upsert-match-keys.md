# MySQL-family Bulk UPSERT match keys

## Fix

MySqlBulkUpsertExecutor previously used ON DUPLICATE KEY UPDATE. MySQL can
select the row to update through any PRIMARY/UNIQUE index, regardless of the
BulkUpsertOption.keyColumns contract. For example, with PK(id), UNIQUE(email),
an incoming (id=3,email='a') could update the existing id=1 row owning email='a'.
Insert-only mode also silently ignored unrelated unique conflicts.

The executor now stages rows, classifies matches using the resolved keyColumns
(primary key by default), updates matched rows by those keys and inserts the
previously unmatched rows with ordinary INSERT. PRIMARY/UNIQUE conflicts on
other keys raise SQLException. No IGNORE, REPLACE or ON DUPLICATE KEY UPDATE is
used. The change applies through the existing MariaDB and Aurora MySQL providers.
The catalog/schema target-name resolution from the previous fix is preserved.

An internal staging-only TINYINT NOT NULL DEFAULT 0 column freezes classification
before target DML. Its name avoids user-column collisions, case insensitively.
The default is explicit because MariaDB CTAS does not supply a default for a
selected numeric literal: the first real-DB run reproduced warning 1364 before
this was corrected. The internal field never enters the source Schema model or
user configuration and its update count is excluded from the result.

A concurrent writer after classification cannot redirect an insert to an UPDATE
or silently skip it: a duplicate target key is an error. Lock waits/deadlocks
remain possible. Tests inject a committed writer after classification at READ
COMMITTED, both for a different UNIQUE constraint and for the same match key.
These checks are specific interleavings, not a claim of universal serializability.

## Compatibility and recovery

Public API, options/defaults, source duplicate-key strategy and key equality (`=`)
are unchanged. NULL keys do not match NULL. Update-only uses the existing join;
insert-only skips initially matched rows without firing a no-op UPDATE. Key-only
upserts need no UPDATE assignment. No MySQL-8-only syntax, CTE, row aliases or
new resolver/version boundary is introduced.

The returned long is the sum of target UPDATE and INSERT JDBC counts. A changed
row normally contributes 1 rather than ON DUPLICATE KEY UPDATE's 2. Unchanged
UPDATE counts still follow Connector/J's affected/found-rows setting. Applications
must not interpret this count as a stable number of processed source records.
Trigger behavior follows ordinary UPDATE/INSERT, rather than INSERT followed by
an ON DUPLICATE KEY UPDATE/no-op.

The existing transaction scope remains responsible for commit/rollback. With
useTransaction=true (default) and initial autocommit, an owned InnoDB transaction
rolls back a successful UPDATE if the later INSERT conflicts. An existing caller
transaction is neither committed nor rolled back: the caller must roll it back
after an error if whole-operation atomicity is required. With autocommit and
useTransaction=false, earlier target updates may already be committed when INSERT
fails. Nontransactional engines/trigger side effects cannot be undone. Retry only
after establishing the outcome. Shared transaction-scope recovery after a rollback
or commit failure is unchanged; this patch does not claim recovery guarantees for
those failures. Use transactional target tables and coordinate destructive DDL.

The existing Java source duplicate-key comparer is unchanged; differences between
Java equality and database collation equality remain a separate limitation.
No changes to BooleanConverter, TIMESTAMP metadata or normal MERGE factories.

## Tests and real databases

Three initial SQL regression tests failed against the old implementation. Added
coverage includes explicit match keys, action flags, composite/key-only keys,
rollback after insert conflict, caller ownership, stopping after update conflict,
internal marker collisions/defaults and existing catalog-qualified names across
MySQL 5.1/5.6/5.7/8.0/8.4/9 resolver branches.

All 511 module tests passed: MySQL 377, MariaDB 109, Aurora MySQL 25.

Commands (Java 25, Gradle Wrapper, offline):

```
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlBulkUpsertTargetNameTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test :sqlapp-core-aurora-mysql:test --offline --console=plain
```

Disposable local DB probes cover MySQL 5.7.44/8.0.46/8.4.11 and MariaDB
10.5.29/11.4.12/11.8.8: unrelated UNIQUE and primary-key collisions, explicitly
selected keys, mixed/update-only/insert-only actions, owned/caller rollback,
and concurrent same-key/other-UNIQUE writers. Aurora dialects run on upstream
MySQL 8.0/8.4; a real AWS Aurora cluster is unavailable. Older supported releases
and MySQL 9 lack local DB images and are SQL-generation tested only.
No external/production DB or persistent volume is used; containers are removed.

Reproduction probe source, Java argument files, container runner and per-version
logs are in:
`C:/Users/satot/.codex/visualizations/2026/11/17/01a1e752-547f-7360-b6ee-49e6314aacb1/mysql-upsert-keys/`.

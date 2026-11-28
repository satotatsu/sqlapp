# Informix dialect

## Server metadata, sequence batches and staged replacement (2026-11-20)

The standard Catalog reader now includes server storage spaces from
`sysmaster:"informix".sysdbspaces` and configuration from
`sysmaster:"informix".sysconfig`. Each reader uses one query for the selected set.
No additional mandatory options are required; the existing table-space/setting
name filters remain available. Queries require SELECT access to those views and
propagate permission/query failures.

Storage spaces retain owner, numeric ID and native flags. `INFORMIX_SPACE_KIND`
distinguishes DBSPACE/BLOBSPACE/SBSPACE; `INFORMIX_SPACE_FLAGS` preserves all bits.
`INFORMIX_PAGE_SIZE` is read only on Informix 10 and later, so the older/unknown
version query does not reference the newer column. Physical chunk paths and
administrative storage-creation commands are not inferred. Configuration uses
`Setting.value` for the effective value, `defaultValue` for the server default,
and `INFORMIX_BOOT_VALUE` for the boot-time value. NULL and empty values remain
distinct, and leading value content is preserved.

`SequenceGenerator` and `SequenceGetter` now work through `SEQUENCE_NEXT_VALUES`:

```java
try (var generator = new SequenceGenerator(connection, dialect,
        new Sequence("ids").setSchemaName("owner"))) {
    var values = generator.get(100);
}
```

The SQL binds one FIRST count and requests NEXTVAL once per returned row. Its
row source is a cross product of ten ten-element collection constructors,
covering all nonnegative Java int counts independently of catalog/table sizes.
No helper routine, sequence alteration or arithmetic reconstruction is used;
actual server-issued values retain increment, cycle and concurrency semantics.
Sequence names resolve in the connected database; catalogName is contextual
metadata, as in other schema-object SQL factories. SERIAL identities are not
advertised as sequence-backed preallocation. The single-value SQL also uses a
collection singleton, avoiding catalog-owner resolution in ANSI databases.

Informix's JDBC PUT transport is enabled by configuring the connection with
`IFX_USEPUT=1`. AUTO retains the ordinary typed batch path, which the driver can
accelerate when PUT is enabled. An explicit `BulkInsertMethod.NATIVE` now checks
the live driver's public IFX_USEPUT getter (including wrapped connections), or
available client-info/URL evidence, before reading rows. An unverified/disabled
setting fails with an actionable error; no connection setting is mutated and no
new driver dependency is added. Existing Schema/JDBC conversions, batch sizing,
timeouts and exception handling are reused. This is the driver's PUT mode, not
a new external-file/HPL process, and no measured throughput improvement is claimed.

An SPI `AtomicTableReplacementProvider` is registered for the existing staged
replacement workflow. It uses transactional DELETE/INSERT, rather than table
renaming or truncation. It requires a logged current database and SERIALIZABLE
support, checks all selected relations in a set query, and emits exclusive table
locks in stable name order. It rejects RAW, external, typed/inherited, replicated,
audited, triggered, missing or otherwise unknown-flag tables, unresolved owners,
cross-database targets, duplicate identities and more than 32 locks per transaction.
The common command retains ownership of commit, rollback, graph validation and
final verification. Database/catalog checks and lock acquisition occur before
replacement DML; this provider never commits or changes the caller's settings.

Unit tests cover SQL generation/binding, XML metadata retention, version boundaries,
query counts, native-mode validation, batch failure propagation and provider
preconditions. The existing embedded-database staged replacement tests are also
run. An opt-in Informix Docker test was added for actual server metadata,
SequenceGenerator/Getter values, PUT inserts, RAW rejection and replacement
rollback. It is compiled but **has not been executed** for this follow-up; actual
11.70/12.10/14.10/15 behavior, rollback under PUT and sequence-query performance
remain live-test requirements. No external database or container was started.

```powershell
.\gradlew.bat :sqlapp-core-informix:test --offline --console=plain
.\gradlew.bat :sqlapp-command:test --tests '*TransferStagedReplacementTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-dialect-test:compileDockerTestJava --offline --console=plain
```

Specification basis: [sysdbspaces](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysdbspaces),
[page-size version boundary](https://publib.boulder.ibm.com/epubs/html/25122930/mig66.htm),
[sysconfig](https://www.ibm.com/docs/ja/informix-servers/12.10.0?topic=tables-sysconfig),
[NEXTVAL per-row behavior](https://help.hcl-software.com/hclinformix/1410/sqs/ids_sqs_1461.html),
[collection-derived tables](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=table-row-type-resulting-collection-derived),
[PUT bulk inserts](https://www.ibm.com/docs/kk/informix-servers/12.10.0?topic=database-perform-bulk-inserts),
[logging and RAW tables](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=storage-table-types-informix).

## ALTER, SQL scripts and authorization metadata (2026-11-20)

The existing Informix 11.70/12.10/14.10/15 resolver selections now use native
`ALTER TABLE ... ADD (...)`, `DROP (...)`, `MODIFY (...)`, `RENAME TABLE` and
`RENAME COLUMN` generation. A combined column type/name change modifies the old
column before renaming it; duplicate unmatched difference entries do not cause
that column to be dropped and recreated. Default values and nullability are
emitted as complete target declarations, including named NOT NULL constraints.

Table metadata reads ROW/PAGE locking and positive next-extent sizes in the
existing batched table query. CREATE TABLE emits the modeled values. Table
ALTER supports changing `INFORMIX_LOCK_MODE` and `INFORMIX_NEXT_SIZE_KB`
to explicit target values. NEXT SIZE affects future extent allocations.
Dbspace relocation, fragmentation, table inheritance, and owner/database moves
require an explicit migration plan and fail with an actionable exception instead
of producing an empty or incomplete ALTER plan. Informix MODIFY can drop keys,
checks and referencing foreign keys. MODIFY on a table containing constraints
other than NOT NULL is therefore rejected until a complete dependency migration
is supplied; the dialect does not infer external dependencies from a table-local
diff. Native index and constraint CREATE factories are reused for ALTER additions.

`registry.createSql(originalCatalog.diff(targetCatalog), SqlType.ALTER)` now plans
constraint dependencies for in-place column-definition changes. Supply **complete
snapshots of the connected database**, including referencing tables in every owner
schema; a filtered Catalog or an isolated Schema/Table is insufficient evidence of
incoming dependencies. Completeness cannot be established from an in-memory model,
and the factory does not query the server to fill missing references.

For the supported path it drops affected incoming/outgoing foreign keys first,
then the changed tables' PRIMARY KEY/UNIQUE/CHECK constraints, composes the existing
Informix table ALTER operations, restores keys/checks, and finally restores foreign
keys. All non-NOT-NULL constraints on changed tables are included conservatively;
the factory does not parse CHECK expressions. Named NOT NULL constraints stay in
the column declarations. Multiple changed tables, cross-owner references, composite
keys and self-references share the same ordered plan. Work uses copied Schema
models and the existing native constraint factories; input snapshots are preserved.

This path requires unchanged table identities, unchanged relative order of
retained columns and owner qualification enabled. Named CHECK constraints can
be added, removed, or have their expressions or modes changed, alone or alongside
column changes. Removed CHECKs are dropped before column changes and new CHECKs
are added afterward. CHECK modes include ENABLED, DISABLED, and both FILTERING
variants. Mode changes recreate the CHECK with its target expression and mode.
Named foreign keys can also be added or removed. Their local columns, referenced
table and columns, mode, and supported ON DELETE rule can be changed. New or changed
foreign keys require an enabled primary or unique constraint on the referenced
columns in target order. The plan drops affected foreign keys before column changes
and restores target foreign keys after restoring the referenced keys.
On tables without column or index changes, only changed CHECKs and foreign keys
are processed unless an incoming foreign key must be rebuilt for a changed table.
PRIMARY KEY and UNIQUE constraints can be added, removed, or replaced. A key
change also rebuilds affected incoming foreign keys, and any retained foreign
key must have an enabled matching key in the target snapshot. Key columns
must resolve in the table. Enabled primary-key columns must be NOT NULL.
Named NOT NULL constraints can be added, removed, renamed, or have their modes
changed on retained columns. A complete MODIFY declaration applies the target
nullability and named constraint together. The enabled constraint must agree
with the target column's nullability. Disabled or filtering backing indexes
still require a separate plan. Rebuilding such an index in enabled mode can
reject data that its original mode allowed. Applying an index mode after
attaching a constraint also requires verifying how Informix handles the shared
index. The planner rejects these cases before returning SQL.
New columns can be inserted or appended in target order, including in a plan
that modifies existing columns or creates indexes on the new columns.
Insertions use Informix's native `ADD (... BEFORE existing_column)` syntax.
Column removal can be combined with independent-index removal and changes
to retained columns. The planner rejects removal when a retained key, index
or modeled privilege still refers to the column. Retained CHECK expressions
must be explicitly rewritten in the target. Partitioning, inheritance,
expression or fragmented indexes, and catalogs containing views,
triggers or routines require a separate removal plan. A column can be renamed
in place when the original and target columns share a nonempty ID that is unique
within each table snapshot and the column stays at the same position. The target
can supply updated key, foreign-key and CHECK definitions for the renamed column.
The same
dependency restrictions as column removal apply. Any type or default change
uses the new name after RENAME COLUMN. Mixed additions and removals that
cannot be matched using those rules are rejected. Reversing a column removal
recreates its definition only.
It rejects table additions/deletions/renames and column reordering,
other catalog/schema/table changes,
unresolved/cross-database references, affected virtual relationships, unnamed
constraints, and backing-index states that cannot be preserved by the rules below.
Broader migration planning remains outstanding.
The returned operations are not executed or made transactional by the factory;
callers must execute the complete list under an appropriate maintenance/transaction
policy. Re-adding enabled constraints validates the existing data on the server.

The dependency SQL targets the existing Informix 11.70–15.x baseline; validation
here is generated-SQL/model coverage, not a live-server compatibility claim.
See the [MODIFY constraint effects](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=ats-modify-clause).

Standalone indexes are retained during Catalog MODIFY rather than dropped and
recreated; Informix rebuilds indexes affected by the column alteration. Unchanged,
explicitly modeled named UNIQUE/PRIMARY KEY backing indexes are now supported too.
The set-based key-index reader includes PRIMARY KEY as well as UNIQUE constraints,
enriching an already-read primary key without replacing its constraint identity.
After all column changes and before restoring keys, the plan emits native
`CREATE UNIQUE INDEX IF NOT EXISTS` for each distinct named backing index. This
keeps an existing index and its attributes, or recreates it with modeled owner,
column order, access method, storage/fragmentation and mode if constraint removal
removed the implementation index. A backing index sharing its constraint's name
is included. Conditional creation is internal to this version-checked dependency
plan; ordinary native index CREATE and existing option defaults remain unchanged.

Conditional backing-index creation requires the original Catalog to identify
Informix **11.70 or later**, including major/minor version; older or unknown versions
fail explicitly on this path. Each backing index must be modeled in Table.indexes,
unique, enabled, have the same ordered key columns as its constraint, and have no
included payload columns or filtering mode. Missing, disabled, nonunique or
incompatible backing indexes are rejected before any operation list is returned.
The key definition must remain unchanged. A named backing index can be renamed
when the original index is removed from the snapshot, the new index is added, and
the constraint references the new name. A competing index on the same key columns
is rejected because the server selects the backing index when adding the key.
On Informix 11.70 and later, a changed named backing index is dropped with
`DROP INDEX IF EXISTS` after dropping the affected constraints, then recreated
with its target definition before restoring the keys. The conditional drop handles
indexes that Informix already removed when dropping their constraints. The
replacement uses unconditional CREATE. As with all snapshot-generated DDL,
the live database must match the original snapshot: IF NOT EXISTS checks existence,
not the definition of an index that has drifted since the snapshot.

These additions have unit/model coverage only. The server's index survival/rebuild
and reuse behavior still require live verification for the full plan. References:
[index rebuild during column alteration](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=definition-in-place-alter),
[existing index reuse by constraints](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=format-adding-primary-key-unique-constraint),
and [11.70 conditional DDL syntax](https://help.hcl-software.com/hclinformix/1410/sqs/ids_sqs_2203.html).
`catalogModifyPreservesNamedPrimaryBackingAndReplacesIndependentIndexes` was added to the
Docker suite to check the actual reader-to-ALTER path, primary-index reuse,
independent-index replacement, default changes and duplicate/orphan rejection in a
disposable logged database. It was compiled but not executed in this follow-up.

Catalog ALTER also supports independent-index definition changes, both alone and
together with column MODIFY. Index additions, removals and name changes are planned
explicitly; owner, key order, storage/access-method options and supported modes
reuse native index DDL. All affected foreign keys and keys/checks are removed
first, changed/deleted indexes are dropped using their original owner, all column
changes are applied, target indexes are created, unchanged named backing indexes
are ensured, and keys/checks/foreign keys are restored. Unchanged independent
indexes stay in place. Index-only changes use this same conservative dependency
plan, so they can also cause constraint revalidation.

Changed independent indexes use unconditional CREATE, so an unexpected existing
index causes an execution error rather than silently keeping an old definition.
Target DISABLED and FILTERING modes are supported for independent indexes.
Included payload columns and incomplete index keys
are explicitly rejected. The complete connected-database snapshot prerequisite
and stable existing table/column identities still apply. Source and target models are
preserved, and the entire SQL list is generated before it is returned.

The dialect's SQL splitter keeps SPL procedures/functions intact through their
matching END PROCEDURE/END FUNCTION, including nested statement blocks and
trailing DOCUMENT clauses. It ignores semicolons and terminators in single/double
quoted text, `--`, `/* ... */` and `{ ... }` comments. External routines remain
ordinary statements. Incomplete routines, quotes and comments fail explicitly.
The shared MERGE generator omits WHEN MATCHED when no writable non-key columns
exist; this also applies to other dialects using that generator.

`CREATE_TEMPORARY` and `TRUNCATE_TEMPORARY` use the same optional
`TableOptions.temporaryTableNamePrefix`/`temporaryTableNameSuffix`. Temporary
tables are session-local and use the server's default logging policy; no
transaction guarantee is inferred. Plain columns, PRIMARY KEY/UNIQUE, CHECK and
ordinary indexes reuse shared generators. Foreign/exclusion constraints and
table storage/inheritance/specifics are rejected. The source Table is not mutated.
`supportsSequence()` is true and `getSequenceNextValString(sqlIdentifier)` returns
a single NEXTVAL query. Pass a quoted/schema-qualified SQL identifier when needed.
Bulk sequence preallocation/`SEQUENCE_NEXT_VALUES` is implemented in the follow-up
described above.

Catalog reading now includes database authorization users, roles, role memberships
and routine EXECUTE grants with overload identities. Each reader uses one set-based
catalog query, independent of the number of objects. User specifics retain
`INFORMIX_USER_TYPE` and `INFORMIX_DEFAULT_ROLE`; memberships retain default-role
and grant-option flags. Routine grants retain `INFORMIX_ROUTINE_KIND`. No passwords
or OS accounts are read. Unknown catalog flags or missing identities fail explicitly.
Role factories now generate `CREATE ROLE` / `DROP ROLE`. RoleMember factories
accept `CREATE` or `GRANT`, and `DROP` or `REVOKE`. Membership `admin` maps to
`WITH GRANT OPTION`; default membership uses `GRANT DEFAULT ROLE`. When both
flags are present, the factory returns two statements, because default-role SQL
does not allow a grant option. Execute the complete returned list. Revoke of a
default membership clears the default before revoking membership. Names are
quoted and escaped; PUBLIC is emitted as the keyword. Default and grant-option
membership to a modeled Role grantee is rejected.

RoutinePrivilege factories accept the same operation pairs for `EXECUTE ON
SPECIFIC FUNCTION/PROCEDURE`, preserving owner, grantee, grant option and optional
`AS grantor` (the latter requires server-side DBA authorization). The reader sets
`INFORMIX_ROUTINE_IDENTITY=SPECIFIC_NAME` for real catalog specific names, and
`PARAMETER_TYPE_IDS` for synthetic overload identities. A synthetic empty
signature is emitted as `FUNCTION owner.name()` or `PROCEDURE owner.name()`.
For unnamed routines with arguments, the reader resolves fixed built-in input
types through one batched SYSPROCCOLUMNS query. SMALLINT, INTEGER, FLOAT, SMALLFLOAT,
DATE, INT8, BIGINT and BOOLEAN are supported. CHAR and NCHAR use their
positive catalog length. VARCHAR and NVARCHAR use catalog lengths from 1
through 255 when no extra length flags are encoded. Decimal precision and
temporal qualifiers still require a SPECIFIC name. Named DISTINCT, opaque and ROW types
are resolved through a join to SYSXTDTYPES in the same batch and emitted
as quoted owner-qualified names. The signature retains identifiers containing
spaces, dots, commas and quotes across XML serialization.
A complete resolved signature is preserved
in `INFORMIX_ROUTINE_SIGNATURE_TYPES`. Other argument types, non-input modes,
and incomplete signatures require a real SPECIFIC name for grant generation.
Routine privilege reads support catalog, owner (`schemaName`) and routine-name
filters. Invalid argument counts, missing routine IDs and duplicate parameter
positions fail during metadata reading.
The Docker suite includes a round-trip test for INTEGER and DATE overloads
that checks that a generated REVOKE removes only the intended overload grant.
Hand-authored grants normally require
`INFORMIX_ROUTINE_KIND`, the SPECIFIC_NAME identity marker, a specific name, owner,
grantee, `privilege=EXECUTE` and `hierachy=false`. XML preserves these fields.
This SQL addresses the connected database; catalogName remains context.

User factories now generate connected-database authorization SQL for `CREATE` /
`GRANT`, `DROP` / `REVOKE`, and original/target `ALTER` differences. A plain
`new User(name)` grants CONNECT. `INFORMIX_USER_TYPE=C/R/D` selects CONNECT,
RESOURCE or DBA; the factory never infers DBA from `admin=true` alone. Type U
requires `INFORMIX_DEFAULT_ROLE` and grants only the default role, retaining the
distinction between implicit access and explicit CONNECT. The optional default
role is also applied for C/R/D users. DROP/REVOKE clears an explicitly modeled
default role, revokes DBA/RESOURCE first, then CONNECT. This removes the modeled
database authorization rather than an OS/mapped account, object privileges or
ordinary role memberships; PUBLIC or other authorization paths can still allow
access. Password/login/lock/expiry/default-schema settings and unknown user
specifics are rejected rather than ignored.

User ALTER handles privilege upgrades/downgrades and default-role changes without
revoking ordinary role memberships. Converting explicit CONNECT access to type U
is rejected pending a verified implicit-access revocation plan. Membership ALTER
handles default-flag changes and grant-option upgrades. Grant-option removal fails
explicitly: Informix REVOKE can affect downstream grants, so a naive revoke/regrant
would lose authorization outside the object being changed. Authentication renames
also require separate account management. ALTER requires a difference, not just
the desired object.

Table ObjectPrivilege factories now generate `CREATE`/`GRANT` and `DROP`/`REVOKE`
for SELECT, UPDATE, INSERT, DELETE, REFERENCES, INDEX, ALTER and UNDER. They preserve
the owner-qualified object, grantor (`AS`), recipient and grant option. ColumnPrivilege
supports SELECT, UPDATE and REFERENCES grants, with a quoted single column per
model object. PUBLIC is emitted as a keyword; grant options to PUBLIC or a modeled
Role are rejected. Hand-authored ObjectPrivilege values must set `hierachy=false`;
the shared model defaults it to true, but Informix has no WITH HIERARCHY clause.
DENY, unsupported privilege names, and routine-specific identities are rejected.

**Direct Column DROP/REVOKE is rejected.** Informix can grant SELECT, UPDATE
and REFERENCES on selected columns, but REVOKE removes that privilege from all
columns. Safely removing one column requires the complete table/column grant set
and downstream grant relationships, which an individual ColumnPrivilege cannot
provide. A table-level REVOKE explicitly affects that privilege across the table;
callers must account for the documented server-side grant-chain effects.

Object and column readers now use one catalog query each (`systabauth` joined to
`systables`, and `syscolauth` joined to `systables`/`syscolumns`). They decode
per-privilege grant options, retain grantors, distinguish column markers from
table-wide grants, and set table privilege hierarchy to false. Unknown or misplaced
codes and missing identities fail explicitly. Both 8- and 9-position table patterns
are supported; UNDER is the ninth position. The SQL supports catalog, owner and
object/column filters without per-table queries. XML round-trip and reader-to-GRANT
generation have unit coverage.

These additions target the existing Informix 11.70–15.x dialect range. Full
security migration remains incomplete: account management, column-specific revocation,
and grant-chain modification planning require separate handling. Catalog CREATE
emits routine grants after the roles, users, and routines. Schema CREATE emits
owner-qualified object statements. Informix's DB-Access CREATE SCHEMA AUTHORIZATION
block is not used as a separate namespace declaration. Catalog CREATE and ALTER
emit modeled grant-option prerequisites before delegated grants on the same
routine, table or column. For a column grant, a grant option on the whole table
for that privilege is also a prerequisite when present in the plan. Cycles in
modeled prerequisites are rejected. Grants from the object owner and grants
without a prerequisite in the generated operations
rely on the authorization present when the SQL is executed.
The individual factories also work when prerequisite principals and routines already exist.

Catalog ALTER accepts combined table, column, and routine privilege differences.
It emits GRANT for additions and GRANT WITH GRANT OPTION for upgrades. It emits
REVOKE for removal of grants without grant option on tables and routines.
For table SELECT and UPDATE, it restores retained column grants when the
affected grantors are known and those column grants have no grant option.
Removing a grantable
privilege or its grant option on a column is supported for SELECT and UPDATE
when the original scope has no downstream grants or unknown grantors. In that
case the planner revokes the scope and restores the target column grants without
grant option. A retained table grant or a target column grant with grant option
still requires a separate plan.
With complete original and target privilege snapshots, table SELECT, INSERT,
UPDATE and DELETE grant options can be removed when there are no downstream
table or column grants or unknown grantors in the affected scope, and no column
grants would need to be retained. REFERENCES, INDEX, ALTER and UNDER are excluded
from this table grant-option removal path. For a routine, complete privilege snapshots
allow removal when the grantee has no downstream grants on the qualified
routine name and all grantors in that scope are known. To keep EXECUTE while
removing its grant option, the plan revokes and then restores EXECUTE without
the option. Execute both operations under the target transaction policy.
With complete
privilege snapshots, Catalog ALTER can revoke all column grants for a given
owner, object, privilege, grantee and explicit grantor in one statement. It groups
equivalent table and column revokes and emits revokes before grants. For partial
column removal, it restores the retained column grants after the all-column
revoke. A retained table grant, a retained column grant with grant option,
or an unknown grantor requires a separate plan. Execute the complete
operation list under a transaction policy appropriate to the target database,
because a failure before restoration can leave retained grants revoked.
Other Catalog changes require separate plans.

Syntax references: [default-role grants](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=name-granting-default-role),
[default-role revocation](https://informix.hcldoc.com/12.10/help/topic/com.ibm.sqls.doc/ids_sqs_0956.htm),
and [routine-level privileges](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=statement-routine-level-privileges).
Database-authorization behavior follows [database-level revocation](https://www.ibm.com/docs/en/informix-servers/15.0.x?topic=rs-database-level-privileges)
and the [SYSUSERS privilege codes](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysusers).
See also [table privileges and column revocation scope](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=rs-table-level-privileges),
[SYSTABAUTH](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-systabauth)
and [SYSCOLAUTH](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-syscolauth).

The new SQL uses the existing catalog baseline and adds no dependencies, public
method signature changes or configuration format changes. This work was validated
with local generated-SQL/model and unit tests, without connecting to an Informix
server. Informix 11.70 live compatibility remains unverified.

```powershell
.\gradlew.bat :sqlapp-core:test --tests '*MergeEmptyUpdateTest' --tests '*MergeFactoryTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-informix:test --offline --console=plain
.\gradlew.bat :sqlapp-core:test :sqlapp-core-postgres:test :sqlapp-core-h2:test :sqlapp-core-sqlserver:test :sqlapp-core-oracle:test :sqlapp-core-db2:test --offline --console=plain
```

Specification basis: [MODIFY and dropped constraints](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=ats-modify-clause),
[RENAME COLUMN](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=statements-rename-column-statement),
[SPL DOCUMENT clauses](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=statement-specify-document-clause),
[SYSUSERS](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=tables-sysusers),
[SYSROLEAUTH](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysroleauth),
[SYSPROCAUTH](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=tables-sysprocauth).

See [enhancement status and remaining work](ENHANCEMENT_STATUS.md) for the
completion scope, remaining validation and intentionally rejected cases.

Data type review (2026-11-01): `InformixDataTypeMatrixTest` checks native
resolution and CREATE TABLE output for the 11.70, 12.10, 14.10 and 15 resolver
selections, which continue to use the existing common dialect. BIGINT, REAL and
DOUBLE/DOUBLE PRECISION aliases now resolve correctly; BIGINT retains its native
spelling, REAL emits SMALLFLOAT and DOUBLE emits FLOAT. Portable TIME emits
DATETIME HOUR TO SECOND, and a portable floating DECIMAL without precision uses
the existing DECIMAL(16) default instead of failing. Explicit decimal precision,
fixed/floating decimal semantics and temporal qualifiers remain covered by the
existing metadata tests. No resolver, metadata queries, dependency versions or
existing method signatures changed. The review ran unit tests only; it did not
connect to a live Informix server.

The final module run passed all 107 tests, including 19 new matrix/portable-type
cases, with no failures or skipped tests. No tracked generated files were
intentionally changed by this review.

```powershell
.\gradlew.bat :sqlapp-core-informix:test --tests '*InformixDataTypeMatrixTest' --console=plain
.\gradlew.bat :sqlapp-core-informix:test --console=plain
```

References: [Informix built-in types](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=udt-built-in-data-types),
[Informix DATETIME ranges](https://informix.hcldoc.com/12.10/help/topic/com.ibm.ddi.doc/ids_ddi_200.htm).

The dialect retains the existing resolver for Informix 11.70, 12.10, 14.10 and
later versions. These enhancements use the existing catalog baseline rather
than introducing newer-version-only catalog columns. The Docker integration
tests below have been executed on Informix Developer Edition 12.10.FC12W1,
14.10.FC9W1 and the 15.0.1.0.3 container image. Informix 11.70 has not been
verified against a live server. The older published 15.0.0.0 tag returned
"not found"; the available 15.0.1.0.3 tag was selected from the registry tag list.

## Metadata and SQL generation

- Indexes and ordered keys are read with bounded set-based catalog queries:
  headers plus columns (two SELECTs), and one additional bulk routine lookup when
  functional keys are present, plus one bulk operator-class lookup when needed
  (at most four SELECTs). UNIQUE constraints compose the same reader.
  Query counts do not grow with the number of indexes or constraints; an empty
  result needs only the header query. Columns are fetched once per table rather
  than repeated for every index/key combination. This guarantee concerns these
  readers, not the entire JDBC metadata pipeline.
- Catalog, owner, table and index identities remain distinct. Fixed-width
  database/owner values are normalized before they are matched to JDBC metadata.
  When an index or constraint owner differs from its containing table schema,
  `INFORMIX_OBJECT_OWNER` retains that identity. Constraint owners are fetched in
  one additional table-set query. Schema-decorated DDL uses the actual owner;
  ordinary undecorated DDL remains concise. ANSI-mode tests recreate identical
  table/constraint names owned by different users.
- PRIMARY KEY/UNIQUE/FOREIGN KEY backing indexes are represented by their constraints rather
  than emitted again as independent indexes. Ordinary indexes retain key order,
  uniqueness and CLUSTER status. B-tree functional indexes retain resolved,
  schema-qualified UDR names, multiple arguments and mixed ascending/descending
  keys. Functional expressions use ReferenceColumn `specifics` markers so a real
  column whose name resembles an expression remains quoted. INDEXKEYARRAY is
  parsed directly instead of truncating keys/arguments to the old 16-column view.
- Standalone index modes are joined from `sysobjstate` in the existing header
  query: DISABLED uses `Index.enable=false`, while FILTERING WITHOUT ERROR and
  FILTERING WITH ERROR use `INFORMIX_INDEX_MODE` in index specifics. Filtering
  requires an enabled UNIQUE index; unknown or contradictory modes fail
  explicitly. Missing catalog mode rows retain the existing enabled default.
  CREATE INDEX includes its mode directly, including with custom access methods,
  so a disabled UNIQUE index can be recreated over existing duplicate data
  without first creating an enabled index. Query counts and the resolver/version
  baseline are unchanged. Ordinary implicit constraint-backed indexes retain
  their constraint representation; named or special backing indexes are modeled
  explicitly as described below.
  Configure target violations/diagnostics tables explicitly before filtering DML;
  their associations are not recreated by the index factory.
- Standalone B-tree index storage is joined from `sysfragments` in that same header
  SELECT. Multiple fragment rows are grouped by catalog, table/schema, index
  owner and index identity before key resolution, so query counts remain at
  two to four SELECTs. Single-dbspace indexes retain `Index.tableSpaceName`;
  EXPRESSION fragments retain ordered names, expressions and dbspaces in
  `Index.partitioning` using the existing Informix fragment specifics. Legacy
  attached indexes without index-fragment catalog rows retain
  `INFORMIX_INDEX_IN_TABLE=true` and generate IN TABLE. Conflicting storage
  settings, missing fragment data, other fragmentation strategies and
  hierarchical index layouts fail explicitly. Storage clauses follow USING
  access-method clauses and precede index modes in CREATE INDEX.
  Table-based fragmentation (`sysfragments.strategy=T`) retains
  `INFORMIX_INDEX_FOLLOWS_TABLE=true` and omits a storage override so the index
  follows its target table. Recreate the table's fragmentation before its index;
  conflicting detached/legacy storage settings are rejected.
  Virtual access methods can store only a descriptor in the catalog dbspace
  while their data lives in an sbspace/extspace. Their single catalog location
  is retained as supplemental `INFORMIX_INDEX_CATALOG_DBSPACE`. Storage is used
  only when `sysams.am_sptype` and `sysmaster:sysdbspaces` identify a compatible
  data location. Otherwise `INFORMIX_INDEX_STORAGE_OVERRIDE_REQUIRED=true`
  prevents CREATE until the caller sets the actual target `Index.tableSpaceName`
  or each expression fragment's `Partition.tableSpaceName`. Descriptor locations
  are never substituted for unknown data locations. BTS on 12.10 can require
  this explicit mapping; 14.10 reports its sbspace in the tested configuration.
  Metadata users require SELECT access to `sysmaster:sysdbspaces`.
- SERIAL, SERIAL8 and BIGSERIAL identity columns use native types without a generated
  identity clause. Non-unit identity increments are rejected. The legacy
  `getIdentitySelectString()` returns the last SERIAL value using
  `DBINFO('sqlca.sqlerrd1')`; use JDBC generated keys for SERIAL8/BIGSERIAL values.
- Native column declarations retain the distinction between INT8/BIGINT and
  SERIAL8/BIGSERIAL, DATETIME field ranges and FRACTION scale, INTERVAL leading
  precision and FRACTION scale, and floating DECIMAL(p) versus fixed DECIMAL(p,s).
  Floating DECIMAL columns use the shared DECIMALFLOAT model type rather than
  incorrectly reporting a fixed-scale DECIMAL with scale 255.
  The shared `dataTypeName` carries temporal qualifiers and floating-decimal
  declarations through XML; invalid qualifiers fail explicitly. INTERVAL leading
  precision is decoded from the JDBC COLUMN_SIZE value (the encoded catalog
  qualifier), so this requires no additional SELECTs. Generic identity columns
  without a native type name retain the existing SERIAL/SERIAL8 defaults.
- Schema-wide JDBC column enumeration can include sequence implementation
  columns and columns exposed through synonyms. The existing bulk catalog query
  identifies and excludes these non-table/view objects, including synonyms with
  no own `syscolumns` rows. Real unresolved table columns still fail explicitly
  with their identities. This requires no extra SELECTs.
- Sequences retain signed 64-bit start/increment/bounds, CACHE/NOCACHE, CYCLE
  and ORDER/NOORDER through XML and CREATE. Unknown catalog cycle/order flags
  fail explicitly. Recreation uses the modeled start value; it does not promise
  to resume an in-use sequence at its current next value or preserve cached
  allocations. Reconcile sequence position separately before loading live data.
- Synonym CREATE is registered for local tables, views and sequences, and for
  database-qualified table/view references. `INFORMIX_SYNONYM_TYPE` is P for
  private or S for public; `INFORMIX_ANSI_DATABASE` records source ANSI mode.
  Both are read in one catalog SELECT, with the ANSI flag joined from
  `sysmaster:sysdatabases`. ANSI databases omit PUBLIC/PRIVATE; non-ANSI databases
  emit the declared visibility. The reader also retains the synonym catalog.
  Local targets omit database qualifiers so recreation does not accidentally
  refer back to the source database. Cross-database/server references retain
  `INFORMIX_DATABASE_NAME` and optional `INFORMIX_SERVER_NAME` using native
  `database@server:owner.object` syntax; quoted identifiers are escaped.
  Readers supply these settings automatically. Hand-built synonyms must specify
  visibility and ANSI mode, and changing target ANSI mode requires updating
  `INFORMIX_ANSI_DATABASE`; public synonyms cannot be recreated in ANSI databases.
  Server-qualified syntax has unit coverage only; no remote server was contacted.
  Synonym discovery needs catalog SELECT access to `sysmaster:sysdatabases`;
  access errors are propagated rather than guessing ANSI mode.
- Named PRIMARY KEY, UNIQUE, CHECK and FOREIGN KEY clauses use Informix's
  trailing `CONSTRAINT name` syntax. Foreign keys support `ON DELETE CASCADE`;
  unsupported update/delete actions fail explicitly.
- Constraint modes are read from `sysobjstate` in the same bulk owner query.
  DISABLED maps to the existing `enable=false`; FILTERING WITH ERROR and
  FILTERING WITHOUT ERROR use `INFORMIX_CONSTRAINT_MODE` in constraint specifics.
  CREATE TABLE and individual constraint CREATE operations retain these modes.
  Named NOT NULL uses the shared `NotNullConstraint` model; individual creation
  emits ALTER TABLE MODIFY with the complete column type and default.
  Direct column reads temporarily retain NOT NULL name/owner/mode in column
  specifics; full table reads resolve these into the canonical constraint.
  The existing bulk column query also corrects JDBC nullability for disabled
  primary keys and NOT NULL constraints. No per-constraint SELECTs are added.
  Configure target violations/diagnostics tables explicitly with START VIOLATIONS
  TABLE before using filtering DML; their associations are not recreated here.
  NOVALIDATE is a statement-scoped option and is not persisted as a catalog mode.
  Shared model XML now retains disabled primary keys, and adding a UNIQUE or a
  disabled constraint no longer makes a nullable column mandatory. Enabled
  primary keys and enabled NOT NULL constraints retain their existing behavior.
- CHECK expressions retain whitespace across the catalog's 32-character chunks
  and are grouped by constraint ID. Actual constraint owners are retained even
  when checks are read directly. Requested checks use one bulk SELECT; missing
  or blank expressions fail explicitly rather than disappearing or generating
  invalid DDL.
- Column defaults supplement the existing JDBC metadata call with one bulk
  catalog SELECT for the requested column set (no per-column queries). This
  distinguishes an absent default, explicit NULL and an empty string; restores
  apostrophe escaping and trailing spaces from null-terminated catalog strings;
  quotes BOOLEAN literals; and restores USER, DBSERVERNAME, TODAY and qualified
  CURRENT expressions. Encoded numeric and temporal literals use their stored
  ASCII values with qualified DATETIME/INTERVAL literal syntax. DATE literals
  require a compatible target date format.
  Unknown or undecodable defaults and unresolved column identities fail explicitly.
- EXPRESSION, ROUND ROBIN, LIST and RANGE/INTERVAL table fragmentation can be
  recreated after Schema XML serialization. The existing Partitioning/Partition
  model retains vendor expressions in `specifics`. Negative `sysfragments.evalpos`
  header rows are retained as `INFORMIX_FRAGMENT_HEADER_<position>` entries.
  Single-dbspace storage is represented by the table's tablespace name.
- Rolling-window RANGE/INTERVAL policies retain their fragment-count limit,
  capacity limit (normalized to KB), explicit DETACH/DISCARD action and
  ANY/INTERVAL ONLY/INTERVAL FIRST scope. Disabled interval generation is restored
  by a separate ALTER statement after CREATE; execute the complete operation list.
  No purge action is guessed when configuration is incomplete. Data-dependent
  interval fragments remain supplemental metadata and are generated from the
  interval policy when data is loaded.
- Date/time defaults and dummy-table queries use native Informix expressions.
- Trigger enabled/disabled modes are joined from `sysobjstate` in the existing
  trigger text SELECT and stored in `Trigger.enable`, including when SQL text
  reading is disabled. Unknown modes fail explicitly. Trigger CREATE returns
  the complete CREATE statement followed by SET TRIGGERS with the modeled mode;
  execute both operations before loading data. This also handles a trigger whose
  mode changed after its original CREATE statement. Trigger names are required;
  Informix trigger names are unique within a database, so the SET statement
  addresses that individual name without changing other triggers on the table.
- Views and routines preserve whitespace across the catalog's 256-character
  SQL chunks. View CREATE text and routine CREATE text are retained in
  `definition` when either text-reading option is enabled; routine arguments
  remain supplemental metadata. Function and procedure CREATE factories reuse
  that text and reject missing definitions instead of producing partial SPL.
  View, function and procedure headers/text each use one set-based SELECT,
  independent of object count. Explicit specific names are retained literally;
  when SPECIFIC was omitted, the model uses `name(parameter types)` from the
  catalog to distinguish overloads without changing their CREATE text.
  Missing requested SQL text or required parameter-type identities fails explicitly.
- Trigger catalog headers (`systrigbody` D rows) and action bodies (A rows) are
  combined into a complete executable CREATE in `definition`, including when
  only `readStatement` is enabled (the default). The body in `statement` is
  supplemental metadata. Chunk-boundary whitespace is retained, and multi-phase
  triggers are not reduced to a guessed single timing/orientation. If both text
  options are disabled, no SQL text is retained. CREATE requires a complete
  definition rather than emitting incomplete generic trigger syntax.
- Custom index access methods, SQL parameter fragments and per-key operator
  classes retain their resolved names and owners through Schema XML. CREATE
  uses those names rather than source-specific catalog IDs. Referenced access
  methods, DataBlade modules and operator classes must already exist at the
  destination; this dialect does not install them automatically.

Named or special UNIQUE backing indexes retain their full `Table.indexes`
representation and `UniqueConstraint.indexName`. Table CREATE returns operations
in table, index, UNIQUE, FK order; execute the entire list. Internal index names
with illegal leading spaces are replaced by deterministic target names.
Functional UNIQUE indexes are supported separately, including duplicate rejection
after XML recreation. A function key reported as a UNIQUE constraint is rejected
rather than guessed into a column constraint. Native tests cover named backing index reuse;
custom operator classes/access methods still require their destination libraries.

Named ROW and DISTINCT types are read in one bulk query as executable `Type.definition`,
including UNDER and only newly introduced subtype fields. Typed tables retain
their ROW type identity and canonical `Table.inherits` in one additional bulk
query. CREATE uses OF TYPE/UNDER; child storage follows the parent rather than
repeating its fragmentation. Recreate referenced types, parent types and tables
before their children. A populated parent model suppresses duplicate inherited
constraints only when both constraint name and effective owner match. In ANSI
databases, a child's own constraint can share its parent's name under a different
owner; it is emitted separately and retains its enforcement after Catalog XML
recreation. This uses existing constraint-owner metadata without extra SELECTs.
Primitive/temporal fields and named type references are
supported. DISTINCT definitions preserve built-in precision/length, including
LVARCHAR limits, and qualified named source types. SET, MULTISET and LIST fields
can contain named types or anonymous ROW/collection types, with element NOT NULL
retained. Anonymous type catalogs are fetched database-wide in the same SELECT
because their IDs can be shared across owners; only the requested named types
are returned. Missing members, cycles and malformed collections fail explicitly.
System built-in type IDs are excluded from named type output.
Ordinary table columns also retain schema-qualified named ROW/DISTINCT and
user-defined opaque type identities
and anonymous ROW/SET/MULTISET/LIST declarations in `Column.dataTypeName`.
`INFORMIX_NATIVE_TYPE` in column specifics stores the exact native declaration
for SQL rendering, preserving quoted identifier case and whitespace through XML
and avoiding generic type-name normalization and JDBC type/length substitution.
The native declaration must agree with the normalized `Column.dataTypeName`.
When changing a native type, update both; when switching to an ordinary type,
remove `INFORMIX_NATIVE_TYPE`. Conflicts fail with an actionable error rather
than silently recreating the previous type.
Named identities are joined into the existing defaults query. Only when anonymous
complex columns are present is one additional bulk type-catalog SELECT needed,
independent of the number of tables or columns. The same declaration renderer
is used for named type attributes and ordinary columns.
Opaque column identities use the existing catalog join without an additional
SELECT. System built-in opaque types, including BOOLEAN and LVARCHAR, retain
their native built-in rendering. Opaque types and their support functions must
already exist at the destination; the reader does not synthesize library-backed
type definitions. Docker coverage preserves a quoted opaque type reference,
checks its catalog owner/name after XML recreation, and verifies neighboring
built-in columns. That fixture inserts NULL for the opaque value and does not
claim to test custom input/output functions.
Disabling both type-text options returns named type headers without requiring
the source identities needed to generate DISTINCT DDL.
Typed-table column defaults are restored with shared column definitions through
ALTER TABLE MODIFY after OF TYPE creation. Execute all returned operations.
ROW subtypes that introduce no fields are recreated without an empty field list:
`CREATE ROW TYPE child UNDER parent`. Multi-level inheritance retains the
parent's fields and NOT NULL semantics. Root ROW types still require readable
fields. Native opaque library/type installation and automatic dependency planning
are outside this dialect.

A disabled interval that already has
automatically generated fragments is rejected during CREATE generation because
reconstructing only its initial ranges would prevent data from being reloaded.
Informix also rejects recreating or renaming the reserved generated partition
names (error 25813), so those identities cannot simply be restored by ADD.
Unknown rolling-window flags, missing function/column identities, missing headers
and invalid configuration also fail explicitly.
Routine recreation also retains ordered `sysprocbody` A-row ALTER history in
`INFORMIX_ROUTINE_ALTER` specifics. CREATE generation returns the CREATE operation
followed by separate ALTER operations; execute the entire list to restore the
final modifiers. Multiple changes and ALTER statements spanning catalog chunks
are supported without additional SELECTs. Quoted identifiers, strings and comments
are excluded when locating statement boundaries. If a line comment swallows a
later ALTER header because the catalog omitted a separating newline, generation
fails explicitly rather than silently dropping an operation; that ambiguous
history requires correction before recreation. External routine libraries and
other referenced objects must already exist at the target.

Existing XML remains readable. Older artifacts containing header-only trigger
definitions, or lacking resolved functional keys or complete LIST/INTERVAL/rolling
headers, must be read again from the source
before those objects can be recreated faithfully. Referenced dbspaces and UDRs
must exist on the destination before indexes are created. XML and public API
signatures have not changed; the additional metadata uses the shared Schema
model and optional `specifics` values.
Re-read older view/routine artifacts if chunk-boundary spaces were lost or
unnamed overloads were collapsed; the missing text/objects cannot be recovered
from those artifacts alone.
Re-read altered routines from artifacts that did not retain their ALTER history.
Re-read CHECK/default artifacts that previously lost chunk-boundary spaces,
literal quoting, trailing spaces, empty strings or dynamic default semantics.
Re-read constraint metadata from older artifacts that omitted disabled/filtering
modes or named NOT NULL constraints; those states cannot be recovered from XML
that did not store them. The XML format and existing default enabled state remain
compatible; the existing optional `enable` attribute is now also written for a
disabled primary key.
Re-read standalone index metadata from artifacts that omitted disabled or
filtering modes; the existing default enabled state remains compatible.
Re-read index metadata to recover dbspace placement, EXPRESSION fragments and
legacy IN TABLE storage; older artifacts did not retain these distinctions.
Re-read sequence/synonym metadata to recover sequence ordering, synonym ANSI
mode and local-versus-cross-database target identity from earlier artifacts.

## Tests

Use Java 25 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-informix:test
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests com.sqlapp.data.db.dialect.informix.metadata.InformixMetadataDockerTest
```

The second command explicitly starts disposable local Informix containers using
the existing Testcontainers infrastructure and Developer Edition license
acceptance. It does not connect to an external database. New metadata coverage
checks bulk query counts, native SERIAL/date expressions, CHECK/FK/CLUSTER DDL,
and catalog → Schema XML → executable CREATE TABLE for four fragmentation
strategies, plus functional index key equality and rolling-policy equality after
recreation. Coverage includes a functional key with 17 arguments, operator-class
recreation after catalog IDs change, and BTS keyword-analyzer search behavior
after index recreation. Trigger coverage checks one-query retrieval across
multiple owners, text options and XML, plus multi-phase execution and long
string-literal preservation after CREATE on the real server.
Mode tests switch both enabled-to-disabled and disabled-to-enabled triggers,
then verify catalog state and actual firing after XML recreation.
Index-mode tests retain ordinary disabled indexes and all four UNIQUE index
modes through XML, recreate disabled UNIQUE indexes over existing duplicates,
and check rejection, acceptance and violations-table routing of duplicate rows.
Index-storage tests recreate single-dbspace, two named EXPRESSION fragments
including REMAINDER, and legacy IN TABLE indexes over existing rows; compare
their sysfragments records before/after XML recreation; and retain the same
two-query bound. Unit tests also cover quoted fragment names, grouping and
conflicting storage options.
Another round trip recreates a fragmented table and its table-based index and
checks that both inherited index fragments retain strategy T.
Whole-schema coverage uses the public SchemaReader.loadFull entry point, XML,
then an explicit dependency order for object recreation. It exercises related
tables and FK/CHECK/NOT NULL, defaults, disabled indexes, views, routines,
triggers, sequences, and private/public table/view/sequence synonyms together.
This verifies the complete retrieval path, not an automatic dependency planner.
Additional tests recreate descending cyclic sequences and values beyond 32 bits,
and synonyms with embedded identifier quotes across two disposable databases in
both ANSI and non-ANSI mode. A separate test creates an OS account only inside
the disposable container, grants database CONNECT and table SELECT, and checks
schema metadata retrieval while INSERT and unrelated-table SELECT are denied.
View/routine tests preserve long literal values after XML recreation, exercise
both function overloads and procedure writes, and retain a view's CHECK OPTION.
ALTER-history coverage includes multiple modifier changes, a 40-argument
signature spanning catalog chunks, final catalog VARIANT flags and an unaltered
overload after XML recreation.
Native-column coverage compares `syscolumns.coltype` and `collength` before and
after JDBC → Schema XML → CREATE, and checks generated values, a large floating
DECIMAL value, fractional DATETIME and INTERVAL values.
CHECK/default coverage compares values inserted into the source and recreated
tables, including quotes, trailing spaces, empty strings, literal versus dynamic
USER, BOOLEAN, numeric and temporal defaults, plus acceptance/rejection by a
long CHECK expression. Unit tests cover constant query counts and missing data.
Constraint-mode coverage recreates disabled PK, UNIQUE, CHECK, FK and named
NOT NULL through XML, checks acceptance of NULL/duplicate/invalid references,
re-adds NOT NULL through MODIFY while retaining its default, and exercises both
filtering error modes with explicitly created target violations tables.
The BTS test creates a temporary smart-blob space, registers the shipped module
inside its disposable container, and verifies both single-space and expression-
fragmented index recreation with searches spanning the fragments. Typed-table
coverage recreates parent/child ROW types and fragmented table inheritance
through whole-schema XML, then checks inherited queries, defaults and stored values.
Complex-type coverage recreates DISTINCT chains and bounded LVARCHAR plus
nested SET/MULTISET/LIST/anonymous ROW attributes, compares reread definitions
and inserts values using the recreated types.
Backing-index coverage checks CREATE ordering, native index reuse, duplicates
and self-referencing FK enforcement. Custom backing operator-class mapping has
unit coverage; no external native library is installed for that case. Unit tests also
cover same-name objects across owners/tables, quoted expression-like column
names, malformed keys, missing identities and unknown policy flags.

The metadata test image can be selected without changing dependency versions:

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests com.sqlapp.data.db.dialect.informix.metadata.InformixMetadataDockerTest -PinformixTestImage=ibmcom/informix-developer-database:12.10.FC12W1DE
```

This property selects the image for `InformixMetadataDockerTest`; the older bulk
and tree-session test classes retain their existing fixed image. Readiness for
the metadata test is checked by a local SQL connection rather than a
version-specific log message.

For Informix 15, quote the complete property argument in PowerShell:

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests com.sqlapp.data.db.dialect.informix.metadata.InformixMetadataDockerTest '-PinformixTestImage=icr.io/informix/informix-developer-database:15.0.1.0.3'
```

The fixture detects both the older direct installation and the versioned
`/opt/ibm/informix/v*` installation used by the tested 15 image. Readiness and
BTS setup use the detected server binaries; no host installation is required.
The tested 15 image digest is
`sha256:8202d69ba5674df4b13140d5121dd11b7b26b28dc60119b7e8f87e533e538ba1`.

References: [IBM catalog documentation](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysindexes),
[extended type catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysxtdtypes),
[complex type attributes](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysattrtypes),
[fragment catalog](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=tables-sysfragments),
[functional index catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysindices),
[trigger text catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-systrigbody),
[trigger modes](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=statement-trigger-modes),
[index modes](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=statement-index-modes),
[view text catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysviews),
[routine text catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysprocbody),
[column length encoding](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=syscolumns-storing-column-length),
[INTERVAL qualifiers](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=expressions-interval-field-qualifier),
[CHECK text catalog](https://www.ibm.com/docs/ja/informix-servers/12.10.0?topic=tables-syschecks),
[default catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysdefaults),
[sequence catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-syssequences),
[synonym catalog](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-syssyntable),
[synonym visibility](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=statement-public-private-synonyms),
[constraint-mode syntax](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=definition-choosing-constraint-mode-option),
[persistent constraint modes](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=statements-set-constraints-statement),
[rolling-window syntax](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=clause-rolling-window-create-table),
[official container images](https://github.com/informix/informix-dockerhub-readme).

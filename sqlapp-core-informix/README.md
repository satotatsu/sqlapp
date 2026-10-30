# Informix dialect

See [enhancement status and remaining work](ENHANCEMENT_STATUS.md) for the
completion scope, remaining validation and intentionally rejected cases.

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

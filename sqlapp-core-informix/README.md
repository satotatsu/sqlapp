# Informix dialect

The dialect retains the existing resolver for Informix 11.70, 12.10, 14.10 and
later versions. These enhancements use the existing catalog baseline rather
than introducing newer-version-only catalog columns. The Docker integration
tests below have been executed on Informix Developer Edition 12.10.FC12W1 and
14.10.FC9W1. Informix 11.70 and 15 have not been verified against a live server.
The published 15.0.0.0 tag and digest returned "not found" when checked, so this
change does not claim a successful Informix 15 integration run.

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
  baseline are unchanged. Constraint-backed indexes retain their constraint
  representation and are excluded from standalone index generation.
  Configure target violations/diagnostics tables explicitly before filtering DML;
  their associations are not recreated by the index factory.
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

Custom access methods/operator classes on UNIQUE constraints and hierarchical
fragmentation remain unsupported and fail explicitly. A disabled interval that already has
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
The BTS test creates a temporary smart-blob space and
registers the shipped module inside its disposable container. Unit tests also
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

References: [IBM catalog documentation](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=tables-sysindexes),
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

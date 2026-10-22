# H2 dialect coverage and verification

[Development index](README.md)

H2 uses the existing Schema model and SQL factory registry. The ordinary path
requires no additional planner, connection wrapper or configuration:

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var reader = dialect.getCatalogReader().getSchemaReader();
reader.setSchemaName("PUBLIC");
var schema = reader.getAllFull(connection).get(0);
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(schema.getTables().get("ORDERS"), SqlType.CREATE);
```

Factories generate operations; callers execute them and retain database failures.
For recreation into an empty database, create schemas and parent domains before
dependent domains/tables, then indexes, aliases, triggers, views and synonyms as
their dependencies require. Definitions with embedded object names must be edited
when those identities change.

## Version boundaries

The resolver retains `H2` for 1.x, `H2_200` for 2.0/2.1 and selects the compatible
subclass `H2_220` for 2.2 and later. Modern catalog fields and
`DATA_TYPE_SQL` remain in the `_200.sql` resources; legacy catalog resources are
not replaced by 2.x queries. Modern MERGE factories and synonym reading are
selected only for 2.x. Generated expression syntax retains the legacy `AS (...)`
form for 1.x. H2 2.x sequence access uses `NEXT VALUE FOR`; removed `CALL IDENTITY()`
fails with an instruction to use JDBC generated keys. Existing generated-key
support remains available.

The executable evidence uses the existing H2 **2.3.232** test dependency, Java 21
and in-memory databases. No dependency upgrades, external databases or Docker are
required. Legacy resolver and SQL generation are tested, but old H2 binaries are
not executed. This is not a claim that every feature in later H2 releases has
been implemented or verified.

## Metadata and recreation

| Area | Representation and verification |
|---|---|
| Identifiers | Native double quotes, embedded quote escaping, reserved words, mixed case and Unicode; quoted schemas execute without compatibility mode |
| Scalar columns | Decimal precision/scale, character/binary lengths, datetime precision, JSON, UUID, DECFLOAT and large-object lengths |
| Complex columns | Native array cardinalities/nesting, ROW, INTERVAL and geometry type SQL in `dataTypeName` when the shared type cannot express the full declaration |
| ENUM | Ordered, decoded values; apostrophes and Unicode survive recreation |
| Identity | ALWAYS/BY DEFAULT, declared start, current next value, increment, bounds, cycle and optional `h2.identityCache` |
| Generated columns | Native generation expression, omitted from INSERT/UPDATE/MERGE inputs |
| Column policies | ON UPDATE, INVISIBLE and optional `h2.defaultOnNull`; configured column remarks generate native inline comments |
| Domain references | Column Domain identity, including a different or quoted schema |
| Domains | Scalar/complex types, defaults, ON UPDATE, separately named checks; parent identity in optional `h2.parentDomainSchema`/`h2.parentDomainName` |
| Views | Query in `statement` plus separately modeled output columns; complete `definition` is also accepted |
| Java aliases | Class/method or Java source in `statement`, determinism and native DROP ALIAS; void aliases are included |
| Alias overloads | One alias object with a complete executable CREATE definition and `h2.aliasOverloadCount`; argument metadata is supplemental rather than a complete signature list |
| Triggers | Grouped event set, timing, ROW/STATEMENT orientation, Java class, rollback event and optional native queue/no-wait fields |
| Constants | Complete executable CREATE CONSTANT definition; bare value expressions are not misrepresented as executable DDL |
| Synonyms | Native source/target identities, filtered set retrieval, CREATE and DROP |
| Constraints and indexes | Existing primary/unique/FK/check readers remain in use; indexes retain direction and NULLS FIRST/LAST; generated recreation and native ALTER execute against H2 |
| Storage | CACHED/MEMORY table kind survives recreation into isolated file databases; LOCAL/GLOBAL temporary identity is retained and generic permanent CREATE is refused |

Modern domain metadata uses `checks` rather than a duplicated flattened `check`.
Hand-authored domains can still use the legacy single `check`, but cannot combine
both forms. Disabled or deferred named domain checks are rejected. Complete domain
definitions can carry their own components without requiring duplicate fields.

Metadata queries retrieve requested sets. Named domain checks use a second bulk
query, not a SELECT per domain. A regression test compares query counts for one
and twelve tables/domains/views/indexes/synonyms and requires constant SELECT
counts. Modern type SQL is resolved inside the existing column query, without
additional per-column statements.

## DML and native operations

`INSERT`, `INSERT_ROWS`, `MERGE`, `MERGE_ROWS`, `MERGE_TABLE`, `UPDATE` and the
existing SELECT/DELETE paths compose with shared binding and column policies.
MERGE reuses shared key selection and match/update planning, with typed H2 VALUES
sources. Generated columns are excluded. Tables containing only key columns
produce no empty UPDATE clause when no non-key update is needed. BY DEFAULT
identity input can be explicit; ALWAYS identity keys require selecting a natural
unique key. Missing keys and unsupported delete-by-source/RETURNING policies fail
before SQL is returned.

String/binary source casts stay unbounded so oversized values fail at the target
assignment instead of being silently truncated by H2 CAST. Domain/ARRAY/ROW VALUES
sources require `MERGE_TABLE` with a typed staging table for the same reason;
native composite casts can otherwise alter input before target validation.

Default-only INSERT emits native `DEFAULT VALUES`. An ALWAYS identity does not
accept explicit input. Identity planning uses the existing Table row/signature
contract: include actual rows in the model when choosing generated versus
explicit identity input. Default-only batch requests require repeated single
INSERT operations rather than invalid empty VALUES SQL.

H2 2.x JSON uses a JDBC handler that sends UTF-8 bytes and reads JSON text. This
preserves documents rather than silently storing a JSON string containing their
text. Object, array, string, JSON null and SQL NULL values are exercised through
single/batch INSERT, MERGE and UPDATE. See the native
[JSON type contract](https://h2database.com/html/datatypes.html#json_type).

`LOCK` emits SELECT FOR UPDATE and holds row locks within the caller's
transaction. It is not a database-wide or table-wide exclusive lock; shared lock
requests are rejected. `ANALYZE` selects the modeled table explicitly. Sequence
range queries honor schema decoration. `SET_COMMENT` supports schemas, tables,
columns, views, indexes, domains, sequences, aliases, triggers and constants;
null remarks remove the comment. Comment operations are explicit; SQL source
comments are not a substitute for native catalog comments.

## ALTER and transaction boundaries

Native ALTER tests cover add/drop/rename/type changes, nullable/default changes,
ON UPDATE addition/removal, table rename, generated additions, indexes and checks
in PUBLIC and a quoted schema, while checking retained row values.

A rename combined with type changes can appear as deleted/added columns in the
shared diff model. H2 rejects any mixed deleted/added column set rather than
issuing ambiguous destructive SQL. Rename separately, reread or update the model,
then apply the remaining changes. Unsupported native operations still report
database errors; there is no automatic destructive table-copy fallback.

H2 DDL statements commonly commit open transactions. This implementation does
not promise SQLite-style atomic schema rebuilding. DML and row-lock tests verify
caller-controlled commit/rollback; DDL tests verify native execution and retained
data, without claiming rollback guarantees. See the native
[command reference](https://h2database.com/html/commands.html).

## UNIQUE null treatment

H2 2.2 and later retain UNIQUE null treatment in the constraint's existing
`specifics` map as `h2.nullsDistinct`: `YES` (NULLS DISTINCT), `NO` (NULLS NOT
DISTINCT), or `ALL` (NULLS ALL DISTINCT). Inline CREATE TABLE and standalone
constraint creation reproduce the policy explicitly, including defaults read
from Oracle and MSSQLServer compatibility modes. Older target dialects reject
this setting. The additional catalog column is queried only on 2.2 and later,
in the existing bulk constraint query. MERGE, MERGE_ROWS and MERGE_TABLE use
the selected unique key's null treatment when matching existing rows. ALL permits
duplicate entirely-null keys, while matching partially-null keys. See the native
[null treatment syntax](https://h2database.github.io/html/grammar.html#nulls_distinct)
and [2.2.220 change log](https://h2database.com/html/changelog.html).

## Remaining limitations

- Source-defined triggers do not expose their original source in the 2.x catalog.
  If Java class metadata is unavailable, recreation requires a complete CREATE
  definition, source statement or explicit Java class and otherwise fails clearly.
- Original temporary-table ON COMMIT policies, linked-table execution/security
  configuration, arbitrary custom engines and all later-version features are
  not covered by these round-trip guarantees. The explicit temporary-table API
  retains its existing behavior. Generic CREATE rejects modeled temporary tables
  with an explicit lifetime-policy instruction rather than converting them to
  permanent tables. Storage tests use automatically cleaned, isolated file DBs.
- H2's nontransactional DDL prevents promising the same atomic table-rebuild
  behavior as SQLite. No production data migration or table reconstruction is
  performed implicitly.
- Historical binaries, server mode, compatibility modes beyond the UNIQUE policy
  checks described above, and external databases
  remain unexecuted in this verification pass.

## Checks

Run focused classes first, then the module and its direct consumers:

```powershell
.\gradlew.bat :sqlapp-core-h2:test --no-parallel --console=plain
.\gradlew.bat :sqlapp-command:test :sqlapp-gradle-plugin:test --no-parallel --console=plain
```

The focused classes are `H2DialectRoundTripTest`, `H2CatalogCoverageTest`,
`H2NativeDmlTest`, `H2JsonBindingTest`, `H2NativeAlterTest` and
`H2NativeOperationsTest`, `H2StorageMetadataTest`, `H2IndexOrderingTest` and
`H2ViewDefinitionTest`, `H2UniqueNullTreatmentTest` and `H2MergeNullTreatmentTest`.
Existing metadata, domain/sequence, JDBC tree, cursor,
delete, generated-key and bulk snapshot tests remain part of the module suite.

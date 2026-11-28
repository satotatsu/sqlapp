# JDBC metadata

[Schema documentation](README.md)

The readers in `com.sqlapp.data.db.dialect.jdbc.metadata` provide shared JDBC
fallbacks. Prefer the catalog and schema readers supplied by your dialect:
vendor readers can obtain definitions and metadata that JDBC does not expose.
These corrections apply to any database version using the shared fallback;
they do not change database-specific SQL or require new configuration.

## Tables, indexes and keys

Full table reads preserve the selected catalog, schema and table identities,
including tables with the same name in different schemas or catalogs.
Columns are fetched in a set. Index, primary-key and imported-key metadata use
one JDBC call per selected table and metadata kind. This is a deliberate
exception to bulk metadata retrieval: JDBC's `getIndexInfo`, `getPrimaryKeys`
and `getImportedKeys` accept one exact table, with no standard set-based
alternative. The table reader reuses its discovery result rather than
discovering tables again for each child reader. A standalone key/index reader
without a table selection first discovers tables once.

Column identity is independent of nullability. Index statistics are excluded
from indexes, and JDBC hash indexes retain `IndexType.Hash`.

Named foreign-key columns are ordered by `KEY_SEQ`. Anonymous single-column
keys and unambiguous anonymous composite keys remain separate constraints.
Their SQL names remain absent; stable IDs in the existing Schema `id` field
keep them distinct in collections and XML. If multiple anonymous composite
keys cannot be distinguished from the supplied JDBC rows, reading fails with
an actionable error. Name those constraints or use a vendor reader that can
identify them; the fallback does not guess column pairings.

## Functions and procedures

Routine parameters retain IN, OUT and INOUT directions. Their type length or
numeric precision comes from JDBC `PRECISION`; `LENGTH` is a byte count and is
not used as a replacement for precision. Character octet length is stored
separately when supplied by the driver. An unknown parameter direction fails
explicitly rather than silently becoming IN.

Full function reads place scalar return metadata in `Function.returning`, and
result columns in its returning table. They are not ordinary parameters.
Procedure return-status and result-set rows are also excluded from the formal
parameter list; the current Procedure model has no dedicated representation
for them.

JDBC does not provide routine bodies or complete CREATE definitions. Reading
the signature alone does not make a function or procedure recreatable: use a
vendor reader that also obtains its body or complete definition for that purpose.
Public method signatures and the Schema XML format remain unchanged, but
corrected metadata can produce different snapshots and generated DDL.

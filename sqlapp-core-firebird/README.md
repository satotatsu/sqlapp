# Firebird dialect data types

Data type regression checks cover representative resolver branches for Firebird
1.5, 2.0, 2.5, 3, 4 and 5. The dialect already emits DOUBLE PRECISION for the
portable DOUBLE model, including retained source DOUBLE/DOUBLE PRECISION names.

## Corrected behavior

- REAL and CHARACTER VARYING aliases resolve to their shared Schema types.
- FLOAT(p) uses decimal precision through Firebird 3 (0..7 selects single
  precision; larger values select double precision), and binary precision from
  Firebird 4 (1..24 selects single precision; 25..53 selects double precision).
- Firebird 4+ resolves INT128, BINARY, VARBINARY and BINARY VARYING. INT128 retains
  its native name on the existing DECIMAL model representation.
- DECFLOAT defaults to precision 34; only precision 16 or 34 is accepted for DDL.
- NUMERIC/DECIMAL defaults follow the engine's (9,0), replacing the previous
  (18,5). Explicit precision and scale are retained. Invalid precision or scale
  fails before DDL generation; precision is limited to 18 before Firebird 4 and
  38 from Firebird 4.
- Native BLOB SUB_TYPE TEXT/1 and BINARY/0 spellings resolve correctly. LOB
  capacity is no longer emitted as an invalid, approximately 2GB SEGMENT SIZE.
  BLOB arrays are rejected.
- Metadata field type 10 is classified as shared REAL (32-bit FLOAT), rather
  than the generic FLOAT model whose fallback could generate double precision.

## Optional BLOB segment size

Ordinary BLOB/CLOB declarations omit SEGMENT SIZE and use the server default.
For an explicit storage setting, use the existing Schema specifics map:

```java
column.getSpecifics().put(FirebirdSqlBuilder.BLOB_SEGMENT_SIZE, "80");
```

This emits `BLOB SUB_TYPE BINARY SEGMENT SIZE 80` (or TEXT for CLOB). The accepted
range is 1..65535; non-integer values and settings on non-LOB columns fail clearly.
Metadata retains the existing length/octet-length fields and additionally records
this setting. Column and domain DDL preserve it. Previously saved schemas that
only recorded a LOB length use the server segment default; re-read metadata or
set this optional specific to retain a chosen segment size.

No module dependencies, existing Java method signatures, resolver version
boundaries or metadata queries changed. Existing explicit numeric declarations
remain unchanged; old incorrectly classified FLOAT models are not rewritten.

## Tests

Run with Java 25 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-firebird:test --tests '*FirebirdDataTypeMatrixTest' --console=plain
.\gradlew.bat :sqlapp-core-firebird:test --console=plain
```

The matrix checks native resolution, portable models, CREATE TABLE generation,
metadata recreation, precision boundaries, BLOB segments and bounded arrays.
The module tests do not require an external database. Live Firebird execution
was not run for this change. No tracked generated files were intentionally changed.

On 2026-11-01, all 39 matrix cases and all 282 module tests passed, with no
failures or skipped tests. External-database compatibility tests in
`sqlapp-core-dialect-test` were not executed.

References:
[Firebird 5 types](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-datatypes.html),
[floating-point types and version changes](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-datatypes-floattypes.html),
[fixed-point defaults](https://firebirdsql.net/file/documentation/html/en/firebirddocs/firebirdinternals/firebird-internals.html),
[Firebird 4 syntax](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref40/fblangref40-datatypes-syntax.html).

# SAP ASE data type review

This review targets SAP ASE, not SAP IQ or SQL Anywhere. The native type matrix
uses ASE 15/16 resolver selections. The resolver still returns the existing
single Sybase dialect; it does not enforce version availability for every type.
In particular, UNITEXT and unsigned integer coverage assumes ASE 15 or later.

## Corrections

- Portable DOUBLE emits DOUBLE PRECISION, including retained source DOUBLE
  names. FLOAT defaults to precision 48 rather than generating FLOAT(0); explicit
  precision must be 1..48. REAL remains REAL.
- Native TINYINT is unsigned (0..255) and maps to UTINYINT. A portable signed
  TINYINT emits SMALLINT. Native SMALLINT remains SMALLINT. The UNSIGNED
  SMALLINT/INT/BIGINT families are registered with their shared unsigned types.
- NUMERIC/DECIMAL defaults are (18,0), replacing the previous (19,5). Explicit
  precision/scale is retained, and invalid precision or scale fails before DDL
  generation.
- TEXT is no longer accidentally normalized to NTEXT. UNITEXT is represented
  by NCLOB; portable LONGNVARCHAR also emits UNITEXT. Legacy NTEXT/NATIONAL TEXT
  inputs normalize to UNITEXT.
- Native UNICHAR and UNIVARCHAR retain their ASE spellings and character lengths.
  Their byte widths use the fixed two-byte Unicode storage unit. NCHAR/NVARCHAR
  instead use the server's @@ncharsize, fetched in the existing column/domain
  SELECTs without adding queries.
- UUID uses VARCHAR(36) and string JDBC binding, rather than assuming an ASE
  UNIQUEIDENTIFIER system type. The old UNIQUEIDENTIFIER input spelling remains
  recognized. Generic ANY_DATA/VARIANT DDL now fails with an actionable error;
  select a concrete Schema type or an explicit domain instead.
- Numeric/integer IDENTITY declarations recognize separating whitespace and
  lowercase keywords. Portable temporal TIMESTAMP remains DATETIME; native ASE
  TIMESTAMP remains the distinct ROWVERSION model.

## Metadata and compatibility

FLOAT catalog precision is preferred over physical byte length. If precision is
absent, four-byte storage maps to FLOAT(15) and eight-byte storage to FLOAT(48),
retaining the corresponding ASE storage class. LOB locator widths are no longer
treated as TEXT/UNITEXT/IMAGE content capacity.

The existing two-argument `SybaseUtils.getMaxLength` cannot infer the server's
national character width. For a nonempty NCHAR/NVARCHAR length, callers must use
the new three-argument overload with @@ncharsize; missing or inconsistent widths
fail instead of assuming two bytes. Column/domain readers supply the width from
their bulk queries. Existing method signatures and Schema XML formats remain
available; type classifications, defaults and generated type spellings change as
described above. Previously saved Schema models are not automatically rewritten.

This does not expand support for DATE/TIME/BIGTIME/BIGDATETIME or introduce
version-specific resolver classes. Target page-size-specific character limits
and live server/JDBC compatibility remain outside this offline review.

## Tests

Use Java 25 and the repository wrapper:

```powershell
.\gradlew.bat :sqlapp-core-sybase:test --tests '*SybaseDataType*Test' --tests '*SybaseDomainPrecisionTest' --console=plain
.\gradlew.bat :sqlapp-core-sybase:test --console=plain
```

Tests cover type resolution, portable/source-native model inputs, CREATE TABLE
generation, numeric bounds, unsigned values, IDENTITY, FLOAT storage widths,
national/Unicode character sizes and LOB capacity. They use local unit tests and
mocked metadata; no external database or container was accessed. No dependencies
or tracked generated files were intentionally changed.

On 2026-11-01, all 105 module tests passed, including 35 new type matrix and
metadata cases, with no failures or skipped tests. External ASE integration tests
in `sqlapp-core-dialect-test` were not executed.

### Server follow-up (2026-11-02)

The targeted server round-trip test now passes against a disposable SAP ASE
container reporting JDBC product version 16.3. It executes generated DDL, checks floating and signed/unsigned
integer values (including UNSIGNED BIGINT's maximum), reads the actual catalog,
checks character lengths, and recreates every tested column from its metadata.
The server exposed the internal catalog aliases USMALLINT/UINT/UBIGINT; these
now resolve to unsigned model types and generate valid UNSIGNED declarations.
All 108 module tests pass. This qualifies the tested declarations on this
engine, not every ASE version, page size or character set. See the
[cross-dialect validation guide](../docs/development/dialect-data-types.md).

## References

- [ASE system types and ranges](https://help.sap.com/docs/SAP_ASE/b65d6a040c4a4709afd93068071b2a76/aa351f76bc2b1014ab5ac21dae4e7dcd.html)
- [ASE numeric defaults](https://infocenter.sybase.com/help/topic/com.sybase.infocenter.dc36271.1600/doc/html/san1393050437990.html)
- [Character storage widths](https://help.sap.com/docs/SAP_ASE/5b36fd3f1aa440818ee6a7c9900d3e87/aabd30abbc2b101482f0cf7002363217.html)
- [ASE type mappings](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-data-access-guide/data-type-mappings-for-sap-hana-cloud-sap-adaptive-server-enterprise)

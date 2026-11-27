# SAP ASE: Docker-free enhancement status

Scope: sqlapp-core-sybase, SAP ASE Transact-SQL. No Docker/container or external
database was used. Existing APIs, XML formats, dependencies and dialect resolver
selection are preserved. This audit does not claim complete ASE feature coverage.

## Implemented and locally verified

| Area | Validation |
| --- | --- |
| Procedure/function/view chunks | Owner identity, significant whitespace, requested missing source, XML to CREATE |
| Source availability | LEFT JOIN retains headers; missing/blank requested text fails; text-disabled reads remain possible |
| Hidden/numbered source | Hidden source and numbered procedure groups beyond group 1 fail explicitly |
| Triggers | Statement orientation, masked header events/timing, XML to native CREATE |
| CHECK | Chunk concatenation before unwrapping, empty-source failure, type C filter avoids ASE 16 trigger collision |
| Columns | At most two bulk queries, default/computed source deduplication, no source-join row multiplication |
| Types | Numeric catalog precision in columns/domains, XML precision retention, locale-independent normalization |
| Identity/computed DDL | Native CREATE and ALTER ADD syntax; unsupported identity seed/increment declarations fail |
| Indexes/constraints | Declarative unique classification, key direction, DOL clustering, composite owner/table identities, XML |
| Version boundaries | Existing pre-15 and 15+ column resource selection and resolver coverage |

The tests validate Java behavior, catalog-query structure and generated SQL.
They do not execute catalog SQL or generated DDL on ASE. Source references are
listed in README.md. The DOL status2 bits were checked against ASE 12.5 and 15.7
documentation. The CHECK/trigger status overlap is documented for ASE 16.

## Remaining work requiring a server or further design

- Execute catalog queries on ASE versions actually supported by users; verify
  driver values, permissions, hidden source and exact DEFAULT/computed text.
- Recreate objects and compare observable behavior, including multi-trigger
  execution order and version-specific syntax.
- Validate charset-dependent national-character lengths and native types.
- Validate storage/index options and function-based index representation.
- Validate cross-database foreign keys through the active JDBC metadata reader.
  SybaseTableReader uses JdbcForeignKeyConstraintReader; the separate legacy
  SybaseForeignKeyConstraintReader is not the active table-reading path.
- User-defined type recreation, bound rules/defaults and owner context need a
  separate design based on sp_addtype/sp_droptype; numeric metadata fixes do not
  establish that full recreation path.
- Restore identity current values separately when required. CREATE currently
  describes identity generation and does not restore a running counter.
- Define computed-column ALTER MODIFY behavior separately from CREATE/ADD.

Legacy SQL Server-style comment helpers and unused resource files do not prove
ASE support. They were not activated or treated as the production reader path.
No settings, dependency versions or intentional generated source files changed.

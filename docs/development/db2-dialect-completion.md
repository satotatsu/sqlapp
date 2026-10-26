# Db2 dialect completion gates

The target is comparable reliability and coverage for shared Schema features,
plus faithful recreation of Db2-specific objects. It does not mean implementing
PostgreSQL-only SQL constructs in Db2. Passing unit tests alone does not close
the live compatibility gate. See [implementation evidence](db2-dialect-parity.md).

## Current implementation

Supported logical XML indexes retain pattern/type/value-mode metadata and native
recreation; database-managed physical XML indexes are excluded. Conflicting or
unsupported XML forms still require complete DDL.

The DB2 module now has bulk metadata and native recreation coverage for ordinary
tables/columns/constraints/indexes, views/MQTs, routines, triggers, sequences,
aliases, roles/grants, native types, variables, module headers/member inventories and condition/variable/type recreation, federation
snapshots and row/column access control. Coverage is versioned; a modern feature
does not imply support in every historical Db2 release. Complete executable DDL
is the supported fallback for forms whose components cannot be read reliably.

Native ALTER now covers modeled sequence configuration without implicit restart,
regular index compression without replacement, and comment changes on sequences,
indexes, views, triggers and aliases. Structural replacement remains explicit
where preserving dependencies, privileges or execution order is required.

Current verification: Java 21, 313 DB2 unit tests passed; the updated
Docker-test sources compiled successfully. Live tests remain unexecuted.

## Remaining gates

| Gate | Remaining work | Completion evidence |
| --- | --- | --- |
| Vendor metadata/recreation | Broader routine/prototype/cursor/PLSQL module-member reconstruction, generated type-support function association and member comments; unsupported XML forms/text indexes and uncovered physical options; complex nickname and mapping reconstruction; federation external user repositories | Bulk catalog fixtures, version boundaries, XML/clone and executable recreation for each newly supported form, with documented full-DDL fallbacks |
| Security coverage | Security labels/policies, instance authorities, newer schema backup/restore RCAC authority and method privileges | Vendor/version-specific catalog and SQL tests; preserve unsupported/unavailable values explicitly |
| Migration semantics | Broader table/column/type/routine ALTER; cross-schema dependencies, alias chains and module-member ordering; specialized object replacement | Same semantics through direct, Table, Schema and Catalog APIs; no partial or silently incomplete SQL plans |
| Live Db2 compatibility | Run disposable 11.5 and 12.1.5 metadata/recreation/ALTER tests; add historical-version checks for the supported version contract | Actual execution results, reread metadata, sequence progress and operation/query-count assertions; fix failures before claiming compatibility |
| Final comparison | Review DB2 and PostgreSQL readers/factories by shared Schema capability and document accepted vendor differences | No unexplained high-priority gaps in the agreed common feature set; reproducible test commands and known limitations |

These gates are work categories, not a promise that one chat turn closes one
gate. Earlier turn-count estimates were preliminary; the remaining work has not
yet been exhaustively sized. A gate stays open until its evidence exists.

## Validation boundary

Unit tests use synthetic catalog rows and Schema models. The Docker-test source
set contains real metadata/DDL and native ALTER assertions for the existing
11.5 and 12.1.5 fixtures. Compiling that source set does not run it. Running an
external database or starting a container needs an explicitly authorized target;
none has been executed as part of this continuation.

The ALTER live fixture uses an isolated schema, checks generated SQL contains no
implicit CREATE/DROP/RESTART, rereads configuration/comments, and checks sequence
progress after changing increment/cache/order. Index compression changes only
the catalog setting; physical compression still requires a separate REORG and
is not performed by the generated ALTER plan.

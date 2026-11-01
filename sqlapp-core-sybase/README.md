# SAP ASE dialect enhancement

This module targets SAP ASE Transact-SQL, not SAP IQ or SQL Anywhere.
This batch uses local unit tests, mocked catalogs, Schema XML round trips and
SQL generation. No Docker image was downloaded or started and no external
database was contacted.

## Metadata and recreation

- Source chunks are grouped by catalog, owner and name, preserving whitespace.
  System source is ordered by number, colid2 and colid. Hidden or missing
  requested source fails explicitly. Numbered procedure groups beyond group 1
  require separate extraction and fail explicitly.
- View headers do not require absent information_schema fields. Complete view
  DDL is retained when either text-reading option is enabled.
- Routine, view, trigger and CHECK queries retain object headers with a LEFT
  JOIN when syscomments text is missing. Requested missing/blank source fails
  explicitly instead of silently omitting the object. Routines, views and
  triggers can still be read with both text options disabled.
- CHECK selection also verifies sysobjects type C. ASE 16 trigger status bits
  overlap the CHECK status bit and must not be interpreted alone.
- Triggers use STATEMENT orientation. Event/timing extraction masks comments,
  strings and identifiers and reads the header. Header-only reads do not invent
  timing. The documented tr.deltrig parent-table join is retained.
- Complete routine and trigger definitions survive XML and generate CREATE
  directly. Missing definitions or definitions of the wrong kind fail.
- CHECK chunks are joined before removing enclosing parentheses.
- Column headers and default/computed source use at most two bulk queries,
  avoiding N+1 queries and multiplication of source chunks by joins.
- DEFAULT expressions are extracted from expressions, DEFAULT clauses and
  CREATE DEFAULT definitions. Computed expressions retain their source.
- Unknown identity seed/increment/current values remain null. NUMERIC/DECIMAL
  precision uses catalog precision rather than storage bytes.
- Numeric precision selection also applies to user-defined types and the
  routine-argument conversion helper. Unknown numeric precision is not replaced
  with storage bytes. Type normalization is independent of the JVM locale.
- Empty CHECK definitions fail explicitly, including empty parentheses.
- UNIQUE constraints use the declarative-constraint status2 bit instead of
  treating every unique index as a constraint. Standalone unique indexes remain
  available through the index reader. Constraint key direction is read with
  index_colorder rather than hardcoded ASC. Both index and constraint queries
  recognize DOL clustered indexes using status2 bit 512, in addition to indid 1.
  These catalog bits are documented in ASE 12.5 and 15.7; live execution remains
  unverified. Retrieval remains set-based with no additional queries.
  Table-header has_clustered_index uses the same DOL-aware condition.
- Column CREATE and ALTER ADD emit native IDENTITY and computed-column
  AS expression MATERIALIZED / NOT MATERIALIZED syntax. Explicit NULL applies
  to ordinary nullable columns. Custom identity start/increment values cannot
  be declared in this syntax and are rejected rather than discarded.
- Missing national-character lengths remain unknown instead of causing an NPE.

Changes stay in sqlapp-core-sybase, add no dependencies and preserve existing
public method signatures and Schema XML formats. Source utility and definition
factory classes are additive. Resolver selection is unchanged. The existing
pre-15 versus 15+ column query boundary is retained: computedcol and status2
remain confined to the modern query. Computed-column generation requires a
server version supporting computed columns; older targets must omit formulas.
This does not establish live compatibility across all supported ASE versions.

## Validation and remaining limits

Tests cover owner identity, chunk boundaries, hidden/missing source, triggers,
CHECK assembly, query counts, catalog version boundaries, unknown identity
values, XML round trips and native CREATE / ALTER ADD output.

Live ASE verification remains necessary for catalog flags and expression text
formats, actual recreation and behavior, permissions and hidden source,
multiple-trigger ordering, charset-dependent national-character lengths,
storage options and older-version compatibility. CREATE does not restore an
identity current value. Computed-column ALTER MODIFY is outside this batch.
Descending-index reading already exists in the active aseIndexes.sql query;
indexes.sql is not the active reader query.

## Specification references

- [CREATE TABLE, identity and computed columns](https://infocenter.sybase.com/help/topic/com.sybase.infocenter.dc36272.1550/html/commands/X80969.htm)
- [syscomments](https://help.sap.com/docs/SAP_ASE/ad4a1ddf1bf34768841bd09d1eddf434/ab9015dfbc2b1014b72df036ad600ead.html?version=16.1.0.2)
- [syscolumns flags](https://infocenter.sybase.com/help/topic/com.sybase.infocenter.dc36274.1600/doc/html/san1393052467836.html)
- [Statement triggers](https://help.sap.com/docs/SAP_ASE/4c45f8d627434bb19e10dd0abbb757b0/a680e6f7bc2b1014a28be1c1cd973be4.html?version=16.0.0.0)
- [DEFAULT source representations](https://userapps.support.sap.com/sap/support/knowledge/en/2890715)
- [systypes storage length and numeric precision](https://infocenter.sybase.com/help/topic/com.sybase.infocenter.dc36274.1570/html/tables/X37714.htm)
- [ASE 12.5 sysindexes status bits](https://infocenter-archive.sybase.com/help/topic/com.sybase.dc36271_36272_36273_36274_1250/html/refman/X22611.htm)
- [ASE 15.7 sysindexes status bits](https://infocenter.sybase.com/help/topic/com.sybase.infocenter.dc36274.1570/html/tables/X22611.htm)
- [ASE 16 sysconstraints and overlapping trigger flags](https://help.sap.com/docs/SAP_ASE/ba2e4785b61c4e3a9373e1671750b34d/ab907056bc2b1014ad3d868ac1c31ecf.html)

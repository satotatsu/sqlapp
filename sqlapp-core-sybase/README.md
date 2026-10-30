# SAP ASE dialect enhancement

This module is the next enhancement target after Informix. The initial scope
is SAP ASE metadata retrieval, Schema XML and native object recreation. SAP IQ
and SQL Anywhere are not included in this assessment.

## Confirmed initial fixes

- View, function and procedure source chunks are grouped by catalog, owner and
  name, so adjacent same-named objects under different owners remain separate.
- Chunk text is read without trimming trailing whitespace. A split string
  literal retains its significant spaces.
- Requested SQL text that is unavailable fails explicitly. Disabling both text
  options permits header-only reads.
- ASE view headers are built from the columns returned by views.sql; retrieval
  no longer depends on information_schema-only IS_UPDATABLE metadata absent
  from that query. View updatability is not inferred by this change.

The existing set-based queries and resolver selection are unchanged. These
fixes add no SELECTs, dependencies, public API changes or Schema XML format changes.
Regression tests are in
[SybaseDefinitionReaderTest](src/test/java/com/sqlapp/data/db/dialect/sybase/metadata/SybaseDefinitionReaderTest.java).

## Next validation scope

1. Add a dedicated metadata Docker suite using the existing ASE 16 test fixture.
   The repository already has ASE Docker coverage for bulk operations and
   tree-data sessions; that does not establish metadata recreation coverage.
2. Exercise long definitions, same-named owners, view/function/procedure
   recreation and observable behavior through XML.
3. Check trigger-to-table catalog joins, trigger definition completeness and
   execution behavior against ASE.
4. Verify column defaults/computed definitions spanning catalog chunks,
   identity metadata, constraint identities and native types.
5. Check table/index storage and quoting. Descending-index reading is already
   present in the active aseIndexes.sql path; the older indexes.sql file must
   not be mistaken for the active reader query.

The initial changes have unit coverage. They do not yet establish live ASE
metadata compatibility across the resolver's older supported versions.

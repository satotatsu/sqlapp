# Phoenix local enhancement completion

Status: the Docker-free pass is complete for the scope below. This is not a
claim of complete Phoenix support or live HBase/Phoenix compatibility.

Final local verification: Java 25, repository Gradle Wrapper,
`./gradlew.bat :sqlapp-core-phoenix:test`: 64 tests, 0 failures, 0 skipped.
Changed-reader/factory tests were run before the full module. Initial test
fixture/format assertions were corrected and rerun successfully. Module diff
whitespace checks also passed. No external database tests were executed.

## Locally covered

- DECIMAL precision, CHAR encoding, native unsigned type names, current-value
  and COUNT expressions.
- One-dimensional ARRAY types/capacity through mapping, XML and DDL.
- CREATE TABLE, column families, optional placement properties, native ALTER
  ADD/DROP, explicit failures for unsupported modifications.
- Sequence CREATE/DROP, bulk metadata, legacy/3.1/4.1 catalog boundaries and
  unknown-value preservation without allocating or inferring sequence values.
- Column metadata, family/capacity preservation, primary-key sequence and sort
  direction from existing JDBC metadata results.
- Set-based index header/column retrieval, INCLUDE versus key separation,
  source families, placement/type/state preservation and guarded native DDL.
- JDBC table/view properties and view statement preservation. Complete native
  view definitions can be recreated; incomplete metadata cannot silently emit
  a misleading or empty CREATE operation.

All changes are confined to sqlapp-core-phoenix. No dependencies, existing API
signatures, resolver selection or XML formats changed. Readers/factories and
optional specifics keys are additive. Generated build/test outputs are normal
Gradle outputs; no generated source changes are intentional.

## Live-server verification still required

No Docker/container or external database was used. Mocked JDBC results and
query-template tests do not execute catalog SQL. A real server is required to
verify exact index column encodings, header/catalog permissions, native array
binding, tenant visibility, effective salting and recreation behavior on the
supported versions. Common native DDL targets 4.1+ and the existing 5.3.1 split;
older syntax/catalog paths are isolated but are not live-certified.

## Deliberately outside this pass

- General reconstruction of inherited/additional view columns and mapped HBase
  views from VIEW_STATEMENT alone; provide complete CREATE DDL.
- Functional/internal index keys, inactive-index state transitions and local
  index storage/salting combinations that cannot be recreated reliably here.
- Multiple source columns with identical simple names in different families:
  current Schema column identity cannot represent these without ambiguity.
- Automatic tenant connection selection and sequence counter restoration.
- New Phoenix types/features requiring further version splits or shared models.
- ALTER of native table properties, primary-key extension and unsupported
  rename/type/constraint changes; these require a separate migration design.

Remaining limitations fail explicitly where generating SQL would lose required
semantics. Specification links, APIs and examples are recorded in README.md.

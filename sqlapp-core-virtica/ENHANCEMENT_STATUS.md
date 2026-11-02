# Vertica enhancement status

## Implemented and locally verified

- Resolver paths retained for 7.x, 8.x, 9.x, 11.1.1+, 12.0.4+ and current versions.
- Native numeric/long/temporal type recognition and size-preserving XML/DDL.
- SQL function statement/arguments/return recreation; quoted argument parsing and
  unnamed multiword UDx type recognition; full CREATE definition precedence.
- Routine script splitting, including nested CASE and untagged dollar literals.
- View query/output columns/local-temporary state and 11.1.1+ privilege inheritance.
- Sequence and identity allocation-state handling and exact cache conversions.
- CHECK predicates, explicit 7.2+ enforcement state and table-scoped composite FKs.
- Fresh Flex CREATE, native partition expression output and explicit temporary
  COMMIT action; independent temporary/Flex flags; shared Flex XML parsing fix.
- Text-index CREATE for explicitly supplied ID/text/payload columns, with guards
  against incomplete catalog metadata and unsupported options.
- Scheduled-event raw metadata and separate trigger/schedule enable states.

Projection round-trips were additionally verified on a dedicated local Vertica
25.1.0-0 server on 2026-11-03, including single-node hash segmentation/KSAFE 0,
encoding, sort order and quoted names. Additional live round-trips cover SQL
function overloads, views, sequences, numeric/long/temporal columns, Identity,
enabled CHECK and partition expressions, with composite foreign-key metadata.
See INTEGRATION_TESTS.md. Other features retain local/mock verification only.
Mocked query counts establish bulk execution rather than historical compatibility.

Live-driven fixes: DROP FUNCTION now emits modeled argument types (including an
empty signature for zero arguments), Identity uses IDENTITY instead of BIGINT
IDENTITY, and temporal metadata uses DATETIME_PRECISION instead of numeric scale.
TIME/TIMETZ/TIMESTAMP/TIMESTAMPTZ default to six fractional digits in generated
DDL, matching Vertica. Public APIs and XML formats are unchanged; generated SQL
and read temporal attributes are corrected for existing resolver paths.

## Unresolved implementation work

| Area | Concrete limitation | Required next work |
| --- | --- | --- |
| Projections | Model, bulk/full metadata, HTML, named DROP, rename-only ALTER and atomic CREATE mapping are implemented; single-node UNSEGMENTED and hash SEGMENTED/KSAFE 0 round-trips passed on 25.1.0-0 | Multi-node buddy/KSAFE 1+ and older versions require live qualification; session-dependent scripts, follow-up ALTER and logical base/buddy grouping remain outside the current path |
| Existing Flex/external tables | Automatic columns, generated dependent objects and external COPY options are not faithfully represented | Model dependencies and external definition semantics, or introduce an explicit vendor-DDL artifact path; verify exported DDL against a server |
| Procedure/UDx recreation | Catalog metadata does not provide sufficient executable body/library definition | Acquire full CREATE DDL through an authorized bulk vendor export; current full-definition input works for routines |
| Text-index catalog recreation | Catalog omits original ID/payload list and tokenizer/stemmer signatures | Bulk vendor DDL acquisition or supplemental canonical metadata; current reader output intentionally fails CREATE when incomplete |
| Scheduled triggers | Event is not a complete representation of schedule + trigger + procedure binding/owner | Define separate reusable schedule identity/lifecycle and reliable procedure-call representation before generating CREATE/DROP |
| Sequence/identity runtime | Original START and last-issued value cannot be inferred from CURRENT_VALUE | Keep allocation state separate; require an explicit runtime-state capture policy if restoration is needed |
| Older catalogs | Local version-boundary tests do not validate real 7.x/8.x catalog availability | Qualify representative older servers before claiming historical live compatibility |

These are genuine remaining features, not completed by validation guards.
EXPORT_OBJECTS can export a scoped object set in one top-level SELECT. Integrating
that output must preserve dependencies, prevent duplicate creation of generated
objects, and retain a clear distinction between model metadata and executable DDL.

## Live verification still required

Use a separately authorized disposable Vertica target, dedicated test schema,
explicit credentials and schema cleanup. Prior MariaDB Docker authorization does
not authorize Vertica. Before running, state the target/version, authentication
source and exact cleanup scope. Existing databases/data must not be used.

Verify: native types and precision; sequence/identity metadata; SQL function
CREATE/read/XML/recreate/script splitting; permanent/local views and inheritance;
enabled/disabled constraints and composite FKs; Flex dependencies and partition
expressions; text-index catalog/DDL; procedure, schedule and projection exports.
Catalog-only tests should assert bulk query counts where practical. Round-trip
tests must compare recreation behavior, not only SQL string snapshots.

Reference: [EXPORT_OBJECTS](https://docs.vertica.com/11.1.x/en/sql-reference/functions/meta-functions/catalog-management-functions/export-objects/).

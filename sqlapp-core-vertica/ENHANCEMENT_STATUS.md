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
| Existing Flex/external tables | Native Flex batch restore now preserves unchanged generated defaults by bulk-export/exact-DDL verification, with source __raw__ length/nullability stored in Table/Column and compared at runtime. Multiple Flex tables, custom added projection, keys/view links, INSERT/view rebuild and implicit Identity passed on 25.1.0-0 without AHM changes | Requires a contiguous Flex CREATE batch before generated child/projection DDL and matching generated definitions/raw attributes. Modified generated designs fail without replacement; populated keys/data, external Flex, older/multi-node configurations remain outside qualification. Generic CREATE still rejects unsafe direct replay |
| Procedure recreation | 11.1.1+ full reads now bulk-export exact native overload signatures and atomically assign complete CREATE DDL; 25.1.0-0 INT/VARCHAR XML/DROP/CREATE/CALL passed | Older live qualification and non-native argument types remain outside this path |
| UDx recreation | Kind/raw signatures retained; kind-specific DROP and complete-definition CREATE guards tested. Parser metadata/XML/DROP/CREATE passed on 25.1 using its installed FlexTableLib | Library/dependency artifacts and grants still require a separate explicit path; other UDx kinds and historical servers lack live qualification |
| Text-index recreation | 25+ full reads bulk-export complete CREATE definitions; XML/DROP/CREATE/search with payload passed on 25.1.0-0 | Older full reads remain catalog-only pending live export qualification; custom tokenizer/library deployment and privileges remain prerequisites |
| Scheduled triggers | Existing-object state restore and the high-level VerticaSchemaRestorer fresh-schema DDL + state restore passed on 25.1.0-0, including XML, disabled states, unattached schedules and recreated Procedure CALL | Requires a stopped scheduler, exclusive drained maintenance window, absent target schema and a complete state snapshot. Bare procedure references require explicit Event.PROCEDURE_SCHEMA_NAME; external schedules and unsupported statement kinds are rejected. Generic CREATE generation retains its scheduling guard; no standalone shared Schedule model was added |
| Sequence/identity runtime | Original START and last-issued value cannot be inferred from CURRENT_VALUE | Keep allocation state separate; require an explicit runtime-state capture policy if restoration is needed |
| Older catalogs | Local version-boundary tests do not validate real 7.x/8.x catalog availability | Qualify representative older servers before claiming historical live compatibility |

These are genuine remaining features, not completed by validation guards.
UDx metadata follow-up passed 129 module unit tests: the one-query reader and
Schema XML retain same-name/same-signature objects of different kinds. Raw
multi-column return signatures are preserved without inventing a single scalar
return type. Polymorphic transform signatures retain their empty DROP signature.
This follow-up did not run a real database; the 25.1 Parser qualification above
belongs to the preceding integration run.
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

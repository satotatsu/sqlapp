# Projection live qualification

This test is destructive within its own newly created test schema. Obtain explicit
authorization for the target, credential source and cleanup before running it.
The regular `test` task always excludes VirticaProjectionIntegrationTest.
An explicit integration execution is required to qualify a server version.

After authorization, configure the process environment (do not put secrets in
source control or JDBC URLs):

- `SQLAPP_VERTICA_PROJECTION_INTEGRATION=true`
- `SQLAPP_VERTICA_URL=jdbc:vertica://127.0.0.1:<explicit-port>/<disposable-db>`
- `SQLAPP_VERTICA_USER=<authorized-test-user>`
- `SQLAPP_VERTICA_PASSWORD=<authorized-test-password>`

Run `./gradlew :sqlapp-core-vertica:projectionIntegrationTest` (Windows:
`./gradlew.bat`). This task is not a dependency of normal module tests.
The JUnit environment gate must also be enabled; otherwise the test is skipped.
Creating/starting a container or pulling an image is a separate action requiring
an explicitly chosen target. No container launcher is included here.

The test validates the URL as loopback with an explicit port/database, connects
using separate user/password properties, checks the product, and creates a random
`sqlapp_projection_it_<uuid>` schema. CREATE SCHEMA has no IF NOT EXISTS: a name
collision cannot authorize cleanup of an existing schema. It creates its table
and a custom non-superprojection, reads its full model/DDL, round-trips XML,
drops/recreates that projection and reads it again. It drops only the newly
created schema with CASCADE in cleanup. Failures during cleanup are retained,
including as suppressed failures when the main test also fails.

Only the test's JDBC connection, statement and result sets are closed. Any supplied
server/container is managed by its owner; container/volume removal is not performed
by the test. Use only a disposable authorized instance, never production or a
shared application database.

## Verified run (2026-11-03)

The explicit task passed against Vertica Analytic Database v25.1.0-0 in a
dedicated local `ratiopbc/vertica-ce:v25.1.0-0` container, with loopback-only
port binding and generated test credentials. The test verified bulk metadata and
EXPORT_OBJECTS acquisition, XML round-trip, generated DROP/CREATE and rename.
It also covered names containing a dot, space and double quote, and a full-schema
read including the automatic table projection. JDBC auto-commit is used; issuing
an explicit COMMIT in this mode is rejected by the driver.

The final integration run had one test, zero failures and zero skips. No test
schemas remained afterward; the dedicated container and anonymous volume were
removed. A subsequent run expanded the same test to cover single-node SEGMENTED
BY HASH / KSAFE 0, RLE encoding and sort order different from output order.
It also verified XML/DDL recreation of sequences, views, overloaded SQL functions
with nested CASE, and a typed/partitioned table with Identity and enabled CHECK.
Composite foreign-key column order was read from the real catalog. Timestamp
values retained all six fractional digits after table recreation. The final module
test run passed 87 unit tests; the extended integration task passed one test with
zero failures/skips. Intermediate runs exposed and drove fixes for missing DROP
FUNCTION signatures, invalid BIGINT IDENTITY output and temporal precision loss.
The first connection attempt preceded container user initialization and failed
authentication; the task was rerun after the image reported readiness.

This qualifies single-node segmented and unsegmented projections, not multi-node
buddy/KSAFE 1+ availability or older server versions. Sequence/Identity original
START and last-issued values are not restored by this test.

The next extension passed on the same local 25.1.0-0 image: two stored-procedure
overloads (INT/VARCHAR) were bulk-exported through the full reader, serialized to
XML, dropped/recreated and called to verify inserted results. Bodies include a
literal semicolon. Full definitions were also supplied explicitly for a Flex
table and an external table reading only the container's `/dev/null`; their
XML/DROP/CREATE/SELECT paths passed. No external data source was accessed. The
first Procedure test body needed PL/vSQL PERFORM; a subsequent export failure
identified and fixed accidental VARCHAR(1) column defaults in overload scopes.
The final integration task passed one test, zero failures/skips; module unit tests
passed 92 tests. Schema count was zero after cleanup, and the dedicated container
and anonymous volume were removed.

Text-index qualification on 25.1.0-0 additionally confirmed scoped EXPORT_OBJECTS
returns CREATE TEXT INDEX with the original ID/text/payload columns and tokenizer
signature. The final test uses TableReader full metadata to acquire that definition
automatically, XML-round-trips it, executes generated DROP TEXT INDEX / CREATE,
and verifies doc_id plus payload for a keyword search. Its quoted name contains
a dot. The task passed one test with zero failures/skips. Unit tests passed 97
tests, including bulk query count, atomic mapping failure, own-schema handling and
the 24/25 full-read version boundary. Schema count was zero before removing the
dedicated container and anonymous volume. Custom tokenizer/library packaging and
older server export behavior remain outside this qualification.

Schema-level qualification on the same 25.1.0-0 image captured a whole-schema
export in Schema.definition, XML-round-tripped it and replayed the ordinary test
schema after dropping only that new test schema. Supplemental model children
were not emitted twice. Flex export replay was separately tested and failed on
the implicit flex_table_super projection collision; a temporary replacement
experiment then failed AHM history protection on DROP. Neither experiment is
treated as successful Flex restoration. The final test asserts preflight rejection
of the Flex export, removes the dedicated Flex test table, and verifies ordinary
schema replay. No global history/AHM parameter was changed. Quoted schema scope
resolution also failed, so the public schema-export path requires simple names.
Final module verification passed 101 unit tests, and the integration task passed
one test with zero failures/skips. No dedicated test schemas remained, and the
created container and anonymous volume were removed.

Scheduling investigation added two triggers attached to schedules with 2099
timestamps, plus an unattached CRON schedule. It verified Event metadata/XML for
trigger-disabled versus schedule-disabled states and multiple timestamps. Real
schema replay exposed unqualified procedure names and lost enabled states in the
vendor export. An unchanged-state ENABLE call also failed. A conditional DO
experiment ended with EOF and a closed JDBC connection; the server subsequently
refused connections. Its cause was not established, and the experimental replay
code was removed. Only the disposable VMart server was restarted for verification
and cleanup; no global scheduler/history configuration was changed.

The final test verifies the schema-level scheduling-state snapshot and preflight
rejection of scheduled replay. It then explicitly drops only its own triggers and
schedules before testing ordinary schema replay. Automatic execution is avoided
by future timestamps; the unattached schedule has no procedure. Full scheduled
recreation remains unqualified.

Final verification passed 103 unit tests and one integration test with zero
failures/skips. The exact schema left by the interrupted run was identified from
the test failure and catalog, removed, and the test-schema count confirmed zero.
The dedicated container and anonymous volume were then removed.

A later qualification used the Java-side VirticaSchedulingStateRestorer rather
than DB-side DO blocks. It exported/XML-round-tripped the state snapshot, changed
trigger/schedule states in its own schema, and verified refusal while the scheduler
was running. Only within the dedicated disposable VMart container, it set
EnableStoredProcedureScheduler to 0, restored and verified the original states,
repeated restoration with zero applied changes, then restored the global setting
to 1 in finally. It did not execute the scheduled procedures; their timestamps
remained in 2099. This integration run passed one test with zero failures/skips.
The production API only checks the scheduler prerequisite and never changes it.

Final module tests passed 107 tests with zero failures/skips. The container's
scheduler setting was independently confirmed as 1 and test-schema count as zero
before deleting the dedicated container and anonymous volume.

Fresh-schema restoration was subsequently qualified through VirticaSchemaRestorer
on 25.1.0-0. The fixture explicitly supplied Event.PROCEDURE_SCHEMA_NAME for the
bare procedure names emitted by vendor export, XML-round-tripped the model, and
verified that preview refuses unresolved references. It tested running-scheduler
and existing-target rejection, stopped only the disposable container's scheduler,
dropped only its own schema, and restored DDL plus final scheduling states through
the new high-level entry point. Two triggers, three schedules (one unattached),
multiple future timestamps and independent disabled states survived. No scheduled
inserts occurred; a manually invoked restored procedure inserted the expected row.
The ordinary CREATE factory's scheduling guard remains in place. Scheduler setting
was restored in finally and independently confirmed as 1; test-schema count was 0.

Final module verification passed 114 unit tests, and the integration task passed
one test with zero failures/skips. The dedicated container and anonymous volume
were removed after the independent cleanup checks.

Native Flex verification subsequently passed on the same 25.1.0-0 image. A separate
new test schema contained two Flex tables (one with an explicit id, one with implicit
Identity) and a custom projection. Schema export captured complete Flex CREATEs
and implicit raw-column lengths/nullability. The XML model restored through
restoreWithDetails: matching generated CREATEs were verified rather than executed
twice, custom projection DDL ran normally and projection identities matched.
RESTORE_FLEXTABLE_DEFAULT_KEYS_TABLE_AND_VIEW reported existing linked objects;
post-restore INSERT, COMPUTE_FLEXTABLE_KEYS_AND_BUILD_VIEW and virtual-field queries
passed. Dropping the test parent with CASCADE (required for its custom projection)
also removed its linked keys/view. A modified source raw length failed at the
GENERATED_OBJECTS phase before custom-projection execution; the unchanged model
then restored. These checks used no MAKE_AHM_NOW or projection replacement.
Intermediate failures exposed empty-snapshot XML handling and the test's required
CASCADE; both were corrected. One rerun was temporarily blocked by an unrelated
new Gradle module directory while it was being created; no unrelated settings were
changed.

Final verification passed 124 module unit tests and one integration test with
zero failures/skips. Scheduler setting was confirmed as 1 and remaining test
schemas as 0. The dedicated container and its anonymous volume were removed.

UDx follow-up: 127 module unit tests and the explicit integration test passed on
25.1.0-0. A test-schema JSON Parser referencing the image's existing
public.FlexTableLib retained its kind and empty signature through metadata/XML,
rejected incomplete CREATE, and was dropped/recreated from a supplied complete
definition. No library was downloaded or installed. Parser execution, library
packaging, other UDx kinds and historical server qualification remain untested.
Afterward the scheduler value was confirmed as 1, remaining test schemas as 0,
and the dedicated container and anonymous volume were removed and checked absent.

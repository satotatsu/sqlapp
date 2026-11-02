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

Run `./gradlew :sqlapp-core-virtica:projectionIntegrationTest` (Windows:
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

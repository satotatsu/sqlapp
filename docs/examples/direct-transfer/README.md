# Direct transfer example

Use Java 25 and a Gradle version supported by the plugin. Supply a plugin build
containing `transferDatabase`, matching sqlapp dialect coordinates and JDBC driver
coordinates available in the configured repositories. No published release or
driver version is assumed here. Set SOURCE/TARGET JDBC URL, USER and PASSWORD
environment variables referenced by `build.gradle`. Adjust `transfer.yaml` to
your authorized disposable tables. The example's REQUIRE_EXISTING mode requires
existing empty targets. To prepare absent selected tables, set
`schemaMode: CREATE_MISSING` before review, inspect the generated Schema plan and
use the new job fingerprint for execution. The target schema namespace must exist.

For schema-only transfer, edit `schema-only.yaml` to select your tables and use
`-PtransferJob=schema-only.yaml` on both review and execution commands below.
This supports different source/target products using the target dialect, requires
no primary keys, and creates missing tables, constraints and indexes without
copying any rows. Existing tables are skipped with warnings and retain their
definitions and rows. The target schema must already exist. Views, independent
sequences, routines and triggers are not copied; review the scope/vendor warnings
and generated DDL. Do not interpret DDL completion as full database equivalence.

For optional existing-table changes, use `-PtransferJob=schema-differences.yaml`
on both review and execution commands. This job uses
`schemaMode: APPLY_DIFFERENCES`. Inspect CREATE/ALTER/DROP SQL and warnings.
Removals, type/narrowing changes, constraint/index replacements and generated
target-data preparation require `allowDestructiveSchemaChanges: true`; save and
review again to obtain a new pin before executing. Source rows are not transferred.
Partial changes may remain after failure, with no automatic reset, rollback or
data restoration. Unsupported changes are unresolved warnings requiring manual work.

For schema-only name changes, adjust `schema-only-mapped.yaml` and
`name-mapping.yaml` to your captured source identifiers, then use
`-PtransferJob=schema-only-mapped.yaml` on both commands. PK/FK/index references
follow the mapped names; source SQL expressions remain verbatim with a warning.
Review the generated SQL. Keep both files together; the mapping path resolves
from the original job folder. For direct copy with the same name mapping, use
`-PtransferJob=direct-mapped.yaml` on both commands. Usable NOT NULL primary keys
select the keyed path; other direct tables use full-scan INSERT. Mapped targets
must be empty and compatible (or absent with CREATE_MISSING),
and copied data is verified by default. CSV name mapping remains unsupported.

For keyless INSERT with an optional new target-only identity PK, adjust
`keyless-insert.yaml` and use `-PtransferJob=keyless-insert.yaml` on both commands.
Duplicate rows/NULLs are retained and verification compares hash multisets.
MERGE, resume and key-based repair are unavailable. To create a keyless target
without the extra column, choose `CREATE_MISSING` instead.

Review only:

```text
gradle transferDatabase -PsqlappVersion=<version> -PsourceDialect=<dialect-coordinates> -PsourceDriver=<driver-coordinates>
```

For different endpoints add `-PtargetDialect=<coordinates>` and
`-PtargetDriver=<coordinates>`. Inspect `transfer-review.json`, warnings and
resolved targets. Its `review.prepared.job.fingerprint` is the configuration pin.
Keep the same job bytes and explicitly execute:

```text
gradle transferDatabase -PsqlappVersion=<version> -PsourceDialect=<dialect-coordinates> -PsourceDriver=<driver-coordinates> -Pexecute=true -PconfigurationFingerprint=<sha256:...>
```

Execution performs a fresh live review rather than replaying a saved report.
The configuration pin covers job bytes, not mapping-file bytes across separate
task invocations. Inspect a new review after editing either file. Within each
review/execute operation, original and captured mapping fingerprints are checked
and changes invalidate that review.
Inspect `transfer-result.json`, including on failure. Previously committed rows
and created objects may remain; no automatic reset or replay is performed. Both credentials and
drivers are runtime inputs. The source/target DataSources are command-owned by
default; standalone Java callers can set `closeDataSource=false` for shared pools.

The TestKit fixture tests equivalent task wiring against embedded HSQL file
databases in separate runs. This environment-based example was not run against
external databases. See the [task guide](../../gradle-plugin/direct-transfer.md)
for properties, compatibility policy, current boundaries and companion-project
integration notes.

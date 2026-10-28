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
Inspect `transfer-result.json`, including on failure. Previously committed rows
and created objects may remain; no automatic reset or replay is performed. Both credentials and
drivers are runtime inputs. The source/target DataSources are command-owned by
default; standalone Java callers can set `closeDataSource=false` for shared pools.

The TestKit fixture tests equivalent task wiring against embedded HSQL file
databases in separate runs. This environment-based example was not run against
external databases. See the [task guide](../../gradle-plugin/direct-transfer.md)
for properties, compatibility policy, current boundaries and companion-project
integration notes.

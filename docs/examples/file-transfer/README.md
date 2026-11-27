# CSV transfer example

Use Java 25 and a Gradle distribution supported by the plugin. Supply
`-PsqlappVersion=<build-containing-transfer-tasks>` and
`-PjdbcDriver=<group:artifact:version>` and
`-PsqlappDialect=com.sqlapp:sqlapp-core-<db>:<sqlapp-version>` on each invocation.
Keep the dialect and plugin versions aligned. The required plugin
build and driver must be available in the configured repositories; no release or
driver version is assumed here. This example lives in this repository because
the separate `sqlapp-gradle-example` checkout is not present in this workspace.

Prepare disposable/authorized source and target tables with the same product and
matching numeric NOT NULL keys. Adjust `transfer.yaml` for their actual schema and
table names. Set `SOURCE_JDBC_URL`, `SOURCE_JDBC_USER` and `SOURCE_JDBC_PASSWORD` in
the environment, then run:

```text
gradle exportTransferFile -PsqlappVersion=<version> -PsqlappDialect=<dialect-coordinates> -PjdbcDriver=<driver-coordinates>
```

Keep the reported manifest path and fingerprint. The package can be transferred
to another environment; the import side only needs the package and
`TARGET_JDBC_URL`, `TARGET_JDBC_USER`, `TARGET_JDBC_PASSWORD`:

```text
gradle importTransferFile -PsqlappVersion=<version> -PsqlappDialect=<dialect-coordinates> -PjdbcDriver=<driver-coordinates> -PmanifestFile=<package/manifest.json> -PmanifestFingerprint=<sha256:...> -PallowImport=true
```

Review the package and target before setting `allowImport=true`. It authorizes
database writes. The source is not contacted during import. Target tables must
already exist and be empty with the example's `REQUIRE_EXISTING` policy.
To prepare missing ordinary tables, change `schemaMode` to `CREATE_MISSING`
before export, review its generated DDL through the GUI or Java review API, and
retain the package fingerprint. The target schema namespace must already exist.
No automatic clear, resume or cross-product conversion occurs. Inspect the reported audit JSON and verification
result, including when the task fails after committed chunks.

See the [task guide](../../gradle-plugin/file-transfer.md) for exact properties,
failure behavior and current type limits. Automated TestKit coverage exercises
equivalent export/import task wiring in separate Gradle runs against disposable
HSQL file databases; these environment-based examples are not run against an
external database by repository tests.

## Optional target names

For an ITEMS source with an ID column, the included names.yaml maps the target
table to ITEMS_COPY and ID to ITEM_ID, leaving all other fields unchanged. Add
these settings to transfer.yaml before a fresh export:

```yaml
schemaMode: CREATE_MISSING
mappingFile: names.yaml
```

Replace the existing schemaMode entry rather than adding a duplicate YAML key.
The target schema must exist. Review the generated DDL before authorizing import;
existing targets instead need the mapped names and empty-table shape. Export
captures/pins the mapping inside the package and keeps source data field names.
Move the whole package for import; the original names.yaml and source connection
are not required there. Changing the original mapping needs a new export. The
same task invocations above apply. This optional example adds no task properties
or external database test execution.

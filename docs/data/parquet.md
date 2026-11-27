# Parquet data files

Parquet is supported by `ExportDataCommand`, `ImportDataCommand`,
`TableFileReader`, `ConvertDataCommand` and their existing Gradle tasks.
Use `outputFileType = 'PARQUET'` for export/conversion. Import recognizes
`.parquet` files automatically. Each file contains one flat table.

```groovy
import com.sqlapp.gradle.plugins.ConvertDataTask

tasks.register('toParquet', ConvertDataTask) {
    directory = layout.projectDirectory.dir('data/input')
    outputDirectory = layout.buildDirectory.dir('data/parquet')
    outputFileType = 'PARQUET'
    recursive = false
    removeOriginalFile = false
}
```

Run `./gradlew toParquet`. To export database rows, use the existing
`ExportDataTask` connection and table selection settings with the same
`outputFileType`. To import, use `ImportDataTask` with the directory containing
the files and the intended target database. Existing batch, commit, rollback,
conversion and SQL-generation policies still apply. Import is not an
automatically atomic operation across tables or configured commit intervals.

For Java callers, set `DataFormat.PARQUET` on the commands. The shared entry
points are `ParquetTableIO.write(path, table)` and
`FileRowIteratorFactory.create(path)`, installed with
`table.setRowIteratorHandler(...)`. Explicitly close a closeable iterator when
stopping before its end. Commands close their readers on execution failure.

## Types and safety

The shared Schema model supplies output column types. Typed empty tables retain
their columns. For conversion without a Schema, existing text readers infer
columns from their data; CSV normally provides text columns. The first row must
establish every column and its type. Additional columns or type changes are
rejected rather than omitted or coerced. For reliable numeric/date conversions,
convert a typed Table Schema XML, or export directly from the database.
Empty untyped input and untyped all-NULL columns require explicit Schema types.

| Schema value | Parquet representation |
|---|---|
| BOOLEAN / BIT | BOOLEAN |
| TINYINT / SMALLINT / INT | Signed INT32 with integer logical annotation |
| BIGINT | INT64 |
| REAL | FLOAT |
| FLOAT / DOUBLE | DOUBLE |
| Character types | UTF-8 STRING; values must be text |
| Binary types | BINARY; values must be byte arrays |
| DECIMAL / NUMERIC | BINARY DECIMAL with explicit precision and scale |
| DATE | Epoch-day DATE |
| TIME | Local TIME(NANOS); fractional input TIME is rejected because the Schema TIME value cannot preserve it |
| TIMESTAMP / DATETIME | Local TIMESTAMP(NANOS), with checked signed INT64 range (approximately 1677–2262) |

NULL, empty text and empty binary remain distinct. Output fields are optional;
Parquet does not replace Schema XML for database constraints or original vendor
type declarations. Readers retain existing target column definitions, resolve
exact names before unique case-insensitive names, and reject missing, ambiguous,
incompatible or duplicate mappings. Decimal, text/binary length and timestamp
precision checks reject loss in target columns. Unsupported or invalid values
fail rather than being silently converted to strings.

Readers accept supported flat Parquet physical/logical types, including decimal
INT32/INT64/binary encodings and local time/timestamp MILLIS/MICROS/NANOS.
Nested/repeated columns, unsigned integer annotations, INT96, timezone-adjusted
temporal values and other logical/vendor types are unsupported. No timezone is
guessed. Binary files require seekable local File/Path input, not Reader or
an arbitrary InputStream. Zero-byte files are invalid Parquet.

Output uses uncompressed Parquet with a 16 MiB row-group threshold. It processes
rows incrementally, but buffering also depends on row width and dictionary size;
the threshold is not a hard memory limit. Existing JSON/Excel input readers may
materialize data independently. Writers close successfully before replacing the
destination via a temporary file, using atomic replacement where supported.
On failure the previous destination is retained; source removal in conversion
happens only after successful replacement.

## Dependencies and scope

`sqlapp-core` uses Parquet Java 1.17.0 internally, with Hadoop common and
MapReduce client core 3.4.2 for referenced runtime classes. These dependencies
are inherited by command/plugin runtime consumers; this adds distribution size.
Local reads/writes use `PlainParquetConfiguration`. Hadoop's transitive
Woodstox/StAX2 provider and SLF4J binding are excluded so adding Parquet does not
replace the application's XML parser or logging binding.
No Avro model or new sqlapp module dependency is introduced. Public method
signatures expose sqlapp/JDK types rather than Parquet/Hadoop types. Existing
formats, defaults and Schema XML remain unchanged.

This is ordinary file I/O. Verified CSV transfer packages, their manifests,
restart/review protocol, and Swing transfer screens do not gain Parquet support.
Cloud stores, partitioned datasets, append, encryption, compression configuration
and predicate pushdown are outside this initial implementation.

## Verification

Run the core tests first, then the directly related command and Gradle tests:

```text
./gradlew :sqlapp-core:test
./gradlew :sqlapp-command:test --tests '*ParquetCommandsTest' --tests '*ConvertDataCommandTest' --tests '*ImportFileReadingTest'
./gradlew :sqlapp-gradle-plugin:test --tests '*ParquetDataTaskTest'
```

The command tests use an embedded HSQLDB database. They do not require an
external database. Standard logical encoding fixtures use Parquet Java's test
example writer without sqlapp-specific metadata; Spark/PyArrow interoperability
and large-file performance require separate validation.

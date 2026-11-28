# Interval values: staged replacement

`sqlapp-core` provides the independent immutable value
`com.sqlapp.data.interval.IntervalValue`. The legacy `Interval*` APIs now delegate
value arithmetic to this model while preserving their four stored fields.
DataType identifiers, Schema XML metadata and file format selection are unchanged.
No new dependency is required. PostgreSQL JDBC/COPY support for the new value is
described in stage 4; dedicated migration-file adapters remain a later stage.

```java
var value = IntervalValue.parse("P1Y2M-3DT4H5M6.123456789S");
value.months();   // 14
value.days();     // -3
value.timePart(); // Duration: 14706 seconds and 123456789 nanoseconds
value.toString(); // P14M-3DT14706.123456789S
```

## Model and exact operations

The components are signed `long months`, signed `long days` and a non-null
`Duration timePart`. Years become total months, and hours/minutes/seconds become
one Duration. Days are never exchanged for time. Thus `P1D` and `PT24H` are
different values; a 25-hour time part stays in timePart. Source spelling is not
retained. Duration itself uses signed long seconds and a normalized nanosecond
adjustment, avoiding the approximately 292-year limit of total long nanoseconds.

Equality and hashing compare all three components exactly. The type does not
implement Comparable. No database-specific comparator is introduced here.
`plus`, `minus`, `multipliedBy` and `negated` return new values and throw
ArithmeticException on overflow. They do not mutate or saturate components.

`toPeriod()` requires a zero time part and checks Period's int ranges.
`toDuration()` requires zero months and days. It does not assume calendar days
last 24 hours. Nonrepresentable component kinds throw DateTimeException; numeric
overflow throws ArithmeticException.

`requireFractionalPrecision(0..9)` returns the same value if exact and throws
ArithmeticException when nonzero digits would be lost. The PostgreSQL
adapter calls `requireFractionalPrecision(6)` before writing microseconds;
this method alone does not check the database's range or column precision.
For example, `PT1.123456789S` is valid here but fails that six-digit check.

## sqlapp text grammar

This is a sqlapp format based on ISO 8601, with an explicit extension for
component signs. It is not advertised as universally valid ISO 8601 text.

```text
interval = "P" [integer "Y"] [integer "M"] [integer "D"]
               ["T" [integer "H"] [integer "M"] [seconds "S"]]
integer  = ["+" | "-"] digit {digit}
seconds  = integer ["." digit{1,9}]
digit    = "0" | "1" | ... | "9"
```

At least one component is required. If T is present, at least one time component
is required. Units must appear in that order, once each. ASCII lowercase units
are accepted independent of locale. Whitespace, a global sign before P, weeks,
fractional units other than seconds, decimal commas, exponents, and more than
nine fractional digits are rejected with DateTimeParseException. Final numeric
overflow throws ArithmeticException. Components are summed exactly before
checking the final range within the month and time groups.

Canonical output uses only total months M, days D and total seconds S, in that
order. It omits zero components and fractional trailing zeros. Zero and negative
zero both produce `PT0S`. No month/day or day/time conversion occurs. Examples:

| Input | Canonical output |
| --- | --- |
| `P1Y` | `P12M` |
| `P1D` | `P1D` |
| `PT24H` | `PT86400S` |
| `P1DT-1H` | `P1DT-3600S` |
| `PT-0.000000001S` | `PT-0.000000001S` |
| `P-0DT-0.000S` | `PT0S` |

The new parser does not read legacy SQL interval text. Existing Converter and
Interval parsers continue to own those formats. CSV compatibility is tested both
for existing Converter output and for transporting new canonical strings through
CsvWriter/CsvParser. This does not mean the migration transfer importer supports
IntervalValue yet. CSV needs the Schema column type to interpret a string.

## Regression tasks and review stages

Stage 1 adds compatibility tests and executable known defects, with no production
Java changes. Frozen Base64 Java serialization streams cover all 15 existing
Interval classes; do not regenerate them when changing the implementation. They
pin old-to-new reading and the current writer's stream layout. Existing CSV and
Schema XML tests pin the current external formats without endorsing known bugs.

Normal core and PostgreSQL `test` tasks exclude `@Tag("known-bug")`. Run the
explicit failing suites with Java 25 and the Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core:knownBugTest :sqlapp-core-postgres:knownBugTest --continue
```

Failures remain build failures; ignoreFailures and Disabled are not used. Reports
are separate from the normal tests. Move each repaired test to an untagged class
or replace the class-level tag with tags on the remaining failing methods. Do not
leave a passing repaired regression permanently in the known-bug suite. PostgreSQL
tests use driver objects only and do not access a database.

Stage 2 adds only IntervalValue, its format, focused tests and this documentation.
It does not delegate existing Interval methods or change their serialization
fields. Further stages must address old setter semantics, legacy Comparable,
driver-specific ranges and qualifiers, and complete DB/file round trips.

## Stage 3: legacy API delegation

There are no deployed consumers of the legacy implementation. Backward
compatibility is therefore not an acceptance requirement: correctness takes
priority over old erroneous results. The four-field storage was retained in this
implementation because the tested facade does not require a new storage format,
not as a promise that future stages must retain it. Frozen stream and CSV tests
remain useful regression coverage, rather than a constraint on future redesign.

The stored instance fields remain exactly `int months`, `int nanos`,
`boolean positive`, `int seconds`, with every existing serialVersionUID unchanged.
There is no cached value or new serialization field. `toIntervalValue()` derives
a fresh immutable value from stored totals, without calling polymorphic component
getters. Legacy storage still combines calendar days into total seconds and is
limited to nonnegative int magnitudes. It cannot retain independent mixed signs
or the distinction between a calendar day and 24 hours. Use IntervalValue for
those cases. Bounds violations now throw ArithmeticException instead of wrapping.

Constructor policy:

- All nonzero arguments must share a sign. All-negative arguments construct a
  negative interval, including negative fractional seconds or nanos.
- Mixed-sign arguments throw IllegalArgumentException with guidance to use
  IntervalValue. They are not converted to absolute values or silently netted.
- A zero constructor value is positive, including negative floating-point zero.
  Explicit `setNegative()` still supports legacy negative zero and its frozen
  serialization. Existing equals/order distinguish explicit negative zero.
- Component setters remain magnitude setters and preserve the selected sign.
  Existing replacement rules are retained: for example, `setMonths(26)` replaces
  total months, even when years are already present. Interval-to-interval addition
  uses totals rather than these setters, so month carries cannot overwrite years.
  Integer setters preserve lower components; fractional setters replace lower
  units when a fractional component is supplied. `setSeconds(double)` replaces
  the fractional seconds even for a whole-valued double.

Addition policy:

- `add(Interval target)` continues to mutate its argument, not its receiver.
  Signed values are added through IntervalValue; unrepresentable mixed month/time
  signs fail before changing the target. Legacy subtype projection remains in
  effect (for example YEAR TO MONTH ignores day/time fields).
- Calendar and timestamp overloads add **total months in one operation first**,
  then calendar days, then time. They do not mutate input dates. Calendar preserves
  its timezone and end-of-month rules. This intentionally changes the old
  time/day/month/year order and makes it agree with java.time month addition.
- Calendar/Date/Time operate at millisecond precision; sub-millisecond signed
  time is truncated toward zero. Timestamp and capable java.time objects retain
  all nine fractional digits, including carry and borrow across second boundaries.
- Legacy total seconds are reconstructed as days plus a sub-day time remainder.
  Therefore a legacy 24-hour amount behaves like a calendar day across DST, while
  IntervalValue itself continues to distinguish these values.
- LocalDate retains the previous date-only projection: sub-day remainders are
  ignored. Month/YearMonth overloads project to months. MonthDay uses the explicit
  reference leap year 2000 for arithmetic and end-of-month handling.

Equality is exact to the nanosecond and matches hashing. Legacy Comparable remains
a signed lexicographic order over (months, seconds, nanos), reversing magnitude
comparison for negative intervals. This is not PostgreSQL or generic SQL ordering.

`IntervalYearToMonth.toYearMonth()` retains valid positive absolute year/month
component mappings. A negative period or month component zero has no unambiguous
absolute-date mapping and raises DateTimeException directing callers to the new
`toYearMonth(YearMonth base)` overload. The overload adds signed total months to
the explicit base; it does not guess an epoch.

Legacy SQL parsing now accepts leading precision annotations and a trailing decimal
point on seconds, retains fractional day/hour/minute values, and validates leading
and fractional precision. Null/blank is not parsable. `isParsable` attempts actual
parsing and checks storage range rather than only matching a permissive regex.
Unknown units, punctuation, trailing garbage, and lossy fractions are rejected.
Previously supported typed lower-field projections and space-separated date parts
remain supported. The complete six-component base `toString()` representation is
also unambiguous and can be read back without a unit suffix. A nonblank bare scalar
requires a typed parser such as IntervalSecond.parse; the base parser does not
guess its unit or silently return null. Null/blank base input still returns null.

Legacy output retains its existing unit layout, with bug fixes for duplicated hours
and lost fractional seconds. Fractional output is now exact to nine digits instead
of rounded to six. This may change affected CSV field text but not file format
selection. IntervalValue's `PT86400S` output is unchanged in this stage; selecting
the future file interchange format is deferred until the file-I/O stage.

The 15 original core regressions and 15 newly reproduced defects/specification
cases now run untagged in normal tests. The PostgreSQL driver mixed-sign regression
was left tagged at the end of stage 3; stage 4 below repairs it. No external database
is required by these regressions.

## Stage 4: PostgreSQL values through JDBC, Schema rows and COPY

PostgreSQL adapters now return IntervalValue. ToPGIntervalConverter returns a
PGobject with type interval, with independently signed integer month, day and
microsecond fields. For example, one calendar day minus one hour is transmitted
as `+0 mons +1 days -3600000000 microseconds`. Neither day/hour nor month/day
normalization is performed, so no conversion warning is necessary.

PostgresIntervalCodec validates signed int32 months/days, signed int64
microseconds, and exact fractional precision. A ninth-digit fraction cannot be
silently rounded to six digits. Infinite intervals and their reserved field
combinations are unsupported and fail explicitly. Time is written as an integer
microsecond quantity, avoiding decimal/double conversion even at the int64 bounds.
Reads recognize postgres, postgres_verbose, sql_standard and designator ISO server
output. SQL-standard's lone leading negative sign is applied to the whole unlabeled
day/time value; explicit component signs are preserved. This is not a general
PostgreSQL expression parser: SQL literals, arbitrary unit aliases, fractional
months and non-finite values are not accepted as JDBC value strings.

JDBC reads use ResultSet.getString rather than constructing PGInterval, whose
hour and whole-second fields are narrower than PostgreSQL's storage. Direct driver
objects are still accepted via exact integer second/microsecond accessors. If an
application has already rounded a value while constructing a PGInterval, the
original digits cannot be recovered. Canonical input is validated before binding.

The handler is registered for generic interval and every supported qualifier,
including older dialect resolutions (8, 11, 15 and 18 covered by tests). Only
existing interval text syntax is used; no new catalog queries, dependency versions,
or version-specific metadata columns are introduced. Server execution is not part
of these unit tests; an external database has not been used.

PostgreSQL qualifiers reject nonzero fields to the right of the last allowed
unit instead of relying on server truncation. PostgreSQL permits fields above the
first named unit, so these checks intentionally differ from SQL-standard target
conversion. Explicit fractional column precision 0..6 is checked when Column
metadata is available. A new default JdbcTypeHandler overload accepts the Column;
normal SQL binding and portable batch INSERT forward this metadata. Existing
handlers retain their original behavior through the default implementation.
Type-only binding has no knowledge of a physical column's lower precision and
uses PostgreSQL's default six digits. Call bindColumn or use
PostgresIntervalJdbcTypeHandler(Column) when binding outside those metadata paths.

Schema interval columns preserve immutable values from modern adapters instead
of converting them back to legacy subclasses. The column converter also recognizes
canonical sqlapp strings, and its formatter emits canonical text for IntervalValue.
The new value's Java class is registered for INTERVAL parameter-type inference.
Legacy values and their typed parsers remain available. DataType identifiers,
qualifiers and XML metadata are unchanged. This common bridge does not make
unmigrated database adapters understand mixed-sign or wide-range values.

PostgreSQL COPY uses the same codec as JDBC, including column precision and field
checks. COPY interval arrays preserve dimensions, null elements and component
signs. JDBC interval array binding is not added in this stage and is rejected on
the column-aware path rather than treated as a scalar.

The existing SQL factory path also uses the PostgreSQL converter/formatter and
typed INTERVAL literals. Column precision is validated before rendering literals,
so scripted INSERT values use the same validation as bound INSERT values.
The general Converter registry retains its established null/empty-string defaults;
IntervalValue.parse itself remains strict.

The PostgreSQL mixed-sign regression now runs in ordinary tests. Core tests cover
every interval column's immutable-value and canonical-text round trips; PostgreSQL
tests cover driver objects, JDBC read -> Row -> bind, wide field bounds, all output
styles, qualifiers, precision, and COPY. These are driver/proxy/text tests, not
proof of an external PostgreSQL round trip.

References: [PostgreSQL interval semantics](https://www.postgresql.org/docs/18/datatype-datetime.html#DATATYPE-INTERVAL-INPUT),
[storage and infinity representations](https://github.com/postgres/postgres/blob/REL_18_STABLE/src/include/datatype/timestamp.h),
[pgJDBC PGInterval implementation](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.11/pgjdbc/src/main/java/org/postgresql/util/PGInterval.java).

## Conversion policy

No conversion warnings are stored on IntervalValue. They belong to conversion
results and migration reports, alongside the source/target type and any explicit
policy. StandardIntervalCodec.Result now retains the normalized destination
value, exact SQL payload and immutable warnings. JDBC handlers expose
convert(value, column) so a migration planner can retain these results before
execution. Automatic migration-report/file integration is a separate stage;
ordinary JDBC binding validates and executes but does not create a report.

| Target | Policy |
| --- | --- |
| PostgreSQL month/day/time model (implemented) | Preserve independent components; validate range and fractional precision. |
| Oracle and H2 YEAR TO MONTH (implemented) | Require zero days and time; validate leading precision and range. |
| Oracle and H2 DAY TO SECOND (implemented) | Require zero months. Combine days at an explicit 24-hour conversion policy; return CALENDAR_DAYS_AS_24_HOURS whenever nonzero calendar days are converted to 24-hour units, including cancellation to zero. Validate range and precision. |
| Other interval qualifiers | Validate omitted components and precision explicitly; never silently truncate. |

The DAY TO SECOND policy preserves a fixed-duration interpretation, not necessarily
the result of applying the source interval to a zoned timestamp across daylight
saving transitions. Month/day conversion using a guessed 30-day month is forbidden.

## Stage 5: SQL-standard targets

This stage connects YEAR TO MONTH and DAY TO SECOND in Oracle's existing interval
registrations and in H2 2.0 and later. H2 1.x registration and resolver behavior
remain unchanged. H2's other eleven standard qualifiers are not registered in this
stage. The shared codec understands their exact field rules, but their dialect
and Schema precision mappings still need separate integration and tests.

The public entry point for a destination is its StandardIntervalJdbcTypeHandler.
Normal SQL/row and batch binding passes Column metadata automatically. For
standalone binding and report preparation:

```java
var column = new Column("elapsed")
        .setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(9).setScale(9);
var handler = (StandardIntervalJdbcTypeHandler) dialect.getDbDataTypes()
        .getDbTypeStrict(column.getDataType()).getJdbcTypeHandler();
var prepared = handler.convert(IntervalValue.parse("P1DT-1H"), column);
// prepared.value() == IntervalValue.parse("PT82800S")
// prepared.text() == "+0 23:00:00"
// prepared.warnings() == [CALENDAR_DAYS_AS_24_HOURS]
handler.setObject(statement, 1, prepared.value(), column);
```

Retain prepared.warnings() with the source and target identity in a migration
report. Re-converting the already normalized value cannot recover its source
calendar-day provenance. Warnings are never placed on IntervalValue itself.
The conversion is checked before the PreparedStatement is touched. Missing
Column metadata uses the SQL default leading precision 2 and fractional precision
6; this cannot detect a physical column declared with lower precision. Use the
column-aware overload when those limits are known.

Generated SQL bind-variable evaluation previously copied only DataType from a
resolved Column. It now retains the Column, including length and scale, while
preserving the parameter's expression name for re-evaluation. This closes a
metadata gap on generated single-row INSERT as well as multi-row INSERT paths;
the H2 integration test exercises both with nine fractional digits.

Oracle leading precision is checked in 0..9 and H2 in 1..18. Fractional precision
is checked in 0..9. Column.length is leading precision and Column.scale is
fractional second precision for these targets (different from PostgreSQL's
precision mapping). Invalid precision, out-of-range leading fields, nonzero
omitted fields and excess fractional digits cause errors rather than truncation
or rounding. Both signs and nine fractional digits are preserved. The codec uses
integer arithmetic for the final day/time sum, including cancellation near range
boundaries, and throws ArithmeticException when the resulting Duration or month
count cannot be represented.

SQL-standard day-time values are read as timePart with zero calendar days. Thus
P1D and PT24H remain distinct source values but both become PT86400S after a
DAY TO SECOND round trip; the former conversion produces a warning. This is an
explicit destination semantic conversion. YEAR TO MONTH preserves signed total
months. H2 DAY(18) can contain values beyond Duration's long-second range; reading
such values fails explicitly rather than clipping them.

Both adapters read JDBC text to avoid old Interval integer limits. H2 binds exact
payloads with setString, verified against the existing embedded H2 2.3.232 test
dependency. Oracle constructs oracle.sql.INTERVALYM/INTERVALDS with the driver's
String constructor and binds the resulting object with setObject. Driver classes
are loaded at runtime from the connection class loader, with the context class
loader as a fallback for connection wrappers. Missing driver support produces an
actionable SQLException. No new mandatory driver dependency is introduced.
Oracle nulls use the vendor's interval type codes. Oracle anonymous interval arrays are explicitly unsupported; SQL named collections require a separate model. H2 interval arrays are supported in Stage 7.

SQL literals include explicit destination precision and use the same codec as
JDBC. H2 metadata now models these two interval qualifiers directly and reads
leading and fractional precision independently. Oracle's catalog type-name
matcher also handles declarations such as INTERVAL DAY(9) TO SECOND(9), which
previously fell back to OTHER because its two precision positions were not
recognized. This matcher is installed only for the newly connected dialect
types; other dialect registrations retain their existing behavior. The existing H2 batched catalog
query gains two fields already present in H2 2.x; no per-object query is added.
Recreation through Schema and the SQL factory is covered by an embedded test.
Stage 7 extends H2 metadata and DDL handling to all thirteen standard qualifiers.
No DataType identifiers, XML formats or existing Interval fields are changed.

Tests cover the shared thirteen-qualifier codec, exact bounds, invalid text,
Turkish locale, warnings, real embedded H2 JDBC/literals/generated INSERT paths,
and H2 metadata -> Schema -> DDL -> metadata. Oracle tests cover resolved versions
9, 10, 11, 12, 19 and 23, proxy JDBC reads/nulls and pre-bind validation.
OracleIntervalDriverTest additionally validates real driver objects offline when
an Oracle driver is present on the test runtime classpath; it skips otherwise.
It opens no database connection. Stage 7 adds opt-in Oracle Free server tests; old Oracle server/driver versions remain unverified.

References: [H2 interval types](https://www.h2database.com/html/datatypes.html#interval_type),
[Oracle interval literals](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/Literals.html),
[Oracle INTERVALYM API](https://docs.oracle.com/database/121/JAJDB/oracle/sql/INTERVALYM.html),
[Oracle INTERVALDS API](https://docs.oracle.com/database/121/JAJDB/oracle/sql/INTERVALDS.html).

## Stage 6: CSV, TSV and JSONL file transfer

The command file-transfer path now accepts scalar interval columns. Export,
typed import and offline previews share the existing IntervalValue parser and
canonical formatter. No dialect parser is used to guess a file value.
The canonical output is fixed before introducing the file path:

| Value | File text |
| --- | --- |
| One calendar day | P1D |
| Twenty-four hours of fixed time | PT86400S |
| Mixed signs and nanoseconds | P14M-3DT0.123456789S |
| Negative one nanosecond | PT-0.000000001S |
| Zero, including input negative zero | PT0S |

Input also accepts the existing sqlapp Y/H/M components and ASCII lowercase
designators. Fractions are allowed on seconds only, at most nine digits.
No day/time normalization is performed. P1D and PT24H retain different values.
Long month/day limits and both Duration boundaries round-trip. Non-null empty
strings, whitespace, malformed text, excess fractional digits and SQL/vendor
interval text fail typed input; they do not become arbitrary strings or zeros.
Legacy Interval objects can be exported through their explicit IntervalValue
bridge, but legacy text is not accepted by this new command format.

CSV and TSV retain their existing quoting, headers, encoding, null tokens and
empty-field policy. A canonical interval equal to the configured null token is
rejected at export, as for other scalar values. JSONL stores intervals as strings
and nulls as JSON null, not objects or numbers. Preview cells use quoted canonical
text. Stage 7 adds interval arrays as described below.

No production dependency, job property, manifest identifier or format version is
changed. These interval column types were previously rejected by typed command
import, so an older reader will still reject them rather than guess their
meaning. Generic core CSV utilities and existing legacy output formats are
unchanged by this stage.

Before target DDL or writes, file import checks interval values using the target
dialect's shared SQL/interval conversion path. Existing physical target column
definitions are read in one batch per review/execution, not per table or row.
Precision or range errors produce an actionable preflight failure. When schema
differences will be applied, validation uses the planned destination definition.
The source package bytes and normal execution-time revalidation remain pinned.

Stage 7 enables explicit calendar-day conversion with reviewed warnings and
verification of the destination projection; the earlier conservative rejection
is superseded. File-only validation and preview retain the original components.

Tests cover actual CSV/TSV/JSONL files, nulls and malformed values, independently
signed components, zero, Turkish locale and full common bounds. End-to-end
embedded H2 tests exercise export -> pinned package -> quoted preview ->
CREATE_MISSING -> import -> verification, including YEAR(9), DAY(9), nine-digit
fractions and negative one nanosecond. Separate tests reject a lower existing
target precision before any target write; Stage 7 verifies calendar-day conversion with an audit.
Only test dependencies on the existing H2 module and its existing driver version
are added to sqlapp-command, so these command-level checks run normally.
External databases are not required. This is not a claim that all unmigrated
dialect adapters or all cross-database Schema type mappings support the full
IntervalValue range.


## Stage 7: destination projections, remaining adapters and arrays

File import and JDBC_DIRECT preparation now resolve one destination interval
projection. The same projection checks every source value before target DDL,
feeds INSERT/COPY, and supplies expected values for verification. Calendar-day
conversion is reported as `CALENDAR_DAYS_AS_24_HOURS`, with column and affected
row count. Precision/qualifier mappings produce `INTERVAL_PRECISION_MAPPING`.
Both warnings appear in review and retained execution/audit results. A strict
compatibility policy rejects them. Values never carry migration warnings.

For standard day-time targets, days are fixed multiples of 24 hours:
`P1DT-1H` becomes `PT82800S`. PostgreSQL/DuckDB destinations retain separate
calendar days and time. Months are never converted to days. A generic source
interval targeting a standard database must resolve to one group from its
nonzero values, or use an explicitly prepared target column; mixed groups and
all-zero/empty ambiguous columns fail with an actionable error.

Leading precision and fractional-second precision are mapped semantically,
rather than copying Column.length between incompatible dialect conventions.
A declaration may narrow from nine fractional digits to six when every actual
value fits exactly; any nonzero discarded digit fails preflight. Range,
qualifier and unsupported array declarations also fail before writes, including
empty/null-only sources where declaration validation applies. PostgreSQL scan
statistics are omitted from captured direct-transfer schema definitions, because
preflight SELECTs change counters without changing types or constraints.

| Adapter | Value/precision boundary | Arrays | Verification performed |
| --- | --- | --- | --- |
| PostgreSQL | Independent int32 months/days and int64 microseconds; fraction 0..6 | Rectangular, at most six actual dimensions; null leaves | Real PostgreSQL 14.23 and 18.4; four output styles, DST, bounds, JDBC, literals, cross-DB file/direct transfer |
| Oracle | YEAR TO MONTH / DAY TO SECOND; leading 0..9, fraction 0..9 | Named collections require a separate model | Real Oracle Free 23.26.3 native driver/server; YEAR(9), DAY(9), nanoseconds and nulls; file transfer to prepared table |
| H2 2.x | All thirteen standard qualifiers; leading 1..18, fraction 0..9, within common Duration bounds | Nested ARRAY, null leaves/subarrays, declared upper cardinalities retained | Embedded H2 2.3.232; all qualifiers, metadata/DDL, JDBC, literals and CSV/TSV/JSONL package verification |
| HSQLDB 2.x | All thirteen standard qualifiers; leading 1..9, fraction 0..9 | Rejected: HSQLDB 2.7.4 interval parser rejects ARRAY declarations | Embedded engine, all scalar qualifiers and explicit array rejection |
| DuckDB | Independent int32 months/days and int64 microseconds | Nested variable-length lists | Embedded DuckDB JDBC 2.0.0.1, mixed signs, 25-hour time part, bounds and arrays |
| Informix | Native leading/trailing fields retained, including HOUR and FRACTION-only; leading 1..9, fraction 0..5 | Named collections require a separate model | Adapter/native qualifier tests; no Informix server execution |
| Vertica | Native driver objects; int64 microseconds; fraction 0..6; YEAR TO MONTH constrained by driver's int year constructor | Rejected pending native qualification | Real Vertica 25.3 driver objects offline; no Vertica server execution |
| Dr.Sum | Experimental DAY TO SECOND text adapter, length treated as fractional precision 0..6 | Rejected | Codec/unit tests only; existing dialect remains experimental, server metadata and binding assumptions unverified |
| TimesTen | Existing six legacy scalar registrations use exact common codecs; corrected YEAR spelling and SECOND registration | Rejected | Registration/codec tests only; server syntax, ranges and drivers unverified |

PostgreSQL before 15 has an int32 limit in its text interval input parser.
Ordinary values use an explicitly signed clock to avoid overflowing a large
microsecond token. A time part requiring more than Integer.MAX_VALUE clock
hours is rejected by transfer preflight on those versions; a binary interval
transport would be required to write that extreme range. PostgreSQL 18 tests
exercise both int64 microsecond endpoints. PostgreSQL 14's sql_standard output
for int32-minimum days prints a double minus; the reader handles exactly that
boundary. Non-default PostgreSQL array lower bounds are rejected explicitly
because ordinary Java arrays cannot retain them. PostgreSQL declarations do
not constrain actual array depth; each value's actual shape is used.

CSV/TSV interval-array cells contain quoted JSON arrays of canonical strings and
nulls. JSONL stores native nested JSON arrays with canonical string leaves.
Scalar interval cells keep the Stage 6 representation. Empty arrays, nulls and
nanosecond values round-trip; malformed shapes and non-string leaves fail typed
input. File readers enforce nesting and string limits. H2 declaration bounds
are stored in `interval.arrayBounds` in innermost-first order and recreated by
its SQL builder. Fixed-size DuckDB ARRAY declarations and SQL named collections
are outside this implementation. Stage 8 enables JDBC_DIRECT with STAGED_REPLACE using captured destination
projections, including retained-stage comparison.

The new `IntervalTypeHandler`, `IntervalPrecision`, `IntervalConversion` and
`IntervalArrays` APIs are shared extension points; vendor rules remain in their
own dialect modules. Existing JdbcTypeHandler column-aware reads and keyset key
metadata use default methods. No production dependency or job/manifest format
version was added. Existing Interval classes and their fields are unchanged in
this stage. Cross-product DDL still requires review. Stage 8 fixes the PostgreSQL table
settings and duplicate backing-index problems in Oracle CREATE SQL, and extends
the Oracle end-to-end test to CREATE_MISSING.

Real-server tests opt in through `sqlapp.interval.pg.url` and
`sqlapp.interval.oracle.url`, accepting only the dedicated loopback fixture
ports/database names in the tests. Default tests skip them. Tests create and
remove only private random schemas/tables or session-temporary tables. The
implementation run used fresh disposable containers, never an existing server.

Sources: [PostgreSQL 14 interval parser](https://github.com/postgres/postgres/blob/REL_14_STABLE/src/backend/utils/adt/datetime.c),
[PostgreSQL 15 interval parser](https://github.com/postgres/postgres/blob/REL_15_STABLE/src/backend/utils/adt/datetime.c),
[DuckDB intervals](https://www.duckdb.org/docs/2.0/sql/data_types/interval),
[Informix interval types](https://www.ibm.com/docs/en/informix-servers/12.10.0?topic=types-interval-data-type),
[Vertica interval JDBC objects](https://docs.vertica.com/26.2.x/en/connecting-to/client-libraries/accessing/java/jdbc-data-types/using-intervals-with-jdbc/).


## Stage 8: replayable staging projections and Oracle CREATE

Staged replacement remains limited to the existing PostgreSQL and HSQLDB
providers. JDBC_DIRECT + STAGED_REPLACE now uses the same reviewed interval semantics for
staging copy, stable-stage verification, final transactional replacement and
retained-stage comparison. Existing final interval columns determine their
actual qualifier and precision; missing final columns use the mapped definition.
Calendar days still convert only at a destination with fixed 24-hour days.
Review and audit retain the conversion warnings and affected row counts.

The generated, fingerprinted source-schema.xml captures destination interval
types, native qualifiers, array dimensions, precision and conversion warnings in
a private table Specifics entry, sqlapp.transfer.intervalProjection.v1. The job
warning list is stored once, not duplicated for every table. No user-authored
configuration or public record constructor changes. The generated data-job.yaml
pins the updated Schema hash. Both artifacts must remain with the saved result.

Execution rebuilds the captured projection and checks all current source values
against it before copying. A changed warning count requires another review.
Retained-stage comparison reconstructs the same types without opening the source
connection or reading source rows; it remains usable after the source table is
removed. Existing artifact fingerprints, target identity checks, stage hashes,
serializable replacement transaction and rollback/commit-uncertain handling are
preserved. Altered Schema evidence is rejected. Older non-interval artifacts
continue through the unchanged path.

Oracle's shared CREATE TABLE factory (all supported Oracle dialect versions)
now emits only its recognized Oracle physical properties from Specifics. Foreign
metadata such as PostgreSQL autovacuum_enabled, unlogged and relallfrozen is
retained in the Schema but is not concatenated into Oracle SQL. Modeled Oracle
storage options remain supported. An enabled inline PK/UNIQUE constraint already
creates its backing index; the separate matching index definition is therefore
omitted, while independent indexes remain modeled and generated. No metadata
catalog query or production dependency is added.

Tests cover interval keysets, keyless duplicate rows, renamed columns, multiple
tables, missing final tables, precision mapping, final INSERT rollback and both
commit-uncertain outcomes. Saved comparison runs after dropping the source table
and rejects altered evidence. Dedicated PostgreSQL 18 and Oracle Free tests
exercise calendar-day staging conversion and PostgreSQL -> Oracle CREATE_MISSING
-> import -> audit -> verification. Older Oracle server versions are covered by
unit tests, not actual server execution.


## Stage 9: native transport and collection qualification

PostgreSQL integer-datetime servers before 15 can store clock components outside
an int32 hour count, but cannot reliably input or output those values as interval
text. The adapter now uses the exact 16-byte binary representation for large
JDBC parameters and binary scalar/array results on SQLapp-owned read statements.
Temporary driver OID settings are restored, including execution failures. Simple
query mode cannot provide this path and fails explicitly. SQL literals split a
large clock component into two int32-hour-safe intervals and add them on the
server without converting hours into calendar days.

Transfer review selects JDBC_BATCH automatically when exact transport needs it,
records INTERVAL_JDBC_BINDING warnings and retains the selection in the captured
projection. Explicit NATIVE is rejected before target DDL. Vertica interval arrays
also require this selection because its delimited COPY parser rejects arrays.
Declarations are checked even for empty or entirely NULL inputs.

Informix typed reads project each interval to VARCHAR(128), or LIST elements to
LVARCHAR(128), before the JDBC driver sees them. This avoids confirmed int32-month
overflow in native YEAR(9) reads without bounding the total collection length.
Ordered, one-dimensional LIST(INTERVAL ... NOT NULL) maps to interval arrays;
order and duplicates are retained. Empty lists and a NULL whole column are
supported. NULL elements, SET and MULTISET are rejected for this array mapping.
The native declaration remains in the Schema model for recreation.

Oracle named VARRAY and nested TABLE collections of INTERVAL YEAR TO MONTH or
INTERVAL DAY TO SECOND are recognized in batched column metadata. The named SQL
type must already exist at the destination, with the recorded element precision
and VARRAY bound. For manual columns, use
OracleIntervalCollections.column(connection, typeName), then set the column name.
Refresh this resolution after type DDL. Identifiers are separately quoted.
Native bindings retain nanoseconds and NULL elements; empty and NULL collections
remain different. VARRAY retains order. Nested TABLE is an unordered bag: values
are sorted canonically for verification, duplicates are retained, and conversion
reports INTERVAL_COLLECTION_UNORDERED. Generated CREATE TABLE includes nested
storage clauses. Collection type CREATE itself remains a separate prerequisite.

Vertica supports one-dimensional interval ARRAY JDBC writes using exact JSON
strings and explicit CAST parameters. Its INSERT factory emits the CAST to avoid
the driver's scalar COPY batching path. Metadata retains cardinality and native
outer byte capacity, and DDL uses ARRAY[element, bound] syntax. Typed reads cast
individual elements to VARCHAR(128), preserving server text independently of
narrow driver interval objects. Nested array parameter casts were rejected by
Vertica 25.1 and are rejected before writes by this adapter. The signed int64
minimum microsecond pattern is reserved/non-finite on the tested server, so the
finite day/time range is -Long.MAX_VALUE through Long.MAX_VALUE microseconds.
The scalar native year/month driver constructor still limits the year field to
int32; larger writes fail explicitly.

JdbcTypeHandler now provides default readExpression and prepareRead hooks;
JdbcReadScope composes temporary statement settings. Options.jdbcRead is false
by default, so ordinary generated SELECT SQL keeps its existing form. SQLapp's
row iterator and migration keyset source enable typed projections automatically.
No Schema fields, production dependencies or dependency versions were added.
Standard destination adapters clear foreign native type names when mapping types.

Real-server qualification in disposable local containers:

| Product | Tested server versions | Checks |
| --- | --- | --- |
| PostgreSQL | 9.3.25, 14.23, 18.4 | Native bounds, mixed signs, scalar/array transport, literals |
| Oracle Free | 23.26.3 image | Scalars, named VARRAY/TABLE, nanos, metadata and table recreation |
| Informix | 14.10.FC9W1DE, 15.0.1.0.3 | Six native qualifiers, leading bounds, fractions, ordered LIST, metadata and recreation |
| Vertica | 25.1.0-0 | All 13 qualifiers, finite microsecond endpoints, ARRAY, metadata, generated INSERT and recreation |

Informix 12.10.FC12W1DE was initially blocked by fixture JDBC authentication;
Stage 10 below completes this qualification. Dr.Sum and TimesTen have no available dedicated
environment and were skipped as requested. Tests never connect to an external
or production database. Real-server tests remain opt-in. The additional command
fixture tests require the Informix/Vertica dialects and JDBC drivers on the test
runtime and sqlapp.interval.additional=true; they use only the dedicated loopback
ports 19088 and 15433.

The native file-transfer fixtures verified CSV, TSV and JSONL imports into
Informix 15 and Vertica 25 with post-import data comparison. Vertica also verifies
an entirely NULL array column under AUTO and rejects forced NATIVE before DDL.
Oracle named collections were qualified through native JDBC and SQL literals,
metadata and table recreation; their file-transfer path was not server-qualified
in Stage 9; Stage 10 below adds bidirectional qualification. PostgreSQL 14 file transfer verifies AUTO/JDBC_BATCH success and
NATIVE rejection for a clock component beyond the old text parser's range.


## Stage 10: Oracle collection file transfer and Informix 12

The Oracle Free 23.26.3 image is exercised in both directions between H2 and
Oracle for named VARRAY and nested TABLE collections, YEAR(9) TO MONTH and
DAY(9) TO SECOND(9), and CSV/TSV/JSONL. The 12 combinations each perform export,
import and data verification in both directions. Cases include nanosecond
fractions, maximum leading fields, negative values, duplicates, NULL elements,
empty collections and NULL whole collections. Nested TABLE conversion retains
its INTERVAL_COLLECTION_UNORDERED warning and canonical bag comparison.
Destination SQL collection types are still an explicit prerequisite.

These tests exposed Oracle storage metadata entering the logical Schema:

* Column catalog queries now default to visible/user-authored columns when the
  batch query context omits containsHiddenColumns, as well as when it is false.
  Explicit true retains the existing internal-hidden-column selection. Oracle
  12c and later still include user-authored INVISIBLE columns. The SQL conditions
  handle a missing flag without a null-negation exception. Oracle11g and later
  use these corrected column queries; earlier versions keep ALL_TAB_COLUMNS.
* Oracle automatically generates a unique constraint and index for a nested
  collection's internal identity. The constraint references a SYS_NC... storage
  column while the index catalog reports the visible collection column. These
  storage-only keys are excluded using catalog relationships to
  ALL_NESTED_TABLES and ALL_IND_COLUMNS, without matching generated name prefixes.
  Ordinary primary keys and unnamed user unique constraints, including an
  INVISIBLE RAW column's key, remain modeled. The change uses the existing
  batched catalog queries, with no additional per-table requests.

The catalog fields used for the key relationship are present in the
[Oracle9i Release 2 ALL_CONSTRAINTS reference](https://docs.oracle.com/cd/B10501_01/server.920/a96536/ch229.htm)
and [ALL_NESTED_TABLES reference](https://docs.oracle.com/cd/B10501_01/server.920/a96536/ch287.htm).
Older Oracle versions are covered by SQL evaluation/unit tests rather than
real-server execution in this stage.

Informix12.10.FC12W1DE passed the same native qualifier bounds, fractional
precision, ordered LIST, NULL/empty handling and metadata recreation tests used
for versions14 and15. This old image did not apply INFORMIX_PASSWORD to its
initial account; qualification used the image's built-in fixture credential.
The temporary trust-file experiment was removed before the successful run;
normal password authentication was used. This is only a disposable loopback
fixture procedure, with no production authentication changes.

Vertica25.1 rejects both a direct nested-array parameter CAST and a CAST through
VARCHAR(65000), with SQLState42846. A real-server regression test verifies both
failures and that no row was inserted. One-dimensional JDBC array support is
unchanged; multi-dimensional JDBC writes remain rejected before execution.
No new public API, dependency or user configuration setting is introduced.

## Stage 11: Vertica nested DAY/TIME arrays through native JSON COPY

Vertica 25.1.0-0 now supports DAY/TIME interval arrays through native JSON COPY.
The real-server fixtures qualify dimensions 1, 2 and 3, all ten DAY/TIME
qualifiers, finite microsecond endpoints, negative fractions, duplicate leaves,
NULL leaves/subarrays/whole arrays and empty arrays at every level. Dimensions
up to the existing 32-level model limit are accepted but not all server-qualified.
The JDBC driver exposes NULL subarrays as empty JDBC Array wrappers; the Vertica
reader instead decodes the outer array text, which retains their distinction.
Other dialect readers are unchanged.

Transfer review selects NATIVE for nested DAY/TIME declarations under AUTO,
retains the decision in the captured plan, and emits INTERVAL_NATIVE_BINDING
in review/audit results. Explicit JDBC_BATCH is rejected before target DDL,
including NULL values. The shared IntervalTypeHandler.supportsJdbcBinding(Column)
default remains true; only dialects requiring another transport override it.
The low-level JDBC batch executor also checks it before consuming source rows.
CSV/TSV/JSONL packages and JDBC_DIRECT transfers are qualified from H2 into
Vertica with post-transfer data verification. Keyless duplicate rows are also
qualified through full-scan verification. File encodings themselves are unchanged;
JSON COPY is an internal destination transport.

Native COPY uses strict materialized-type checking, ABORT ON ERROR and NO COMMIT,
and checks accepted/rejected row counts. Autocommit connections use an owned
transaction that commits only after the input is closed successfully; caller
transactions retain commit/rollback ownership. Server rejections, input read
failures and input-close failures are covered by real-server rollback/reuse tests.
All 13 scalar interval qualifiers and binary NULL/empty/nonempty cells are
qualified through delimited COPY. Interval text uses its declared qualifier;
an incompatible text CAST can silently produce NULL on this server.

JSON COPY accepts DAY/TIME interval arrays only. YEAR/MONTH multidimensional
arrays remain explicitly unsupported: JDBC parameter casts reject them and
FJSONPARSER cannot materialize their values. One-dimensional YEAR/MONTH arrays
retain JDBC_BATCH selection; forced NATIVE is rejected at transfer review.
A task combining nested DAY/TIME arrays and YEAR/MONTH arrays has no common
supported transport and is rejected. Non-interval array columns in a JSON COPY
row are also rejected. Hidden, computed and skipped identity columns are excluded
from transport selection and validation.

For JSON COPY rows, binary cells use two VARCHAR hex fillers and HEX_TO_BINARY,
with an explicit limit of 65,000 bytes per binary cell. Larger cells fail rather
than silently truncate. Scalar JSON values must have an explicitly supported
Java representation, and floating-point values must be finite. This stage does
not qualify every possible non-interval vendor type or older Vertica versions.
No dependency versions, credentials or public configuration formats change.

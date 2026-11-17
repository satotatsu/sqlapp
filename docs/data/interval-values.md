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
policy. The current stages introduce no conversion-result API.

| Future target | Policy |
| --- | --- |
| PostgreSQL month/day/time model (implemented) | Preserve independent components; validate range and fractional precision. |
| SQL-standard YEAR TO MONTH targets (future) | Require zero days and time; validate leading precision and range. |
| SQL-standard DAY TO SECOND targets (future) | Require zero months. Combine days at an explicit 24-hour conversion policy; record a warning whenever nonzero calendar days are converted to 24-hour units. Validate range and precision. |
| Other interval qualifiers | Validate omitted components and precision explicitly; never silently truncate. |

The DAY TO SECOND policy preserves a fixed-duration interpretation, not necessarily
the result of applying the source interval to a zoned timestamp across daylight
saving transitions. Month/day conversion using a guessed 30-day month is forbidden.

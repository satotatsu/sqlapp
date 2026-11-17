# Interval values: staged replacement

`sqlapp-core` provides the independent immutable value
`com.sqlapp.data.interval.IntervalValue`. Existing `Interval*`, `DataType`, JDBC
handlers, Schema XML and file export defaults are unchanged. No new dependency
is required. This stage does not enable new database or migration-file support.

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
ArithmeticException when nonzero digits would be lost. A future PostgreSQL
adapter must call `requireFractionalPrecision(6)` before writing microseconds;
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

## Future conversion policy

No conversion warnings are stored on IntervalValue. They belong to conversion
results and migration reports, alongside the source/target type and any explicit
policy. The current stages introduce no conversion-result API.

| Future target | Policy |
| --- | --- |
| PostgreSQL-like month/day/time model | Preserve independent components; validate range and fractional precision. |
| YEAR TO MONTH | Require zero days and time; validate leading precision and range. |
| DAY TO SECOND | Require zero months. Combine days at an explicit 24-hour conversion policy; record a warning whenever nonzero calendar days are converted to 24-hour units. Validate range and precision. |
| Other interval qualifiers | Validate omitted components and precision explicitly; never silently truncate. |

The DAY TO SECOND policy preserves a fixed-duration interpretation, not necessarily
the result of applying the source interval to a zoned timestamp across daylight
saving transitions. Month/day conversion using a guessed 30-day month is forbidden.

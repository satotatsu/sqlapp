# Value conversion (`sqlapp-core`)

`Converters` is the common entry point for converting scalar values, arrays,
collections, JDBC values, and selected date/time types. It is used by sqlapp's
file and database workflows and can also be called directly from application
code.

Use a separate instance when changing converter settings. This keeps local
formatting and default-value choices from affecting code that uses the shared
default registry.

```java
import java.math.BigDecimal;
import java.util.List;

import com.sqlapp.data.converter.Converters;

Converters converters = new Converters();

Character letter = converters.convertObject("日", Character.class);
char[] text = converters.convertObject("日本語", char[].class);
boolean[] flags = converters.convertObject(
        List.of("yes", "off"), boolean[].class);
Number number = converters.convertObject("12.50", Number.class);

assert number instanceof BigDecimal;
```

`Converters.getDefault()` returns the shared default registry. It is suitable
when the application only uses the built-in behavior. Prefer `new Converters()`
before replacing converters or changing defaults.

## Common conversion behavior

| Target | Accepted input and result |
|---|---|
| `Character` / `char` | A `Character` or a `CharSequence` containing exactly one UTF-16 code unit |
| `Character[]` | Arrays, collections, iterables, JDBC arrays, or one scalar value; null elements are retained |
| `char[]` | A complete `CharSequence`, or element conversion from an array, collection, iterable, or JDBC array |
| `Boolean` and boolean arrays | Boolean values and configured text/number forms such as `true`/`false`, `yes`/`no`, and `1`/`0` |
| `Number` | Existing `Number` values, or other values parsed to the configured number class; the default is `BigDecimal` |
| Java enum | Enum names, subject to the registry's enum conversion settings |
| `MonthDay`, `Month`, `DayOfWeek` | ISO text, supported numeric values, or date/time inputs with the required fields |

Primitive targets cannot contain null. A null element becomes the primitive
default, such as `\0` for `char` and `false` for `boolean`. Wrapper arrays retain
null elements. A null input for the entire array follows the configured default
behavior and is null unless another default was supplied.

Array conversions return a new array. A configured array default supplied by a
`Supplier` is evaluated when the default is requested, and the returned array
is copied.

## Character conversion

A `Character` represents one UTF-16 code unit. Empty input produces the
configured default: initially null for `Character` and `\0` for `char`.
Whitespace is a character and is preserved.

Input containing multiple code units, including a supplementary Unicode code
point represented by a surrogate pair, cannot be converted to one
`Character`. Use `String` or `char[]` for such text.

```java
Character space = converters.convertObject(" ", Character.class);
char[] supplementary = converters.convertObject("𠮷", char[].class);
```

Numeric input is not interpreted as a character code. Invalid scalar input
raises `ConverterException` rather than truncating it.

## Boolean arrays

Boolean arrays reuse the configured scalar `BooleanConverter` for every
element. This keeps accepted true/false tokens and unknown-value handling
consistent between scalar and array conversion.

```java
Boolean[] nullable = converters.convertObject(
        List.of("true", "0"), Boolean[].class);
boolean[] primitive = converters.convertObject(
        new String[] { "yes", "no" }, boolean[].class);
```

Use `Boolean[]` when a null element must remain distinguishable from false.

## Number conversion

The `Number` target uses the registry's `NumberConverter`. Existing `Number`
instances are retained. Other supported values are converted to the configured
number class, which defaults to `BigDecimal`.

```java
Number decimal = converters.convertObject("42.25", Number.class);
```

Use `setNumberConverter(...)` on an independent `Converters` instance when the
application requires another default number representation or number format.
Conversions to concrete numeric targets continue to use their corresponding
converter and range rules. Values with an invalid format, a fractional part
that the target does not accept, or an overflow raise `ConverterException`.

## Month, day, and weekday types

```java
import java.time.DayOfWeek;
import java.time.Month;
import java.time.MonthDay;
import java.util.List;

MonthDay anniversary = converters.convertObject("--02-29", MonthDay.class);
Month month = converters.convertObject(12, Month.class);       // DECEMBER
DayOfWeek day = converters.convertObject(1, DayOfWeek.class); // MONDAY
MonthDay[] dates = converters.convertObject(
        List.of("--01-01", "--12-31"), MonthDay[].class);
```

`MonthDay` uses ISO `--MM-dd` text. Valid leap-day text such as `--02-29` is
accepted; impossible dates such as `--02-30` and `--04-31` fail. Null and
blank-only text return the configured default, initially null.

`Month` accepts integers 1 through 12. `DayOfWeek` accepts ISO integers 1
through 7, where Monday is 1 and Sunday is 7. Integer text is accepted as well.
Fractional numbers, out-of-range values, and integer overflow fail instead of
being truncated.

These types can also be extracted from `java.sql.Date` and a
`TemporalAccessor` that contains the required date fields. Local date values
from `OffsetDateTime` and `ZonedDateTime` are preserved. The converter does not
invent a time zone or missing date fields for an `Instant` or an incomplete
temporal value.

Existing enum-name forms such as `JANUARY` and `MONDAY` remain available.
Call `setEnumEmptyToNull(true)` before first use when empty enum text should
become null.

## Optional, Supplier, iterable, and JDBC input

Converters unwrap supported `Optional` and `Supplier` inputs before applying
the target converter. Array converters accept Java arrays, collections,
iterables, and `java.sql.Array` values.

When a JDBC array is used, sqlapp reads its elements and releases the JDBC
array on both successful and failed conversion paths. The caller remains
responsible for the JDBC connection, result set, and statement that produced
the value.

## Failure handling

Conversion failures use `ConverterException`. Treat the exception as invalid
input unless the application deliberately configured a fallback/default for
that converter. Do not assume that every text value is trimmed or coerced:
whitespace, empty text, null, unknown boolean tokens, overflow, and incomplete
date/time values have target-specific behavior.

For file conversion, validate the resulting data before replacing source
files. The Gradle `ConvertDataTask` can remove originals when
`removeOriginalFile` is enabled; its safe default workflow and properties are
described in [Custom tasks and versioned migrations](gradle-plugin/custom-tasks-and-migrations.md#convert-saved-data-files).

## Extending the registry

Individual converter classes, including `CharacterConverter`,
`MonthDayConverter`, `MonthConverter`, and `DayOfWeekConverter`, can be used
directly when only one target type is required. Use `Converters` when a workflow
needs consistent dispatch across multiple runtime source and target types.

Register or replace converters on a dedicated registry during application
initialization, before that registry handles values. This avoids mixing
different conversion policies within one import or export operation.

The converter regression tests use JDBC array stubs and do not require an
external database. Repository contributors can run the focused converter tests
as described in [Building and testing sqlapp](build-and-test.md).

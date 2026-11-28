# Text file I/O

[Data processing](README.md)

`com.sqlapp.util.file` provides CSV, TSV and fixed-width text readers and writers.
Use try-with-resources to close parsers and writers. Calling `read()` on a
`CsvParser`, `TsvParser` or `FixedWidthParser` starts parsing automatically;
subsequent calls continue from the current position and return `null` at EOF.
An explicit `beginParsing()` call remains supported.

`FixedByteLengthParser` reads records using the configured field byte lengths
plus the encoded `lineBreak` bytes (LF by default). `readAll()` delivers each
complete record, including its line-break bytes, with a zero-based index.
`readAllRecord()` converts complete records to Schema rows. Both methods
accumulate short stream reads and throw `EOFException` if EOF occurs partway
through a record, including a missing final configured line break. Complete
records preceding the failure have already been delivered. Set `lineBreak` to
an empty string for records without line separators.

When writing Schema rows, `FixedByteLengthWriter` checks the encoded byte length
of each value. A value exceeding its configured field width causes an
`IllegalArgumentException` identifying the field, required bytes and configured
width; the rejected row is not written. Earlier rows remain written. Field
widths are measured in bytes, so multi-byte characters may require more space
than their character count suggests.

Default field widths are unchanged. For example, the default `INT` width is
10 bytes, while `Integer.MIN_VALUE` requires 11. Set the field length explicitly
through the field settings callback when the file format needs that range.

Every configured field must have a positive byte length. Types whose width
cannot be inferred, such as `DECIMAL`, require an explicit field length.
Invalid settings are rejected before a writer opens or truncates its output
file. An empty padding string is invalid for left or right padding.

`NO_PADDING` requires each encoded value to exactly fill its field width and
copies those bytes unchanged. With left or right padding, the remaining width
must be an exact multiple of the encoded padding length; otherwise the row is
rejected rather than splitting a multi-byte padding character.

NULL values are rejected with an `IllegalArgumentException` naming the field.
Choose an explicit non-null representation in the source rows when your file
format needs to represent NULL. Rows rejected for NULL, padding or width errors
are not written; earlier complete rows remain written.

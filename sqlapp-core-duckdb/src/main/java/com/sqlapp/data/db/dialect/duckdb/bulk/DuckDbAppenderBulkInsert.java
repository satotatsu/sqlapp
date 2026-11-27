/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.bulk;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import com.sqlapp.data.converter.ConvertObject;
import com.sqlapp.data.converter.JdbcConvertObject;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.datatype.DefaultJdbcTypeHandler;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.iterator.IteratorCloseUtils;

/**
 * Inserts rows through the DuckDB Appender, which writes directly into DuckDB's
 * column vectors instead of executing one parameterized INSERT per row.
 * <p>
 * The Appender is used only when it can reproduce the batch INSERT exactly: the
 * driver provides the 1.4+ Appender API, every target column is written from
 * the row and has a supported type. {@link #plan} returns {@code null}
 * otherwise, and the caller uses the JDBC batch.
 * </p>
 * <p>
 * Driver behavior this class relies on, verified with duckdb_jdbc 2.0.0.1:
 * </p>
 * <ul>
 * <li>The Appender joins the connection's transaction only after a statement
 * has started it; otherwise it commits independently. A statement is therefore
 * executed first when auto-commit is off.</li>
 * <li>{@code close()} without {@code flush()} discards constraint violations,
 * so the rows are always flushed explicitly.</li>
 * <li>Values must have exactly the column's physical type (an {@code int}
 * cannot be appended to BIGINT, a DECIMAL must have the column's scale), so
 * every value is converted to the target type here.</li>
 * <li>{@code appendDefault()} stores a wrong value for a non-constant default
 * such as {@code nextval}, so a target column that is not written from the row
 * disables the Appender.</li>
 * </ul>
 */
final class DuckDbAppenderBulkInsert {

	private static final String COLUMNS_SQL = "SELECT table_catalog, table_schema, table_name, column_name,"
			+ " data_type, numeric_precision, numeric_scale, is_generated FROM information_schema.columns"
			+ " WHERE table_catalog = coalesce(?, current_database())"
			+ " AND lower(table_schema) = lower(coalesce(?, current_schema()))"
			+ " AND lower(table_name) = lower(?) ORDER BY table_catalog, table_schema, table_name, ordinal_position";

	private static final Pattern DECIMAL = Pattern.compile("(?:DECIMAL|NUMERIC)\\((\\d+),\\s*(\\d+)\\)");

	private static final BigInteger HUGEINT_MIN = BigInteger.ONE.shiftLeft(127).negate();
	private static final BigInteger HUGEINT_MAX = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

	private static volatile Boolean available;

	private final DuckDBConnection duckdb;
	private final String catalog;
	private final String schema;
	private final String table;
	private final List<Writer> writers;

	private DuckDbAppenderBulkInsert(final DuckDBConnection duckdb, final String catalog, final String schema,
			final String table, final List<Writer> writers) {
		this.duckdb = duckdb;
		this.catalog = catalog;
		this.schema = schema;
		this.table = table;
		this.writers = writers;
	}

	/** Writes one value of one target column. */
	@FunctionalInterface
	private interface Append {
		void append(DuckDBAppender appender, Object value) throws SQLException;
	}

	private record Writer(Column column, String targetName, String targetType, ConvertObject<?> converter,
			Append append) {
	}

	/**
	 * Resolves the target table and value conversions.
	 *
	 * @return the Appender plan, or {@code null} when the JDBC batch must be used
	 */
	static DuckDbAppenderBulkInsert plan(final Connection connection, final Dialect dialect, final Table table,
			final List<Column> columns) throws SQLException {
		final DuckDBConnection duckdb;
		try {
			if (!isAvailable() || !connection.isWrapperFor(DuckDBConnection.class)) {
				return null;
			}
			duckdb = connection.unwrap(DuckDBConnection.class);
		} catch (LinkageError e) {
			// The driver is missing or loaded by another class loader.
			return null;
		}
		final Map<String, Column> byName = new HashMap<>();
		for (final Column column : columns) {
			if (byName.put(key(column.getName()), column) != null) {
				return null;
			}
		}
		String catalog = null;
		String schema = null;
		String name = null;
		final List<Writer> writers = new ArrayList<>();
		try (var statement = connection.prepareStatement(COLUMNS_SQL)) {
			statement.setString(1, table.getCatalogName());
			statement.setString(2, table.getSchemaName());
			statement.setString(3, table.getName());
			try (var resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					if (catalog == null) {
						catalog = resultSet.getString(1);
						schema = resultSet.getString(2);
						name = resultSet.getString(3);
					} else if (!catalog.equals(resultSet.getString(1)) || !schema.equals(resultSet.getString(2))
							|| !name.equals(resultSet.getString(3))) {
						return null;
					}
					if ("ALWAYS".equalsIgnoreCase(resultSet.getString(8))) {
						continue;
					}
					final String targetName = resultSet.getString(4);
					final Column column = byName.remove(key(targetName));
					final Append append = column == null ? null
							: append(resultSet.getString(5), resultSet.getInt(6), resultSet.getInt(7));
					if (append == null) {
						return null;
					}
					writers.add(
							new Writer(column, targetName, resultSet.getString(5), converter(dialect, column), append));
				}
			}
		}
		if (catalog == null || !byName.isEmpty() || writers.isEmpty()) {
			return null;
		}
		return new DuckDbAppenderBulkInsert(duckdb, catalog, schema, name, List.copyOf(writers));
	}

	/** Appends every row and returns the number of rows. */
	long execute(final Connection connection, final Iterator<Row> rows) throws SQLException {
		if (!connection.getAutoCommit()) {
			// Start the caller's transaction so that the Appender joins it.
			try (var statement = connection.createStatement()) {
				statement.execute("SELECT 1");
			}
		}
		long count = 0;
		try (DuckDBAppender appender = duckdb.createAppender(catalog, schema, table)) {
			while (rows.hasNext()) {
				final Row row = rows.next();
				appender.beginRow();
				for (final Writer writer : writers) {
					Object value = row.get(writer.column());
					if (value != null && writer.converter() != null) {
						value = writer.converter() instanceof JdbcConvertObject<?> jdbc
								? jdbc.convertObject(value, connection)
								: writer.converter().convertObject(value);
					}
					if (value == null) {
						appender.appendNull();
						continue;
					}
					try {
						writer.append().append(appender, value);
					} catch (RuntimeException e) {
						throw new SQLException(
								"Cannot append " + value.getClass().getName() + " value to " + writer.targetName() + " "
										+ writer.targetType() + " of " + table + ": " + e.getMessage(),
								e);
					}
				}
				appender.endRow();
				count++;
			}
			// close() alone does not report constraint violations.
			appender.flush();
		}
		return count;
	}

	/**
	 * Inserts all rows when the Appender can be used.
	 *
	 * @return the row count, or {@code -1} when the caller must use the JDBC batch;
	 *         the rows are not consumed in that case
	 */
	static long tryExecute(final Connection connection, final Dialect dialect, final Table table,
			final List<Column> columns) throws SQLException {
		final DuckDbAppenderBulkInsert plan = plan(connection, dialect, table, columns);
		if (plan == null) {
			return -1;
		}
		final Iterator<Row> rows = table.getRows().iterator();
		Throwable failure = null;
		try {
			return plan.execute(connection, rows);
		} catch (SQLException | RuntimeException | Error e) {
			failure = e;
			throw e;
		} finally {
			try {
				IteratorCloseUtils.close(failure, rows);
			} catch (final RuntimeException closeFailure) {
				throw new SQLException("Failed to close DuckDB appender rows", closeFailure);
			}
		}
	}

	/** Whether the runtime driver provides the 1.4+ Appender API. */
	static boolean isAvailable() {
		Boolean value = available;
		if (value == null) {
			value = detect();
			available = value;
		}
		return value;
	}

	private static boolean detect() {
		try {
			final Class<?> appender = DuckDBAppender.class;
			appender.getMethod("append", LocalDate.class);
			appender.getMethod("append", LocalTime.class);
			appender.getMethod("append", LocalDateTime.class);
			appender.getMethod("append", OffsetDateTime.class);
			appender.getMethod("append", BigDecimal.class);
			appender.getMethod("append", BigInteger.class);
			appender.getMethod("append", UUID.class);
			appender.getMethod("append", byte[].class);
			if (appender.getMethod("flush").getReturnType() != long.class) {
				return false;
			}
			DuckDBConnection.class.getMethod("createAppender", String.class, String.class, String.class);
			return true;
		} catch (NoSuchMethodException | LinkageError e) {
			return false;
		}
	}

	/**
	 * The column's statement converter, the same conversion the JDBC batch applies
	 * before binding. Columns bound by a DuckDB-specific handler convert in the
	 * append step instead.
	 */
	private static ConvertObject<?> converter(final Dialect dialect, final Column column) {
		final DataType type = column.getDataType();
		if (type == null || type.isOther() || type.isDomain() || column.getArrayDimension() > 0) {
			return null;
		}
		final DbDataType<?> dbDataType = dialect.getDbDataTypes().getDbType(type);
		if (dbDataType != null && dbDataType.getJdbcTypeHandler() instanceof DefaultJdbcTypeHandler handler) {
			return handler.getStatementConverter();
		}
		return null;
	}

	/**
	 * Append operation for a DuckDB column type, or {@code null} if unsupported.
	 */
	private static Append append(final String type, final int precision, final int scale) {
		final String upper = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
		final Matcher decimal = DECIMAL.matcher(upper);
		if (decimal.matches()) {
			final int p = Integer.parseInt(decimal.group(1));
			final int s = Integer.parseInt(decimal.group(2));
			return (appender, value) -> appender.append(decimal(value, p, s));
		}
		return switch (upper) {
		case "BOOLEAN" -> (appender, value) -> appender.append(bool(value));
		case "TINYINT" -> (appender, value) -> appender.append((byte) integral(value, Byte.MIN_VALUE, Byte.MAX_VALUE));
		case "SMALLINT" ->
			(appender, value) -> appender.append((short) integral(value, Short.MIN_VALUE, Short.MAX_VALUE));
		case "INTEGER" ->
			(appender, value) -> appender.append((int) integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
		case "BIGINT" -> (appender, value) -> appender.append(integral(value, Long.MIN_VALUE, Long.MAX_VALUE));
		case "HUGEINT" -> (appender, value) -> appender.append(hugeint(value));
		case "FLOAT" -> (appender, value) -> appender.append(floating(value).floatValue());
		case "DOUBLE" -> (appender, value) -> appender.append(floating(value).doubleValue());
		case "VARCHAR" -> (appender, value) -> appender.append(text(value));
		case "BLOB" -> (appender, value) -> appender.append(bytes(value));
		case "DATE" -> (appender, value) -> appender.append(date(value));
		case "TIME" -> (appender, value) -> appender.append(time(value));
		case "TIMESTAMP" -> (appender, value) -> appender.append(timestamp(value));
		case "TIMESTAMP WITH TIME ZONE" -> (appender, value) -> appender.append(timestampWithTimeZone(value));
		case "UUID" -> (appender, value) -> appender.append(uuid(value));
		default -> null;
		};
	}

	private static String key(final String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static boolean bool(final Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof Number number) {
			return toBigDecimal(number).signum() != 0;
		}
		if (value instanceof CharSequence text) {
			return switch (text.toString().trim().toLowerCase(Locale.ROOT)) {
			case "true", "t", "1", "yes", "y" -> true;
			case "false", "f", "0", "no", "n" -> false;
			default -> throw new IllegalArgumentException("not a boolean: " + text);
			};
		}
		throw unsupported(value);
	}

	/**
	 * Integral value with DuckDB's cast rounding: binary floating-point values
	 * round half to even, decimals half away from zero.
	 */
	private static long integral(final Object value, final long min, final long max) {
		final BigInteger integer = integer(value);
		if (integer.compareTo(BigInteger.valueOf(min)) < 0 || integer.compareTo(BigInteger.valueOf(max)) > 0) {
			throw new IllegalArgumentException("value out of range: " + value);
		}
		return integer.longValue();
	}

	private static BigInteger hugeint(final Object value) {
		final BigInteger integer = integer(value);
		if (integer.compareTo(HUGEINT_MIN) < 0 || integer.compareTo(HUGEINT_MAX) > 0) {
			throw new IllegalArgumentException("value out of range: " + value);
		}
		return integer;
	}

	private static BigInteger integer(final Object value) {
		if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
			return BigInteger.valueOf(((Number) value).longValue());
		}
		if (value instanceof BigInteger integer) {
			return integer;
		}
		if (value instanceof Double || value instanceof Float) {
			final double number = ((Number) value).doubleValue();
			if (Double.isNaN(number) || Double.isInfinite(number)) {
				throw new IllegalArgumentException("value out of range: " + value);
			}
			return new BigDecimal(Math.rint(number)).toBigIntegerExact();
		}
		if (value instanceof Boolean bool) {
			return bool ? BigInteger.ONE : BigInteger.ZERO;
		}
		if (value instanceof Number number) {
			return toBigDecimal(number).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
		}
		if (value instanceof CharSequence text) {
			return new BigDecimal(text.toString().trim()).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
		}
		throw unsupported(value);
	}

	private static BigDecimal decimal(final Object value, final int precision, final int scale) {
		final BigDecimal decimal;
		if (value instanceof Number number) {
			decimal = toBigDecimal(number);
		} else if (value instanceof CharSequence text) {
			decimal = new BigDecimal(text.toString().trim());
		} else {
			throw unsupported(value);
		}
		final BigDecimal scaled = decimal.setScale(scale, RoundingMode.HALF_UP);
		if (scaled.precision() - scaled.scale() > precision - scale) {
			throw new IllegalArgumentException(
					"value out of range for DECIMAL(" + precision + "," + scale + "): " + value);
		}
		return scaled;
	}

	private static Double floating(final Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof CharSequence text) {
			return Double.valueOf(text.toString().trim());
		}
		throw unsupported(value);
	}

	private static BigDecimal toBigDecimal(final Number number) {
		if (number instanceof BigDecimal decimal) {
			return decimal;
		}
		if (number instanceof BigInteger integer) {
			return new BigDecimal(integer);
		}
		if (number instanceof Double || number instanceof Float) {
			final double value = number.doubleValue();
			if (Double.isNaN(value) || Double.isInfinite(value)) {
				throw new IllegalArgumentException("value out of range: " + number);
			}
			// Exact binary value, as DuckDB casts a DOUBLE parameter.
			return new BigDecimal(value);
		}
		if (number instanceof Long || number instanceof Integer || number instanceof Short || number instanceof Byte) {
			return BigDecimal.valueOf(number.longValue());
		}
		return new BigDecimal(number.toString());
	}

	private static String text(final Object value) {
		if (value instanceof CharSequence text) {
			return text.toString();
		}
		if (value instanceof Number || value instanceof Boolean || value instanceof UUID
				|| value instanceof Character) {
			return value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
		}
		throw unsupported(value);
	}

	private static byte[] bytes(final Object value) {
		if (value instanceof byte[] bytes) {
			return bytes;
		}
		if (value instanceof java.sql.Blob blob) {
			try {
				return blob.getBytes(1, Math.toIntExact(blob.length()));
			} catch (SQLException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
		}
		throw unsupported(value);
	}

	/** Same inputs as {@code DuckDbJdbcTypeHandlers.DateHandler}. */
	private static LocalDate date(final Object value) {
		if (value instanceof LocalDate date) {
			return date;
		}
		if (value instanceof java.sql.Date date) {
			return date.toLocalDate();
		}
		if (value instanceof java.sql.Timestamp timestamp) {
			return timestamp.toLocalDateTime().toLocalDate();
		}
		if (value instanceof LocalDateTime dateTime) {
			return dateTime.toLocalDate();
		}
		if (value instanceof java.util.Date date) {
			return new java.sql.Date(date.getTime()).toLocalDate();
		}
		if (value instanceof CharSequence text) {
			return LocalDate.parse(text.toString().trim());
		}
		throw unsupported(value);
	}

	/** Same inputs as {@code DuckDbJdbcTypeHandlers.TimeHandler}. */
	private static LocalTime time(final Object value) {
		if (value instanceof LocalTime time) {
			return time;
		}
		if (value instanceof java.sql.Time time) {
			return time.toLocalTime().withNano((int) Math.floorMod(time.getTime(), 1000L) * 1_000_000);
		}
		if (value instanceof java.sql.Timestamp timestamp) {
			return timestamp.toLocalDateTime().toLocalTime();
		}
		if (value instanceof LocalDateTime dateTime) {
			return dateTime.toLocalTime();
		}
		if (value instanceof CharSequence text) {
			return LocalTime.parse(text.toString().trim());
		}
		throw unsupported(value);
	}

	private static LocalDateTime timestamp(final Object value) {
		if (value instanceof LocalDateTime dateTime) {
			return dateTime;
		}
		if (value instanceof java.sql.Timestamp timestamp) {
			return timestamp.toLocalDateTime();
		}
		if (value instanceof java.sql.Date date) {
			return date.toLocalDate().atStartOfDay();
		}
		if (value instanceof LocalDate date) {
			return date.atStartOfDay();
		}
		if (value instanceof java.util.Date date) {
			return new java.sql.Timestamp(date.getTime()).toLocalDateTime();
		}
		if (value instanceof CharSequence text) {
			return LocalDateTime.parse(text.toString().trim().replace(' ', 'T'));
		}
		throw unsupported(value);
	}

	/**
	 * Same inputs as {@code DuckDbJdbcTypeHandlers.TimestampWithTimeZoneHandler}.
	 */
	private static OffsetDateTime timestampWithTimeZone(final Object value) {
		if (value instanceof OffsetDateTime dateTime) {
			return dateTime;
		}
		if (value instanceof ZonedDateTime dateTime) {
			return dateTime.toOffsetDateTime();
		}
		if (value instanceof Instant instant) {
			return instant.atOffset(ZoneOffset.UTC);
		}
		if (value instanceof java.util.Date date) {
			final Instant instant = date instanceof java.sql.Timestamp timestamp ? timestamp.toInstant()
					: date.toInstant();
			return instant.atOffset(ZoneOffset.UTC);
		}
		if (value instanceof CharSequence text) {
			return OffsetDateTime.parse(text.toString().trim());
		}
		throw unsupported(value);
	}

	private static UUID uuid(final Object value) {
		if (value instanceof UUID uuid) {
			return uuid;
		}
		if (value instanceof CharSequence text) {
			return UUID.fromString(text.toString().trim());
		}
		throw unsupported(value);
	}

	private static IllegalArgumentException unsupported(final Object value) {
		return new IllegalArgumentException("unsupported value type");
	}
}

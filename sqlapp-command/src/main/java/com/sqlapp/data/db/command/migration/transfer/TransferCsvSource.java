/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.DefaultBulkMigrationKeysetCodec;

/**
 * Package-owned CSV/TSV/JSONL source; no database access. Numeric keys have a
 * portable ordering.
 */
final class TransferCsvSource implements BulkMigrationKeysetSource {
	private final Table table;
	private final Path file;
	private final TransferJobConfiguration.FileTransfer settings;
	private final String fingerprint;
	private final long expectedRows;
	private final List<Column> keys;

	TransferCsvSource(Table table, Path file, TransferJobConfiguration.FileTransfer settings, String fingerprint,
			long expectedRows) {
		this(table, file, settings, fingerprint, expectedRows, true);
	}

	TransferCsvSource(Table table, Path file, TransferJobConfiguration.FileTransfer settings, String fingerprint,
			long expectedRows, boolean requireImportKeys) {
		this.table = table;
		this.file = file;
		this.settings = settings;
		this.fingerprint = fingerprint;
		this.expectedRows = expectedRows;
		if (requireImportKeys && table.getPrimaryKeyConstraint() == null)
			throw new IllegalArgumentException("File import requires a modeled primary key.");
		var primary = numericKeys(table);
		boolean importKeys = !primary.isEmpty();
		if (requireImportKeys && !importKeys)
			throw new IllegalArgumentException("Initial File import requires NOT NULL numeric primary keys.");
		keys = importKeys ? primary : List.of();
		validateColumnTypes(table);
	}

	static boolean supportsImportKeys(Table table) {
		return !numericKeys(table).isEmpty();
	}

	private static List<Column> numericKeys(Table table) {
		var primary = table.getPrimaryKeyConstraint() == null ? List.<Column>of()
				: table.getPrimaryKeyConstraint().getColumns().stream()
						.map(ref -> table.getColumns().get(ref.getName())).toList();
		return !primary.isEmpty() && primary.stream().allMatch(key -> key != null && key.isNotNull() && numeric(key))
				? primary
				: List.of();
	}

	static void validateColumnTypes(Table table) {
		for (final Column column : table.getColumns()) {
			// Validate unsupported types even when the table is empty or the column
			// contains only NULLs.
			decode(column, null);
		}
	}

	@Override
	public Table getTable() {
		return table;
	}

	@Override
	public String getConfigurationFingerprint() {
		return fingerprint;
	}

	@Override
	public String resumeToken(Row row) {
		if (keys.isEmpty())
			throw new IllegalArgumentException("A keyless preview cannot create a resume token.");
		return new DefaultBulkMigrationKeysetCodec().encode(keys, row);
	}

	@Override
	public Iterator<Row> iterator(String resumeToken) {
		try {
			return new Rows(resumeToken);
		} catch (Exception failure) {
			throw new IllegalArgumentException("Unable to open packaged data.");
		}
	}

	void validate() {
		final var rows = iterator(null);
		Throwable primary = null;
		try {
			while (rows.hasNext())
				rows.next();
		} catch (RuntimeException | Error failure) {
			primary = failure;
			throw failure;
		} finally {
			com.sqlapp.util.iterator.IteratorCloseUtils.close(primary, rows);
		}
	}

	private final class Rows implements Iterator<Row>, AutoCloseable {
		private final java.io.Reader reader;
		private final com.univocity.parsers.csv.CsvParser parser;
		private String seek;
		private Row next;
		private Row previous;
		private long count;
		private boolean ended;

		Rows(String token) throws Exception {
			if (token != null && keys.isEmpty())
				throw new IllegalArgumentException("A keyless preview cannot resume.");
			seek = token;
			reader = new java.io.BufferedReader(new java.io.InputStreamReader(Files.newInputStream(file),
					Charset.forName(settings.encoding()).newDecoder()));
			if (settings.format() == TransferJobConfiguration.FileFormat.JSONL) {
				parser = null;
				return;
			}
			final var options = new com.univocity.parsers.csv.CsvParserSettings();
			options.setEmptyValue("");
			options.setIgnoreLeadingWhitespaces(false);
			options.setIgnoreTrailingWhitespaces(false);
			options.setIgnoreLeadingWhitespacesInQuotes(false);
			options.setIgnoreTrailingWhitespacesInQuotes(false);
			options.setNormalizeLineEndingsWithinQuotes(false);
			options.setMaxCharsPerColumn(1_048_576);
			options.setMaxColumns(table.getColumns().size());
			options.setReadInputOnSeparateThread(false);
			options.setSkipEmptyLines(false);
			options.getFormat().setLineSeparator("\n");
			options.getFormat().setDelimiter(settings.format() == TransferJobConfiguration.FileFormat.TSV ? '\t' : ',');
			parser = new com.univocity.parsers.csv.CsvParser(options);
			try {
				parser.beginParsing(reader);
				if (settings.headerMode() != TransferJobConfiguration.HeaderMode.NONE) {
					final String[] header = parser.parseNext();
					if (header == null)
						throw new IllegalArgumentException("The configured header row is missing.");
					if (settings.headerMode() == TransferJobConfiguration.HeaderMode.VALIDATE && !java.util.Arrays
							.equals(header, table.getColumns().stream().map(Column::getName).toArray(String[]::new)))
						throw new IllegalArgumentException("Delimited header differs from canonical Schema.");
				}
			} catch (Exception failure) {
				close();
				throw failure;
			}
		}

		@Override
		public boolean hasNext() {
			if (next != null)
				return true;
			if (ended)
				return false;
			try {
				while (true) {
					final String[] values = parser == null ? TransferJsonlCodec.read(reader, table)
							: parser.parseNext();
					if (values == null) {
						ended = true;
						if (count != expectedRows || seek != null)
							throw new IllegalArgumentException(
									"File row count or resume token differs from its manifest.");
						return false;
					}
					if (values.length != table.getColumns().size())
						throw new IllegalArgumentException("CSV column count differs from canonical Schema.");
					final Row row = table.newRow();
					for (int index = 0; index < values.length; index++) {
						boolean isNull = values[index] == null
								? parser == null
										|| settings.emptyFieldMode() == TransferJobConfiguration.EmptyFieldMode.NULL
								: settings.nullValue() != null && values[index].equals(settings.nullValue());
						if (isNull && table.getColumns().get(index).isNotNull())
							throw new IllegalArgumentException("File contains NULL for a NOT NULL column.");
						row.put(index, decode(table.getColumns().get(index),
								isNull ? null : values[index] == null ? "" : values[index]));
					}
					if (!keys.isEmpty() && previous != null && compare(previous, row) >= 0)
						throw new IllegalArgumentException("File keys must be unique and in ascending numeric order.");
					if (!keys.isEmpty())
						resumeToken(row); // Reject NULL primary keys before any write.
					previous = row;
					count = Math.addExact(count, 1);
					if (count > expectedRows)
						throw new IllegalArgumentException("File exceeds manifest row count.");
					if (seek != null) {
						if (seek.equals(resumeToken(row)))
							seek = null;
						continue;
					}
					next = row;
					return true;
				}
			} catch (RuntimeException | java.io.IOException failure) {
				throw new IllegalArgumentException(
						"Invalid " + settings.format() + " rows, types, fields, key order or recorded count.");
			}
		}

		@Override
		public Row next() {
			if (!hasNext())
				throw new NoSuchElementException();
			final Row value = next;
			next = null;
			return value;
		}

		@Override
		public void close() throws java.io.IOException {
			try {
				if (parser != null)
					parser.stopParsing();
			} finally {
				reader.close();
			}
		}
	}

	private int compare(Row left, Row right) {
		for (Column key : keys) {
			final int compared = new java.math.BigDecimal(left.get(key).toString())
					.compareTo(new java.math.BigDecimal(right.get(key).toString()));
			if (compared != 0)
				return compared;
		}
		return 0;
	}

	private static boolean numeric(Column column) {
		return switch (column.getDataType().getJdbcType()) {
		case TINYINT, SMALLINT, INTEGER, BIGINT, NUMERIC, DECIMAL -> true;
		default -> false;
		};
	}

	private static Object decode(Column column, String value) {
		if (TransferCsvTemporalCodec.temporal(column))
			return TransferCsvTemporalCodec.decode(column, value);
		final var type = column.getDataType().getJdbcType();
		if (!numeric(column) && type != java.sql.JDBCType.BOOLEAN && type != java.sql.JDBCType.BIT
				&& type != java.sql.JDBCType.CHAR && type != java.sql.JDBCType.VARCHAR
				&& type != java.sql.JDBCType.LONGVARCHAR && type != java.sql.JDBCType.NCHAR
				&& type != java.sql.JDBCType.NVARCHAR && type != java.sql.JDBCType.LONGNVARCHAR
				&& type != java.sql.JDBCType.REAL && type != java.sql.JDBCType.FLOAT
				&& type != java.sql.JDBCType.DOUBLE)
			throw new IllegalArgumentException("File import does not support this canonical column type.");
		if (value == null)
			return null;
		return switch (type) {
		case TINYINT -> new java.math.BigDecimal(value).byteValueExact();
		case SMALLINT -> new java.math.BigDecimal(value).shortValueExact();
		case INTEGER -> new java.math.BigDecimal(value).intValueExact();
		case BIGINT -> new java.math.BigDecimal(value).longValueExact();
		case NUMERIC, DECIMAL -> new java.math.BigDecimal(value);
		case BOOLEAN, BIT -> {
			if (!value.equals("true") && !value.equals("false"))
				throw new IllegalArgumentException("Invalid boolean.");
			yield Boolean.valueOf(value);
		}
		case REAL, FLOAT, DOUBLE -> {
			final double number = Double.parseDouble(value);
			if (!Double.isFinite(number))
				throw new IllegalArgumentException("Nonfinite number.");
			if (column.getDataType().getDefaultClass() == Float.class) {
				final float single = Float.parseFloat(value);
				if (!Float.isFinite(single))
					throw new IllegalArgumentException("Nonfinite number.");
				yield Float.valueOf(single);
			}
			yield Double.valueOf(number);
		}
		default -> value;
		};
	}
}

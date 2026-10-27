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
 * Package-owned CSV source; no database access. Numeric keys have a portable
 * ordering.
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
		this.table = table;
		this.file = file;
		this.settings = settings;
		this.fingerprint = fingerprint;
		this.expectedRows = expectedRows;
		if (table.getPrimaryKeyConstraint() == null)
			throw new IllegalArgumentException("CSV import requires a modeled primary key.");
		keys = table.getPrimaryKeyConstraint().getColumns().stream().map(ref -> table.getColumns().get(ref.getName()))
				.toList();
		if (keys.isEmpty() || keys.stream().anyMatch(key -> !key.isNotNull() || !numeric(key)))
			throw new IllegalArgumentException("Initial CSV import requires NOT NULL numeric primary keys.");
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
		return new DefaultBulkMigrationKeysetCodec().encode(keys, row);
	}

	@Override
	public Iterator<Row> iterator(String resumeToken) {
		try {
			return new Rows(resumeToken);
		} catch (Exception failure) {
			throw new IllegalArgumentException("Unable to open packaged CSV.");
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
			seek = token;
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
			parser = new com.univocity.parsers.csv.CsvParser(options);
			reader = new java.io.InputStreamReader(Files.newInputStream(file),
					Charset.forName(settings.encoding()).newDecoder());
			try {
				parser.beginParsing(reader);
				final String[] header = parser.parseNext();
				if (header == null || !java.util.Arrays.equals(header,
						table.getColumns().stream().map(Column::getName).toArray(String[]::new)))
					throw new IllegalArgumentException("CSV header differs from canonical Schema.");
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
					final String[] values = parser.parseNext();
					if (values == null) {
						ended = true;
						if (count != expectedRows || seek != null)
							throw new IllegalArgumentException(
									"CSV row count or resume token differs from its manifest.");
						return false;
					}
					if (values.length != table.getColumns().size())
						throw new IllegalArgumentException("CSV column count differs from canonical Schema.");
					final Row row = table.newRow();
					for (int index = 0; index < values.length; index++) {
						if (values[index] == null)
							throw new IllegalArgumentException(
									"CSV uses an implicit NULL instead of the configured token.");
						if (values[index].equals(settings.nullValue()) && table.getColumns().get(index).isNotNull())
							throw new IllegalArgumentException("CSV contains NULL for a NOT NULL column.");
						row.put(index, decode(table.getColumns().get(index),
								values[index].equals(settings.nullValue()) ? null : values[index]));
					}
					if (previous != null && compare(previous, row) >= 0)
						throw new IllegalArgumentException("CSV keys must be unique and in ascending numeric order.");
					resumeToken(row); // Reject NULL primary keys before any write.
					previous = row;
					count = Math.addExact(count, 1);
					if (count > expectedRows)
						throw new IllegalArgumentException("CSV exceeds manifest row count.");
					if (seek != null) {
						if (seek.equals(resumeToken(row)))
							seek = null;
						continue;
					}
					next = row;
					return true;
				}
			} catch (RuntimeException failure) {
				throw new IllegalArgumentException("Invalid CSV rows, types, header, key order or recorded count.");
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
			throw new IllegalArgumentException("CSV import does not support this canonical column type.");
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

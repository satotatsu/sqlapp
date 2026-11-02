/* Copyright (C) 2026 Tatsuo Satoh */
package com.sqlapp.data.schemas.rowiterator;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.schema.MessageType;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;

/** Reads flat Parquet rows, preserving existing target column definitions. */
public final class ParquetRowIteratorHandler extends AbstractRowIteratorHandler {
	private final File file;

	public ParquetRowIteratorHandler(final File file) {
		this(file, (row, column, value) -> value);
	}

	public ParquetRowIteratorHandler(final File file, final RowValueConverter converter) {
		super(converter);
		this.file = Objects.requireNonNull(file, "file");
	}

	@Override
	public Iterator<Row> iterator(final RowCollection rows) {
		return new AbstractRowIterator<Group>(rows, 0, getRowValueConverter()) {
			private ParquetReader<Group> reader;
			private MessageType schema;
			private Column[] columns;
			private Group next;
			private boolean loaded;
			private long rowNumber;

			@Override
			protected void preInitialize() throws Exception {
				schema = ParquetTableIO.readSchema(file.toPath());
				reader = ParquetTableIO.reader(file.toPath());
			}

			@Override
			protected void initializeColumn() {
				boolean infer = table.getColumns().isEmpty();
				columns = new Column[schema.getFieldCount()];
				var assigned = new HashSet<Column>();
				var fieldNames = new HashSet<String>();
				for (int i = 0; i < columns.length; i++) {
					Column source = ParquetTableIO.column(schema.getType(i));
					final String identity = table.getColumns().isCaseSensitive() ? source.getName()
							: source.getName().toLowerCase(java.util.Locale.ROOT);
					if (!fieldNames.add(identity)) throw ParquetTableIO.unsupported(source, "duplicate or ambiguous field name");
					if (infer) {
						table.getColumns().add(source);
						columns[i] = source;
					} else {
						var exact = table.getColumns().stream().filter(c -> c.getName().equals(source.getName())).toList();
						var matches = exact.isEmpty() ? table.getColumns().stream()
								.filter(c -> c.getName().equalsIgnoreCase(source.getName())).toList() : exact;
						if (matches.size() != 1) throw ParquetTableIO.unsupported(source, "missing or ambiguous target column");
						columns[i] = matches.getFirst();
						if (!assigned.add(columns[i])) throw ParquetTableIO.unsupported(source, "multiple fields map to one target column");
						if (!compatible(source, columns[i])) throw ParquetTableIO.unsupported(source,
								"incompatible target type " + columns[i].getDataType());
					}
				}
			}

			@Override
			protected boolean hasNextInternal() throws Exception {
				if (!loaded) {
					next = reader.read();
					loaded = true;
				}
				return next != null;
			}

			@Override
			public Row next() {
				if (reader == null && !hasNext()) {
					throw new NoSuchElementException();
				}
				return super.next();
			}

			@Override
			protected Group read() throws Exception {
				if (!hasNextInternal()) throw new NoSuchElementException();
				loaded = false;
				return next;
			}
			@Override
			protected void set(final Group group, final Row row) {
				row.setDataSourceInfo(file.getAbsolutePath());
				row.setDataSourceRowNumber(++rowNumber);
				for (int i = 0; i < columns.length; i++) {
					Object value = ParquetTableIO.value(group, i);
					if (value instanceof LocalTime time && time.getNano() != 0)
						throw ParquetTableIO.unsupported(columns[i], "fractional TIME cannot be preserved by the Schema TIME model");
					validateValue(columns[i], value);
					put(row, columns[i], value);
				}
			}
			@Override
			protected void doClose() {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					} finally {
						reader = null;
					}
				}
			}

			@Override
			protected void closeSilent() {
				try {
					close();
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new IllegalStateException("Unable to close Parquet input: " + file, e);
				}
			}
		};
	}

	private static void validateValue(Column column, Object value) {
		if (value instanceof Number number) {
			if (column.getDataType() == DataType.TINYINT
					&& (number.longValue() < Byte.MIN_VALUE || number.longValue() > Byte.MAX_VALUE))
				throw ParquetTableIO.unsupported(column, "signed 8-bit integer range exceeded");
			if (column.getDataType() == DataType.SMALLINT
					&& (number.longValue() < Short.MIN_VALUE || number.longValue() > Short.MAX_VALUE))
				throw ParquetTableIO.unsupported(column, "signed 16-bit integer range exceeded");
		}
		if (value instanceof BigDecimal decimal) {
			if (column.getScale() != null) {
				try {
					decimal = decimal.setScale(column.getScale(), RoundingMode.UNNECESSARY);
				} catch (ArithmeticException e) {
					throw new IllegalArgumentException("Parquet column '" + column.getName()
							+ "' exceeds target decimal scale " + column.getScale() + ".", e);
				}
			}
			if (column.getLength() != null && decimal.precision() > column.getLength())
				throw ParquetTableIO.unsupported(column, "target decimal precision exceeded");
		}
		if (value instanceof String text && column.getLength() != null && text.codePointCount(0, text.length()) > column.getLength())
			throw ParquetTableIO.unsupported(column, "target text length exceeded");
		if (value instanceof byte[] bytes && column.getLength() != null && bytes.length > column.getLength())
			throw ParquetTableIO.unsupported(column, "target binary length exceeded");
		if (value instanceof Timestamp timestamp && column.getScale() != null && column.getScale() < 9) {
			int scale = column.getScale();
			if (scale < 0 || timestamp.getNanos() % (int) Math.pow(10, 9 - scale) != 0)
				throw ParquetTableIO.unsupported(column, "target timestamp precision would lose data");
		}
	}

	private static boolean compatible(Column source, Column target) {
		var s = source.getDataType();
		var t = target.getDataType();
		if (t == null) return false;
		if (s == t) return true;
		if (s.isCharacter() && t.isCharacter()) return true;
		if (s.isBinary() && t.isBinary()) return true;
		return (s == DataType.DECIMAL && t == DataType.NUMERIC)
				|| (s == DataType.BOOLEAN && t == DataType.BIT)
				|| (s == DataType.DOUBLE && t == DataType.FLOAT)
				|| (s == DataType.TIMESTAMP && t == DataType.DATETIME);
	}
}

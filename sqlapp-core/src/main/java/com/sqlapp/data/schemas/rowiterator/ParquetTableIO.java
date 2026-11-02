/* Copyright (C) 2026 Tatsuo Satoh */
package com.sqlapp.data.schemas.rowiterator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Map;

import org.apache.parquet.conf.ParquetConfiguration;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.RecordConsumer;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

/** Typed, flat local Parquet files. Unsupported types fail before output replacement. */
public final class ParquetTableIO {

	private ParquetTableIO() {
	}

	static Iterable<Map<String, Object>> maps(final Path path) {
		return new com.sqlapp.iterable.IndexedConvertIterable<Row, Map<String, Object>>(() -> {
			final Table table = new Table();
			table.setRowIteratorHandler(new ParquetRowIteratorHandler(path.toFile()));
			return table.getRows();
		}, (index, row) -> row.getValuesAsMap());
	}

	/** Writes one table with bounded row-group buffering and replaces output only on success. */
	public static long write(final Path output, final Table table) throws Exception {
		final Iterator<Row> rows = table.getRows().iterator();
		Path temporary = null;
		Throwable failure = null;
		boolean rowsClosed = false;
		try {
			// Typed readers initialize metadata before rows; text readers may need the first row.
			boolean more = rows.hasNext();
			Row first = null;
			if (more && table.getColumns().isEmpty()) {
				first = rows.next();
				more = rows.hasNext();
			}
			final MessageType schema = schema(table);
			final Path target = output.toAbsolutePath();
			Files.createDirectories(target.getParent());
			temporary = Files.createTempFile(target.getParent(), ".parquet-", ".tmp");
			Files.delete(temporary);
			long count = 0;
			try (ParquetWriter<Row> writer = new Builder(temporary, schema, table)
					.withConf(new PlainParquetConfiguration()).withRowGroupSize(16L * 1024 * 1024).build()) {
				if (first != null) { writer.write(first); count++; }
				while (more) {
					writer.write(rows.next());
					count++;
					more = rows.hasNext();
				}
			}
			rowsClosed = true;
			com.sqlapp.util.iterator.IteratorCloseUtils.close(null, rows);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return count;
		} catch (Exception | Error e) {
			failure = e;
			throw e;
		} finally {
			try {
				if (!rowsClosed) com.sqlapp.util.iterator.IteratorCloseUtils.close(failure, rows);
			} catch (Exception e) {
				if (failure != null) {
					failure.addSuppressed(e);
				} else {
					failure = e;
					throw e;
				}
			} finally {
				if (temporary != null) {
					try { Files.deleteIfExists(temporary); }
					catch (IOException e) { if (failure != null) failure.addSuppressed(e); else throw e; }
				}
			}
		}
	}

	static MessageType schema(final Table table) {
		if (table.getColumns().isEmpty()) throw new IllegalArgumentException("Parquet requires typed columns; supply a Schema table.");
		final var fields = new java.util.ArrayList<Type>();
		for (Column c : table.getColumns()) fields.add(field(c));
		return new MessageType("sqlapp", fields);
	}

	private static Type field(final Column c) {
		final DataType type = c.getDataType();
		if (type == null) throw unsupported(c, "column type is missing");
		PrimitiveTypeName physical;
		LogicalTypeAnnotation logical = null;
		switch (type) {
		case BOOLEAN, BIT -> physical = PrimitiveTypeName.BOOLEAN;
		case TINYINT, SMALLINT, INT -> {
			physical = PrimitiveTypeName.INT32;
			logical = LogicalTypeAnnotation.intType(type == DataType.TINYINT ? 8 : type == DataType.SMALLINT ? 16 : 32, true);
		}
		case BIGINT -> physical = PrimitiveTypeName.INT64;
		case REAL -> physical = PrimitiveTypeName.FLOAT;
		case FLOAT, DOUBLE -> physical = PrimitiveTypeName.DOUBLE;
		case CHAR, VARCHAR, NCHAR, NVARCHAR, LONGVARCHAR, LONGNVARCHAR, CLOB, NCLOB -> {
			physical = PrimitiveTypeName.BINARY;
			logical = LogicalTypeAnnotation.stringType();
		}
		case BINARY, VARBINARY, LONGVARBINARY, BLOB -> physical = PrimitiveTypeName.BINARY;
		case DECIMAL, NUMERIC -> {
			if (c.getLength() == null || c.getScale() == null || c.getLength() < 1
					|| c.getLength() > Integer.MAX_VALUE || c.getScale() < 0 || c.getScale() > c.getLength())
				throw unsupported(c, "DECIMAL requires explicit positive precision and valid scale");
			physical = PrimitiveTypeName.BINARY;
			logical = LogicalTypeAnnotation.decimalType(c.getScale(), c.getLength().intValue());
		}
		case DATE -> { physical = PrimitiveTypeName.INT32; logical = LogicalTypeAnnotation.dateType(); }
		case TIME -> { physical = PrimitiveTypeName.INT64; logical = LogicalTypeAnnotation.timeType(false, LogicalTypeAnnotation.TimeUnit.NANOS); }
		case TIMESTAMP, DATETIME -> { physical = PrimitiveTypeName.INT64; logical = LogicalTypeAnnotation.timestampType(false, LogicalTypeAnnotation.TimeUnit.NANOS); }
		default -> throw unsupported(c, "unsupported type " + type);
		}
		var builder = Types.optional(physical);
		if (logical != null) builder.as(logical);
		return builder.named(c.getName());
	}

	static IllegalArgumentException unsupported(Column c, String detail) {
		return new IllegalArgumentException("Parquet column '" + c.getName() + "': " + detail);
	}

	static MessageType readSchema(Path path) throws IOException {
		try (var reader = ParquetFileReader.open(new LocalInputFile(path),
				org.apache.parquet.ParquetReadOptions.builder(new PlainParquetConfiguration()).build())) {
			return reader.getFooter().getFileMetaData().getSchema();
		}
	}

	static ParquetReader<Group> reader(Path path) throws IOException {
		return new ParquetReader.Builder<Group>(new LocalInputFile(path), new PlainParquetConfiguration()) {
			@Override protected org.apache.parquet.hadoop.api.ReadSupport<Group> getReadSupport() {
				return new GroupReadSupport();
			}
		}.build();
	}

	static Column column(Type field) {
		if (!field.isPrimitive() || field.isRepetition(Type.Repetition.REPEATED))
			throw new IllegalArgumentException("Unsupported nested/repeated Parquet column: " + field.getName());
		PrimitiveType p = field.asPrimitiveType();
		var annotation = p.getLogicalTypeAnnotation();
		Column c = new Column(field.getName());
		if (annotation instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation) c.setDataType(DataType.VARCHAR);
		else if (annotation instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation d) {
			c.setDataType(DataType.DECIMAL); c.setLength(d.getPrecision()); c.setScale(d.getScale());
		} else if (annotation instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) c.setDataType(DataType.DATE);
		else if (annotation instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation t) {
			if (t.isAdjustedToUTC()) throw unsupported(c, "UTC-adjusted TIME is unsupported");
			c.setDataType(DataType.TIME);
		} else if (annotation instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation t) {
			if (t.isAdjustedToUTC()) throw unsupported(c, "UTC-adjusted TIMESTAMP requires an explicit timezone policy");
			c.setDataType(DataType.TIMESTAMP);
		} else if (annotation instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation i) {
			if (!i.isSigned()) throw unsupported(c, "unsigned integers are unsupported");
			c.setDataType(i.getBitWidth() == 8 ? DataType.TINYINT : i.getBitWidth() == 16 ? DataType.SMALLINT
					: i.getBitWidth() == 32 ? DataType.INT : DataType.BIGINT);
		} else {
			if (annotation != null) throw unsupported(c, "unsupported logical type " + annotation);
			c.setDataType(switch (p.getPrimitiveTypeName()) {
			case BOOLEAN -> DataType.BOOLEAN;
			case INT32 -> DataType.INT;
			case INT64 -> DataType.BIGINT;
			case FLOAT -> DataType.REAL;
			case DOUBLE -> DataType.DOUBLE;
			case BINARY, FIXED_LEN_BYTE_ARRAY -> DataType.VARBINARY;
			default -> throw unsupported(c, "unsupported physical type " + p.getPrimitiveTypeName());
			});
		}
		return c;
	}

	static Object value(Group group, int index) {
		if (group.getFieldRepetitionCount(index) == 0) return null;
		PrimitiveType p = group.getType().getType(index).asPrimitiveType();
		var a = p.getLogicalTypeAnnotation();
		if (a instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation) return group.getString(index, 0);
		if (a instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation d) {
			BigInteger integer = switch (p.getPrimitiveTypeName()) {
			case INT32 -> BigInteger.valueOf(group.getInteger(index, 0));
			case INT64 -> BigInteger.valueOf(group.getLong(index, 0));
			default -> new BigInteger(group.getBinary(index, 0).getBytes());
			};
			return new BigDecimal(integer, d.getScale());
		}
		if (a instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) return java.sql.Date.valueOf(LocalDate.ofEpochDay(group.getInteger(index, 0)));
		if (a instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation t) {
			long time = p.getPrimitiveTypeName() == PrimitiveTypeName.INT32 ? group.getInteger(index, 0) : group.getLong(index, 0);
			return LocalTime.ofNanoOfDay(Math.multiplyExact(time, nanos(t.getUnit())));
		}
		if (a instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation t) {
			long time = group.getLong(index, 0), units = 1_000_000_000L / nanos(t.getUnit());
			return java.sql.Timestamp.valueOf(LocalDateTime.ofEpochSecond(Math.floorDiv(time, units),
					(int) (Math.floorMod(time, units) * nanos(t.getUnit())), ZoneOffset.UTC));
		}
		return switch (p.getPrimitiveTypeName()) {
		case BOOLEAN -> group.getBoolean(index, 0);
		case INT32 -> group.getInteger(index, 0);
		case INT64 -> group.getLong(index, 0);
		case FLOAT -> group.getFloat(index, 0);
		case DOUBLE -> group.getDouble(index, 0);
		case BINARY, FIXED_LEN_BYTE_ARRAY -> group.getBinary(index, 0).getBytes();
		default -> throw new IllegalArgumentException("Unsupported Parquet type: " + p);
		};
	}

	private static long nanos(LogicalTypeAnnotation.TimeUnit unit) {
		return switch (unit) { case MILLIS -> 1_000_000L; case MICROS -> 1_000L; case NANOS -> 1L; };
	}

	@SuppressWarnings("deprecation") // Required compatibility overrides in Parquet's builder/write support API.
	private static final class Builder extends ParquetWriter.Builder<Row, Builder> {
		private final MessageType schema;
		private final Table table;
		private final java.util.List<DataType> columnTypes;
		Builder(Path path, MessageType schema, Table table) {
			super(new LocalOutputFile(path)); this.schema = schema; this.table = table;
			this.columnTypes = table.getColumns().stream().map(Column::getDataType).toList();
		}
		@Override protected Builder self() { return this; }
		@Override protected WriteSupport<Row> getWriteSupport(org.apache.hadoop.conf.Configuration conf) { return support(); }
		@Override protected WriteSupport<Row> getWriteSupport(ParquetConfiguration conf) { return support(); }
		private WriteSupport<Row> support() {
			return new WriteSupport<>() {
				private RecordConsumer consumer;
				@Override public WriteContext init(org.apache.hadoop.conf.Configuration conf) { return new WriteContext(schema, Map.of()); }
				@Override public WriteContext init(ParquetConfiguration conf) { return new WriteContext(schema, Map.of()); }
				@Override public void prepareForWrite(RecordConsumer consumer) { this.consumer = consumer; }
				@Override public void write(Row row) {
					if (table.getColumns().size() != schema.getFieldCount())
						throw new IllegalArgumentException("Parquet input columns changed after the first row; supply a typed Schema XML file.");
					consumer.startMessage();
					for (int i = 0; i < schema.getFieldCount(); i++) {
						Column c = table.getColumns().get(i);
						if (!c.getName().equals(schema.getFieldName(i)) || c.getDataType() != columnTypes.get(i))
							throw unsupported(c, "input type changed after the first row; supply a typed Schema XML file");
						Object v = row.get(c);
						if (v == null) continue;
						consumer.startField(c.getName(), i);
						PrimitiveType p = schema.getType(i).asPrimitiveType();
						var a = p.getLogicalTypeAnnotation();
						try {
						if (a instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation) {
							if (!(v instanceof CharSequence)) throw unsupported(c, "expected text, received " + v.getClass().getName());
							consumer.addBinary(Binary.fromString(v.toString()));
						} else if (a instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation d) {
							BigDecimal decimal = new BigDecimal(v.toString()).setScale(d.getScale(), java.math.RoundingMode.UNNECESSARY);
							if (decimal.precision() > d.getPrecision()) throw unsupported(c, "decimal precision exceeded");
							consumer.addBinary(Binary.fromConstantByteArray(decimal.unscaledValue().toByteArray()));
						} else if (a instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) {
							LocalDate date = v instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) v;
							consumer.addInteger(Math.toIntExact(date.toEpochDay()));
						} else if (a instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation) {
							LocalTime time = v instanceof java.sql.Time t ? t.toLocalTime() : (LocalTime) v;
							if (time.getNano() != 0 || (v instanceof java.sql.Time t && Math.floorMod(t.getTime(), 1000) != 0))
								throw unsupported(c, "fractional TIME cannot be preserved by the Schema TIME model");
							consumer.addLong(time.toNanoOfDay());
						} else if (a instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation) {
							LocalDateTime t = v instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) v;
							consumer.addLong(BigInteger.valueOf(t.toEpochSecond(ZoneOffset.UTC))
									.multiply(BigInteger.valueOf(1_000_000_000L)).add(BigInteger.valueOf(t.getNano())).longValueExact());
						} else switch (p.getPrimitiveTypeName()) {
						case BOOLEAN -> consumer.addBoolean((Boolean) v);
						case INT32 -> consumer.addInteger(new BigDecimal(v.toString()).intValueExact());
						case INT64 -> consumer.addLong(new BigDecimal(v.toString()).longValueExact());
						case FLOAT -> consumer.addFloat(((Number) v).floatValue());
						case DOUBLE -> consumer.addDouble(((Number) v).doubleValue());
						case BINARY -> consumer.addBinary(Binary.fromConstantByteArray((byte[]) v));
						default -> throw unsupported(c, "unsupported physical type");
						}
						} catch (ArithmeticException | ClassCastException e) {
							throw new IllegalArgumentException("Parquet column '" + c.getName()
									+ "' cannot preserve the value as " + c.getDataType() + ".", e);
						}
						consumer.endField(c.getName(), i);
					}
					consumer.endMessage();
				}
			};
		}
	}
}

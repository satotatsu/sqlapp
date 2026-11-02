/* Copyright (C) 2026 Tatsuo Satoh */
package com.sqlapp.data.schemas.rowiterator;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class ParquetTableIOTest {
	@TempDir Path directory;

	@Test void roundTripsTypedValuesNullsAndUnicode() throws Exception {
		Table source = new Table("items");
		column(source, "ID", DataType.INT);
		column(source, "TEXT", DataType.VARCHAR);
		column(source, "AMOUNT", DataType.DECIMAL).setLength(20).setScale(4);
		column(source, "STAMP", DataType.TIMESTAMP);
		column(source, "BYTES", DataType.VARBINARY);
		column(source, "FLAG", DataType.BOOLEAN);
		Row row = source.newRow();
		row.put("ID", 42); row.put("TEXT", "日本語😀");
		row.put("AMOUNT", new BigDecimal("1234567890123456.1234"));
		Timestamp timestamp = Timestamp.valueOf("1969-12-31 23:59:59.123456789");
		row.put("STAMP", timestamp); row.put("BYTES", new byte[] { 0, -1, 42 }); row.put("FLAG", true);
		source.getRows().add(row);
		source.getRows().add(source.newRow());
		Path file = directory.resolve("items.parquet");
		assertEquals(2, ParquetTableIO.write(file, source));
		Table target = read(file);
		Iterator<Row> rows = target.getRows().iterator();
		assertTrue(rows.hasNext());
		Row actual = rows.next();
		assertEquals(42, ((Number) actual.get("ID")).intValue());
		assertEquals("日本語😀", actual.get("TEXT"));
		assertEquals(new BigDecimal("1234567890123456.1234"), actual.get("AMOUNT"));
		assertEquals(timestamp, actual.get("STAMP"));
		assertArrayEquals(new byte[] { 0, -1, 42 }, (byte[]) actual.get("BYTES"));
		assertEquals(true, actual.get("FLAG"));
		assertTrue(rows.hasNext());
		assertNull(rows.next().get("ID"));
		assertFalse(rows.hasNext());
		Files.delete(file);
	}

	@Test void emptyTableRetainsSchema() throws Exception {
		Table source = new Table("empty");
		column(source, "ID", DataType.BIGINT);
		Path file = directory.resolve("empty.parquet");
		assertEquals(0, ParquetTableIO.write(file, source));
		Table target = read(file);
		assertFalse(target.getRows().iterator().hasNext());
		assertEquals(DataType.BIGINT, target.getColumns().get("ID").getDataType());
	}

	@Test void failurePreservesExistingOutputAndCleansTemporaryFile() throws Exception {
		Table source = new Table("items");
		column(source, "STAMP", DataType.TIMESTAMP);
		Row row = source.newRow(); row.put("STAMP", Timestamp.valueOf("1500-01-01 00:00:00")); source.getRows().add(row);
		Path file = directory.resolve("items.parquet"); Files.writeString(file, "previous");
		assertThrows(Exception.class, () -> ParquetTableIO.write(file, source));
		assertEquals("previous", Files.readString(file));
		try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
	}

	@Test void rejectsUnsupportedTypesAndCorruptFiles() throws Exception {
		Table source = new Table("items"); column(source, "STAMP", DataType.TIMESTAMP_WITH_TIMEZONE);
		assertThrows(IllegalArgumentException.class, () -> ParquetTableIO.write(directory.resolve("bad.parquet"), source));
		Path broken = Files.writeString(directory.resolve("broken.parquet"), "not parquet");
		assertThrows(RuntimeException.class, () -> read(broken).getRows().iterator().hasNext());
		Files.delete(broken);
	}

	@Test void closesOnEarlyTerminationAndRejectsMissingTargetColumns() throws Exception {
		Table source = new Table("items"); column(source, "ID", DataType.INT);
		Row row = source.newRow(); row.put("ID", 1); source.getRows().add(row);
		Path file = directory.resolve("items.parquet"); ParquetTableIO.write(file, source);
		Iterator<Row> iterator = read(file).getRows().iterator();
		assertTrue(iterator.hasNext()); ((AutoCloseable) iterator).close();
		Table target = new Table("target"); column(target, "OTHER", DataType.INT);
		target.setRowIteratorHandler(new ParquetRowIteratorHandler(file.toFile()));
		assertThrows(RuntimeException.class, () -> target.getRows().iterator().hasNext());
		Files.delete(file);
	}

	@Test void roundTripsScalarBoundariesAndEmptyValues() throws Exception {
		Table source = new Table("scalars");
		DataType[] types = { DataType.TINYINT, DataType.SMALLINT, DataType.INT, DataType.BIGINT,
				DataType.REAL, DataType.DOUBLE, DataType.DATE, DataType.TIME, DataType.VARCHAR, DataType.VARBINARY };
		Object[] values = { Byte.MIN_VALUE, Short.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE,
				Float.NaN, Double.POSITIVE_INFINITY, java.sql.Date.valueOf("1970-01-01"),
				java.sql.Time.valueOf("23:59:59"), "", new byte[0] };
		for (int i = 0; i < types.length; i++) column(source, "C" + i, types[i]);
		Row row = source.newRow();
		for (int i = 0; i < values.length; i++) row.put("C" + i, values[i]);
		source.getRows().add(row);
		Path file = directory.resolve("scalars.parquet"); ParquetTableIO.write(file, source);
		var iterator = read(file).getRows().iterator(); assertTrue(iterator.hasNext());
		Row actual = iterator.next();
		for (int i = 0; i < values.length - 1; i++) assertEquals(values[i], (Object) actual.get("C" + i), "C" + i);
		assertArrayEquals(new byte[0], (byte[]) actual.get("C9")); assertFalse(iterator.hasNext());
	}

	@Test void readsStandardLogicalEncodingsWithoutSqlappMetadata() throws Exception {
		Path file = fixture("message data { optional int32 D (DECIMAL(9,2)); optional int64 TS (TIMESTAMP(MICROS,false)); optional int32 T (TIME(MILLIS,false)); }",
				group -> { group.add("D", -12345); group.add("TS", -1L); group.add("T", 1000); });
		var iterator = read(file).getRows().iterator(); assertTrue(iterator.hasNext());
		Row row = iterator.next();
		assertEquals(new BigDecimal("-123.45"), row.get("D"));
		assertEquals(Timestamp.valueOf("1969-12-31 23:59:59.999999"), row.get("TS"));
		assertEquals(java.sql.Time.valueOf("00:00:01"), row.get("T"));
		assertFalse(iterator.hasNext());
	}

	@Test void rejectsTimezoneNestedAndFractionalTimeInsteadOfGuessing() throws Exception {
		Path utc = fixture("message data { optional int64 TS (TIMESTAMP(MICROS,true)); }", group -> group.add("TS", 0L));
		assertThrows(RuntimeException.class, () -> read(utc).getRows().iterator().hasNext());
		Path nested = fixture("message data { optional group N { optional int32 ID; } }", group -> {});
		assertThrows(RuntimeException.class, () -> read(nested).getRows().iterator().hasNext());
		Path fractional = fixture("message data { optional int64 T (TIME(NANOS,false)); }", group -> group.add("T", 1L));
		var iterator = read(fractional).getRows().iterator(); assertTrue(iterator.hasNext());
		assertThrows(RuntimeException.class, iterator::next);
		Files.delete(fractional);
	}

	@Test void rejectsTargetDecimalLossAndDoesNotChangeTargetDefinition() throws Exception {
		Path file = fixture("message data { optional int32 D (DECIMAL(9,2)); }", group -> group.add("D", 12345));
		Table target = new Table("target"); Column decimal = column(target, "D", DataType.DECIMAL).setLength(4).setScale(1);
		target.setRowIteratorHandler(new ParquetRowIteratorHandler(file.toFile()));
		var iterator = target.getRows().iterator(); assertTrue(iterator.hasNext());
		assertThrows(RuntimeException.class, iterator::next);
		assertEquals(1, decimal.getScale()); assertEquals(4L, decimal.getLength());
		Files.delete(file);
	}

	@Test void rejectsMissingDecimalPrecisionAndUntypedInput() {
		Table source = new Table("items"); column(source, "D", DataType.DECIMAL);
		assertThrows(IllegalArgumentException.class, () -> ParquetTableIO.write(directory.resolve("bad.parquet"), source));
		assertThrows(IllegalArgumentException.class, () -> ParquetTableIO.write(directory.resolve("empty.parquet"), new Table("empty")));
		assertThrows(IllegalArgumentException.class, () -> DataFormat.PARQUET.createMapIterable(new java.io.StringReader("")));
	}

	@Test void exposesRestartableMapRowsWithExplicitClose() throws Exception {
		Table source = new Table("items"); column(source, "ID", DataType.INT);
		var row = source.newRow(); row.put("ID", 42); source.getRows().add(row);
		Path file = directory.resolve("maps.parquet"); ParquetTableIO.write(file, source);
		assertTrue(DataFormat.PARQUET.supportsMapRows());
		var iterable = DataFormat.PARQUET.createMapIterable(file);
		for (int i = 0; i < 2; i++) {
			var iterator = iterable.iterator(); assertTrue(iterator.hasNext());
			assertEquals(42, ((Number) iterator.next().get("ID")).intValue());
			((AutoCloseable) iterator).close();
		}
		Files.delete(file);
	}

	@Test void rejectsAmbiguousNamesInCaseInsensitiveSchema() throws Exception {
		Path file = fixture("message data { optional int32 ID; optional int32 id; }", group -> {
			group.add("ID", 1); group.add("id", 2);
		});
		Table target = read(file); target.getColumns().setCaseSensitive(false);
		assertThrows(RuntimeException.class, () -> target.getRows().iterator().hasNext());
		Files.delete(file);
	}

	@Test void roundTripsTimestampInt64Endpoints() throws Exception {
		Table source = new Table("items"); column(source, "TS", DataType.TIMESTAMP);
		for (long nanos : new long[] { Long.MIN_VALUE, Long.MAX_VALUE }) {
			var row = source.newRow();
			row.put("TS", Timestamp.valueOf(java.time.LocalDateTime.ofEpochSecond(Math.floorDiv(nanos, 1_000_000_000L),
					(int) Math.floorMod(nanos, 1_000_000_000L), java.time.ZoneOffset.UTC)));
			source.getRows().add(row);
		}
		Path file = directory.resolve("endpoints.parquet"); ParquetTableIO.write(file, source);
		var iterator = read(file).getRows().iterator();
		for (var expected : source.getRows()) {
			assertTrue(iterator.hasNext()); assertEquals((Object) expected.get("TS"), (Object) iterator.next().get("TS"));
		}
		assertFalse(iterator.hasNext());
	}

	private Path fixture(String schemaText, java.util.function.Consumer<org.apache.parquet.example.data.Group> populate) throws Exception {
		var schema = org.apache.parquet.schema.MessageTypeParser.parseMessageType(schemaText);
		var group = new org.apache.parquet.example.data.simple.SimpleGroupFactory(schema).newGroup();
		populate.accept(group);
		Path file = directory.resolve(java.util.UUID.randomUUID() + ".parquet");
		try (var writer = org.apache.parquet.hadoop.example.ExampleParquetWriter.builder(new org.apache.parquet.io.LocalOutputFile(file))
				.withConf(new org.apache.parquet.conf.PlainParquetConfiguration()).withType(schema).build()) {
			writer.write(group);
		}
		return file;
	}

	private Table read(Path file) {
		Table table = new Table("items"); table.setRowIteratorHandler(FileRowIteratorFactory.create(file)); return table;
	}
	private Column column(Table table, String name, DataType type) {
		Column column = new Column(name); column.setDataType(type); table.getColumns().add(column); return column;
	}
}

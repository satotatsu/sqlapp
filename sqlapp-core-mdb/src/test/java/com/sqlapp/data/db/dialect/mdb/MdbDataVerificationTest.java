/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.spannm.jackcess.*;

class MdbDataVerificationTest {
	@TempDir
	Path directory;

	@Test
	void losslessNumericWideningAndExplicitTextToNumericConversionRemainSupported() throws Exception {
		final Path source = directory.resolve("types.accdb");
		try (var file = DatabaseBuilder.create(Database.FileFormat.V2019, source.toFile())) {
			new TableBuilder("ITEMS").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addColumn(new ColumnBuilder("TEXT_NUMBER", DataType.TEXT))
					.addColumn(new ColumnBuilder("DECIMAL", DataType.NUMERIC).withPrecision(10).withScale(3))
					.toTable(file).addRow(7, "001", new BigDecimal("12.500"));
		}
		final var result = MdbSchemaMigration.rebuild(source, directory.resolve("converted.accdb"), schema -> {
			final var columns = schema.getTables().get("ITEMS").getColumns();
			columns.get("ID").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			columns.get("TEXT_NUMBER").setDataType(com.sqlapp.data.db.datatype.DataType.INT);
			columns.get("DECIMAL").setDataType(com.sqlapp.data.db.datatype.DataType.DOUBLE);
		});
		assertEquals(1L, result.rowCounts().get("ITEMS"));
	}

	@Test
	void detectsChangedValuesWithTheSameCountsWithoutExposingData() throws Exception {
		final Path source = directory.resolve("source.accdb");
		final Path target = directory.resolve("target.accdb");
		try (var file = DatabaseBuilder.create(Database.FileFormat.V2019, source.toFile())) {
			file.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			new TableBuilder("ITEMS").addColumn(new ColumnBuilder("TXT", DataType.TEXT))
					.addColumn(new ColumnBuilder("AMOUNT", DataType.NUMERIC).withPrecision(23).withScale(7))
					.addColumn(new ColumnBuilder("BYTES", DataType.BINARY).withLength(8))
					.addColumn(new ColumnBuilder("STAMP", DataType.EXT_DATE_TIME)).toTable(file).addRow("private-value",
							new BigDecimal("1234567890123456.1234567"), new byte[] { 1, 2 },
							LocalDateTime.of(2026, 10, 29, 12, 30, 0, 123456700));
		}
		MdbSchemaMigration.rebuild(source, target,
				schema -> schema.getTables().get("ITEMS").getColumns().get("TXT").setName("RENAMED"));
		try (var original = new DatabaseBuilder().withPath(source).withReadOnly(true).open();
				var rebuilt = new DatabaseBuilder().withPath(target).open()) {
			original.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			rebuilt.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			final var table = rebuilt.getTable("ITEMS");
			final var row = table.iterator().next();
			row.put("BYTES", new byte[] { 1, 3 });
			table.updateRow(row);
			final var issues = new ArrayList<String>();
			MdbDataVerification.verify(original.getTable("ITEMS"), table, Map.of("renamed", "txt"), issues);
			assertEquals(1, issues.size());
			assertTrue(issues.getFirst().contains("ITEMS.BYTES"));
			assertFalse(issues.toString().contains("private-value"));
			assertEquals(original.getTable("ITEMS").getRowCount(), table.getRowCount());
		}
	}

	@Test
	void rejectsLossyNumericConversionAfterWriting() throws Exception {
		final Path source = directory.resolve("decimal.accdb");
		try (var file = DatabaseBuilder.create(Database.FileFormat.V2010, source.toFile())) {
			new TableBuilder("ITEMS").addColumn(new ColumnBuilder("VALUE", DataType.DOUBLE)).toTable(file)
					.addRow(16777217d);
		}
		final var error = assertThrows(MdbSchemaMigration.VerificationException.class,
				() -> MdbSchemaMigration.rebuild(source, directory.resolve("rounded.accdb"),
						schema -> schema.getTables().get("ITEMS").getColumns().get("VALUE")
								.setDataType(com.sqlapp.data.db.datatype.DataType.REAL)));
		assertTrue(error.issues().stream().anyMatch(issue -> issue.contains("Copied value differs")));
	}

	@Test
	void detectsDeletedAutoNumberHistoryIncludingAnEmptyTable() throws Exception {
		for (final var format : new Database.FileFormat[] { Database.FileFormat.V2000, Database.FileFormat.V2010 }) {
			for (final boolean empty : new boolean[] { false, true }) {
				final Path source = directory.resolve("seed-" + empty + format.getFileExtension());
				final Path target = directory.resolve("rebuilt-" + empty + format.getFileExtension());
				try (var file = DatabaseBuilder.create(format, source.toFile())) {
					final var table = new TableBuilder("ITEMS")
							.addColumn(new ColumnBuilder("ID", DataType.LONG).withAutoNumber(true)).toTable(file);
					table.addRow((Object) null);
					table.addRow((Object) null);
					final var rows = table.iterator();
					final var first = rows.next();
					final var last = rows.next();
					table.deleteRow(last);
					if (empty)
						table.deleteRow(first);
				}
				final byte[] before = java.nio.file.Files.readAllBytes(source);
				final var error = assertThrows(MdbSchemaMigration.VerificationException.class,
						() -> MdbSchemaMigration.rebuild(source, target, schema -> {
						}));
				assertTrue(error.issues().stream().anyMatch(issue -> issue.contains("AutoNumber state differs")));
				assertTrue(java.nio.file.Files.exists(target));
				assertArrayEquals(before, java.nio.file.Files.readAllBytes(source));
			}
		}
	}
}

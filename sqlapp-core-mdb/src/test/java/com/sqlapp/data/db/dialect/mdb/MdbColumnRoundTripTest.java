/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import io.github.spannm.jackcess.*;

class MdbColumnRoundTripTest {
	@TempDir
	Path directory;

	@Test
	void calculatedValuesAreRebuiltFromTheirExpression() throws Exception {
		final Path source = directory.resolve("calculated.accdb");
		try (var file = DatabaseBuilder.create(Database.FileFormat.V2010, source.toFile())) {
			new TableBuilder("CALC").addColumn(new ColumnBuilder("PRICE", DataType.LONG))
					.addColumn(new ColumnBuilder("QTY", DataType.LONG))
					.addColumn(new ColumnBuilder("TOTAL", DataType.LONG).withCalculatedInfo("[PRICE] * [QTY]"))
					.toTable(file).addRow(7, 3, null);
		}
		final Path target = directory.resolve("calculated-target.accdb");
		MdbJackcessSupport.rebuild(source, target, MdbFileLoader.load(source));
		try (var file = new DatabaseBuilder().withPath(target).withReadOnly(true).open()) {
			assertTrue(file.getTable("CALC").getColumn("TOTAL").isCalculated());
			assertEquals(21, file.getTable("CALC").iterator().next().get("TOTAL"));
		}
	}

	@ParameterizedTest
	@EnumSource(value = Database.FileFormat.class, names = { "V2000", "V2010", "V2019" })
	void nativeTypesValuesAndPropertiesSurviveRebuild(final Database.FileFormat format) throws Exception {
		final Path source = directory.resolve("source" + format.getFileExtension());
		final LocalDateTime precise = LocalDateTime.of(2026, 10, 29, 12, 34, 56, 123456700);
		try (Database database = DatabaseBuilder.create(format, source.toFile())) {
			database.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			final TableBuilder builder = new TableBuilder("DETAIL").addColumn(new ColumnBuilder("ID", DataType.LONG))
					.addColumn(new ColumnBuilder("TXT", DataType.TEXT).withLengthInUnits(40)
							.withProperty(PropertyMap.ALLOW_ZERO_LEN_PROP, false)
							.withProperty(PropertyMap.REQUIRED_PROP, true)
							.withProperty(PropertyMap.DESCRIPTION_PROP, "表示名")
							.withProperty(PropertyMap.DEFAULT_VALUE_PROP, "'fallback'")
							.withProperty(PropertyMap.VALIDATION_RULE_PROP, "Len([TXT]) <= 40"))
					.addColumn(new ColumnBuilder("CASH", DataType.MONEY))
					.addColumn(new ColumnBuilder("DEC", DataType.NUMERIC).withPrecision(23).withScale(7))
					.addColumn(new ColumnBuilder("TOKEN", DataType.GUID))
					.addColumn(new ColumnBuilder("BYTES", DataType.BINARY).withLength(8))
					.addIndex(new IndexBuilder("PK_DETAIL").withColumns("ID").withPrimaryKey());
			final var row = new ArrayList<Object>(java.util.Arrays.asList(1, "日本語", new BigDecimal("1234.5678"),
					new BigDecimal("1234567890123456.1234567"), "{12345678-1234-1234-1234-1234567890AB}",
					new byte[] { 1, 2, 3 }));
			if (format == Database.FileFormat.V2019) {
				builder.addColumn(new ColumnBuilder("BIG", DataType.BIG_INT));
				builder.addColumn(new ColumnBuilder("PRECISE", DataType.EXT_DATE_TIME));
				row.add(Long.MAX_VALUE - 1);
				row.add(precise);
			}
			final var table = builder.toTable(database);
			table.getProperties().put(PropertyMap.DESCRIPTION_PROP, "明細");
			table.getProperties().put(PropertyMap.VALIDATION_RULE_PROP, "[ID] > 0");
			table.getProperties().save();
			table.addRow(row.toArray());
		}
		final var schema = MdbFileLoader.load(source);
		assertTrue(schema.getTables().get("DETAIL").getColumns().get("ID").isNotNull());
		assertEquals("MONEY", schema.getTables().get("DETAIL").getColumns().get("CASH").getSpecifics()
				.get(MdbFileLoader.SOURCE_TYPE));
		final Path target = directory.resolve("target" + format.getFileExtension());
		MdbJackcessSupport.rebuild(source, target, schema);
		try (Database left = new DatabaseBuilder().withPath(source).withReadOnly(true).open();
				Database right = new DatabaseBuilder().withPath(target).withReadOnly(true).open()) {
			left.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			right.setDateTimeType(DateTimeType.LOCAL_DATE_TIME);
			final var original = left.getTable("DETAIL");
			final var rebuilt = right.getTable("DETAIL");
			for (final var column : original.getColumns()) {
				final var actual = rebuilt.getColumn(column.getName());
				assertEquals(column.getType(), actual.getType(), column.getName());
				assertEquals(column.getLength(), actual.getLength(), column.getName());
			}
			assertEquals("明細", rebuilt.getProperties().getValue(PropertyMap.DESCRIPTION_PROP));
			assertEquals("[ID] > 0", rebuilt.getProperties().getValue(PropertyMap.VALIDATION_RULE_PROP));
			final var text = rebuilt.getColumn("TXT").getProperties();
			assertEquals(false, text.getValue(PropertyMap.ALLOW_ZERO_LEN_PROP));
			assertEquals("表示名", text.getValue(PropertyMap.DESCRIPTION_PROP));
			assertEquals("Len([TXT]) <= 40", text.getValue(PropertyMap.VALIDATION_RULE_PROP));
			final var a = original.iterator().next();
			final var b = rebuilt.iterator().next();
			for (final String name : a.keySet()) {
				if (a.get(name) instanceof byte[] bytes)
					assertArrayEquals(bytes, (byte[]) b.get(name));
				else
					assertEquals(a.get(name), b.get(name), name);
			}
		}
		if (format != Database.FileFormat.V2019) {
			try (var connection = DriverManager.getConnection("jdbc:ucanaccess://" + target.toAbsolutePath())) {
				final var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setObjectName("DETAIL");
				final var metadata = reader.getAllFull(connection).getFirst();
				assertTrue(metadata.getColumns().get("ID").isNotNull());
				final var text = metadata.getColumns().get("TXT");
				assertEquals("表示名", text.getRemarks());
				assertEquals("Len([TXT]) <= 40", text.getCheck());
				assertEquals("false", text.getSpecifics().get(MdbFileLoader.ALLOW_ZERO_LENGTH));
				assertEquals("'fallback'", text.getDefaultValue());
				assertTrue(text.isNotNull());
			}
		}
	}
}

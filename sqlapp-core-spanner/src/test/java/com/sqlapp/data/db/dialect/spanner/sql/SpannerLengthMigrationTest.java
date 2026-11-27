/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerLengthMigrationTest {
	private Table table(DataType type) {
		final var table = new Table("Events").setSchemaName("Sales");
		table.getColumns().add(new Column("value").setDataType(type).setLength(20).setNotNull(true));
		return table;
	}

	@Test
	void expandsAndShrinksStringAndBytesThroughXmlPreservingNullability() throws Exception {
		for (final var type : java.util.List.of(DataType.VARCHAR, DataType.VARBINARY)) {
			final var original = table(type);
			final var target = original.clone();
			target.getColumns().get(0).setLength(100);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			registry.getOptions().setQuateColumnName(true);
			final String nativeType = type == DataType.VARCHAR ? "STRING" : "BYTES";
			final var expanded = registry.createSql(original.diff(restored), SqlType.ALTER);
			assertEquals(1, expanded.size());
			assertEquals("ALTER TABLE `Sales`.`Events` ALTER COLUMN `value` " + nativeType + "(100) NOT NULL",
					expanded.get(0).getSqlText());
			assertTrue(registry.createSql(restored.diff(original), SqlType.ALTER).get(0).getSqlText()
					.endsWith(nativeType + "(20) NOT NULL"));
			assertEquals(100L, restored.getColumns().get(0).getLength());
		}
	}

	@Test
	void handlesNativeMaxInBothDirectionsUsingTheSharedTypeModel() {
		for (final var type : java.util.List.of(DataType.VARCHAR, DataType.VARBINARY)) {
			final var original = table(type);
			final var target = original.clone();
			final String nativeType = type == DataType.VARCHAR ? "STRING" : "BYTES";
			DialectHolder.defaultDialect.setDbType(nativeType + "(MAX)", null, null, target.getColumns().get(0));
			final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			final String maxSql = registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText();
			assertTrue(maxSql.endsWith(nativeType + "(MAX) NOT NULL"), maxSql);
			assertTrue(registry.createSql(target.diff(original), SqlType.ALTER).get(0).getSqlText()
					.endsWith(nativeType + "(20) NOT NULL"));
			final String create = registry.createSql(target, SqlType.CREATE).get(0).getSqlText();
			assertTrue(create.contains(nativeType + "(MAX)"), create);
			assertFalse(create.contains("(MAX)("), create);
			final var array = target.getColumns().get(0).clone().setArrayDimension(1).setNotNull(false);
			assertEquals("ARRAY<" + nativeType + "(MAX)>",
					DialectHolder.defaultDialect.createSqlBuilder().definition(array, false).toString());
		}
	}

	@Test
	void combinesLengthNullabilityAndDefaultsWithoutDroppingTargetSemantics() {
		final var original = table(DataType.VARCHAR);
		original.getColumns().get(0).setDefaultValue("'old'");
		final var target = original.clone();
		target.getColumns().get(0).setLength(50).setNotNull(false).setDefaultValue("'new'");
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertTrue(operations.get(0).getSqlText().endsWith("STRING(50) DEFAULT ('new')"));
		target.getColumns().get(0).setDefaultValue(null);
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().endsWith("STRING(50)"));
		final var retained = original.clone();
		retained.getColumns().get(0).setLength(30);
		assertTrue(registry.createSql(original.diff(retained), SqlType.ALTER).get(0).getSqlText()
				.endsWith("STRING(30) NOT NULL DEFAULT ('old')"));
	}

	@Test
	void rejectsMissingInvalidOrOutOfRangeLengthsWithoutFallingBackToDefaultSize() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final var type : java.util.List.of(DataType.VARCHAR, DataType.VARBINARY)) {
			for (final Long length : new Long[] { null, 0L, -1L, Long.MAX_VALUE }) {
				final var original = table(type);
				final var target = original.clone();
				target.getColumns().get(0).setLength(length);
				assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(original.diff(target), SqlType.ALTER));
			}
		}
	}

	@Test
	void rejectsArraysKeysAndMixedTypeChanges() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var array = table(DataType.VARCHAR);
		array.getColumns().get(0).setArrayDimension(1);
		final var resizedArray = array.clone();
		resizedArray.getColumns().get(0).setLength(40);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(array.diff(resizedArray), SqlType.ALTER));
		final var key = table(DataType.VARCHAR);
		key.getConstraints().addPrimaryKeyConstraint("pk", key.getColumns().get(0));
		final var resizedKey = key.clone();
		resizedKey.getColumns().get(0).setLength(40);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(key.diff(resizedKey), SqlType.ALTER));
		final var original = table(DataType.VARCHAR);
		final var converted = original.clone();
		converted.getColumns().get(0).setDataType(DataType.VARBINARY).setLength(40);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(converted), SqlType.ALTER));
	}
}

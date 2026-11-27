/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerNullabilityMigrationTest {
	private Table table() {
		final var table = new Table("Events").setSchemaName("Sales");
		table.getColumns().add(new Column("value").setDataType(DataType.BIGINT).setDefaultValue("0"));
		return table;
	}

	@Test
	void changesNullabilityThroughXmlWithoutDroppingDefaultOrMutatingTheModel() throws Exception {
		final var original = table();
		final var target = original.clone();
		target.getColumns().get(0).setNotNull(true);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setQuateColumnName(true);
		final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertEquals("ALTER TABLE `Sales`.`Events` ALTER COLUMN `value` INT64 NOT NULL DEFAULT (0)",
				operations.get(0).getSqlText());
		assertEquals("ALTER TABLE `Sales`.`Events` ALTER COLUMN `value` INT64 DEFAULT (0)",
				registry.createSql(restored.diff(original), SqlType.ALTER).get(0).getSqlText());
		assertEquals("0", restored.getColumns().get(0).getDefaultValue());
		assertTrue(restored.getColumns().get(0).isNotNull());
	}

	@Test
	void combinesNullabilityWithSettingOrRemovingDefaultInOneStatement() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		final var target = original.clone();
		target.getColumns().get(0).setNotNull(true).setDefaultValue("10 + 1");
		assertTrue(registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText()
				.endsWith("INT64 NOT NULL DEFAULT (10 + 1)"));
		target.getColumns().get(0).setDefaultValue(null);
		final var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(1, operations.size());
		assertTrue(operations.get(0).getSqlText().endsWith("INT64 NOT NULL"));
		final var nullable = target.clone();
		nullable.getColumns().get(0).setNotNull(false);
		assertTrue(registry.createSql(target.diff(nullable), SqlType.ALTER).get(0).getSqlText().endsWith("INT64"));
	}

	@Test
	void usesNativeTypeRendererWithoutEmittingUnchangedColumnOptions() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		original.getColumns().get(0).setDataType(DataType.TIMESTAMP).setDefaultValue("CURRENT_TIMESTAMP()");
		original.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.LOCALITY_GROUP, "hot");
		final var target = original.clone();
		target.getColumns().get(0).setNotNull(true);
		final String sql = registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText();
		assertTrue(sql.endsWith("TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP())"), sql);
		assertFalse(sql.contains("OPTIONS"), sql);
		assertEquals("hot", target.getColumns().get(0).getSpecifics().get(SpannerSqlBuilder.LOCALITY_GROUP));
		final var commitTimestamp = original.clone();
		commitTimestamp.getColumns().get(0).setDefaultValue(null);
		commitTimestamp.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		final var requiredTimestamp = commitTimestamp.clone();
		requiredTimestamp.getColumns().get(0).setNotNull(true);
		final String commitSql = registry.createSql(commitTimestamp.diff(requiredTimestamp), SqlType.ALTER).get(0)
				.getSqlText();
		assertTrue(commitSql.endsWith("TIMESTAMP NOT NULL"), commitSql);
		assertFalse(commitSql.contains("OPTIONS"), commitSql);
		final var alteredOption = target.clone();
		alteredOption.getColumns().get(0).getSpecifics().put(SpannerSqlBuilder.LOCALITY_GROUP, "cold");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(alteredOption), SqlType.ALTER));
	}

	@Test
	void rejectsArrayTighteningButSupportsRelaxation() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		original.getColumns().get(0).setArrayDimension(1).setDefaultValue(null);
		final var target = original.clone();
		target.getColumns().get(0).setNotNull(true);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
		assertTrue(
				registry.createSql(target.diff(original), SqlType.ALTER).get(0).getSqlText().endsWith("ARRAY<INT64>"));
	}

	@Test
	void rejectsKeyGeneratedIdentityOnUpdateAndMixedTypeChanges() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (int mode = 0; mode < 4; mode++) {
			final var original = table();
			switch (mode) {
			case 0 -> original.getConstraints().addPrimaryKeyConstraint("pk", original.getColumns().get(0));
			case 1 -> original.getColumns().get(0).setIdentity(true);
			case 2 -> original.getColumns().get(0).setFormula("1 + 2");
			case 3 -> original.getColumns().get(0).setOnUpdate("PENDING_COMMIT_TIMESTAMP()");
			}
			final var target = original.clone();
			target.getColumns().get(0).setNotNull(!original.getColumns().get(0).isNotNull());
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		final var original = table();
		final var target = original.clone();
		target.getColumns().get(0).setNotNull(true).setDataType(DataType.DOUBLE);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}
}

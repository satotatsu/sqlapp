/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixAlterViewTest {
	private View view() {
		final var view = new View("extended");
		view.setSchemaName("Sales");
		view.setStatement("SELECT * FROM base WHERE id = 100");
		view.getColumns().add(new Column("existing").setDataType(DataType.BIGINT));
		return view;
	}

	@Test
	void addsAndDropsFamilyColumnsThroughXmlOnBothVersions() throws Exception {
		for (final var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			registry.getOptions().setQuateColumnName(true);
			registry.getOptions().setCreateIfNotExists(true);
			registry.getOptions().setDropIfExists(true);
			final var original = view();
			final var target = (View) original.clone();
			final var column = new Column("Extra Info").setDataType(DataType.VARCHAR).setArrayDimension(1)
					.setArrayDimensionUpperBound(5);
			column.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "details");
			target.getColumns().add(column);
			final View restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
			final var additions = registry.createSql(original.diff(restored), SqlType.ALTER);
			assertEquals(1, additions.size());
			assertEquals(
					"ALTER VIEW \"Sales\".\"extended\" ADD IF NOT EXISTS \"details\".\"Extra Info\" VARCHAR ARRAY[5]",
					additions.get(0).getSqlText());
			final var drops = registry.createSql(restored.diff(original), SqlType.ALTER);
			assertEquals(1, drops.size());
			assertEquals("ALTER VIEW \"Sales\".\"extended\" DROP COLUMN IF EXISTS \"details\".\"Extra Info\"",
					drops.get(0).getSqlText());
			registry.getOptions().setCreateIfNotExists(false);
			registry.getOptions().setDropIfExists(false);
			assertFalse(registry.createSql(original.diff(restored), SqlType.ALTER).get(0).getSqlText()
					.contains("IF NOT EXISTS"));
			assertFalse(registry.createSql(restored.diff(original), SqlType.ALTER).get(0).getSqlText()
					.contains("IF EXISTS"));
			assertTrue(registry.createSql(original.diff(original), SqlType.ALTER).isEmpty());
		}
	}

	@Test
	void altersMetadataViewsWithoutTryingToRecreateTheirDefinition() {
		final var original = view();
		original.getSpecifics().put("phoenix.viewMetadataOnly", true);
		final var target = original.clone();
		target.getColumns().add(new Column("extra").setDataType(DataType.INT));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(
				registry.createSql(original.diff(target), SqlType.ALTER).get(0).getSqlText().startsWith("ALTER VIEW"));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original, SqlType.CREATE));
	}

	@Test
	void rejectsViewSourcePropertiesConstraintsRenameAndExistingColumnChanges() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = view();
		final var renamed = original.clone().setName("renamed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(renamed), SqlType.ALTER));
		final var query = original.clone();
		query.setStatement("SELECT * FROM other_base");
		query.getColumns().add(new Column("extra").setDataType(DataType.INT));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(query), SqlType.ALTER));
		final var property = original.clone();
		property.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, true);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(property), SqlType.ALTER));
		final var key = original.clone();
		key.getConstraints().addPrimaryKeyConstraint("pk", key.getColumns().get(0));
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(key), SqlType.ALTER));
		final var changed = original.clone();
		changed.getColumns().get(0).setDataType(DataType.DOUBLE);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(changed), SqlType.ALTER));
		final var columnRename = original.clone();
		columnRename.getColumns().get(0).setName("renamed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(columnRename), SqlType.ALTER));
	}

	@Test
	void sharesCreateValidationAndRejectsDroppingPrimaryKeyColumns() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = view();
		for (final var column : java.util.List.of(new Column("extra").setDataType(DataType.INT).setNotNull(true),
				new Column("extra").setDataType(DataType.INT).setDefaultValue("1"),
				new Column("extra").setDataType(DataType.INT).setIdentity(true),
				new Column("extra").setDataType(DataType.INT).setFormula("id + 1"))) {
			final var target = original.clone();
			target.getColumns().add(column);
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		original.getConstraints().addPrimaryKeyConstraint("pk", original.getColumns().get(0));
		final var target = original.clone();
		target.getColumns().remove("existing");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}
}

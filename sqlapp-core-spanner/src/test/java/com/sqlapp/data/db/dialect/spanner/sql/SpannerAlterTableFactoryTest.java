/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class SpannerAlterTableFactoryTest {
	private Table table() {
		final var table = new Table("events").setSchemaName("Sales");
		final var id = new Column("id").setDataType(DataType.BIGINT).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("value").setDataType(DataType.BIGINT));
		table.getConstraints().addPrimaryKeyConstraint("pk", id);
		return table;
	}

	@Test
	void addsAndDropsColumnsThroughXmlWithNativeSyntax() throws Exception {
		final var original = table();
		final var target = original.clone();
		target.getColumns()
				.add(new Column("Order Count").setDataType(DataType.BIGINT).setNotNull(true).setDefaultValue("0"));
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setQuateColumnName(true);
		registry.getOptions().setCreateIfNotExists(true);
		registry.getOptions().setDropIfExists(true);
		final var add = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, add.size());
		assertEquals("ALTER TABLE `Sales`.`events` ADD COLUMN IF NOT EXISTS `Order Count` INT64 NOT NULL DEFAULT (0)",
				add.get(0).getSqlText());
		assertEquals("0", restored.getColumns().get("Order Count").getDefaultValue());
		final var drop = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, drop.size());
		assertEquals("ALTER TABLE `Sales`.`events` DROP COLUMN `Order Count`", drop.get(0).getSqlText());
		registry.getOptions().setCreateIfNotExists(false);
		assertFalse(registry.createSql(original.diff(restored), SqlType.ALTER).get(0).getSqlText()
				.contains("IF NOT EXISTS"));
	}

	@Test
	void setsChangesAndDropsDefaultWithoutRestatingColumnType() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(false);
		registry.getOptions().setQuateColumnName(true);
		final var original = table();
		final var first = original.clone();
		first.getColumns().get("value").setDefaultValue("1 + 2");
		assertEquals("ALTER TABLE `events` ALTER COLUMN `value` SET DEFAULT (1 + 2)",
				registry.createSql(original.diff(first), SqlType.ALTER).get(0).getSqlText());
		final var second = first.clone();
		second.getColumns().get("value").setDefaultValue("(4)");
		assertEquals("ALTER TABLE `events` ALTER COLUMN `value` SET DEFAULT ((4))",
				registry.createSql(first.diff(second), SqlType.ALTER).get(0).getSqlText());
		assertEquals("ALTER TABLE `events` ALTER COLUMN `value` DROP DEFAULT",
				registry.createSql(first.diff(original), SqlType.ALTER).get(0).getSqlText());
		assertTrue(registry.createSql(original.diff(original), SqlType.ALTER).isEmpty());
	}

	@Test
	void rejectsUnsupportedColumnAndTableChangesInsteadOfLosingSemantics() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		final var type = original.clone();
		type.getColumns().get("value").setDataType(DataType.DOUBLE).setDefaultValue("1");
		assertThrows(UnsupportedOperationException.class, () -> registry.createSql(original.diff(type), SqlType.ALTER));
		final var rename = original.clone();
		rename.getColumns().get("value").setName("renamed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(rename), SqlType.ALTER));
		final var options = original.clone();
		options.getSpecifics().put(SpannerCreateTableFactory.LOCALITY_GROUP, "ssd");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(options), SqlType.ALTER));
		final var formula = original.clone();
		formula.getColumns().get("value").setFormula("id + 1");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(formula), SqlType.ALTER));
		final var droppedKey = original.clone();
		droppedKey.getColumns().remove("id");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(droppedKey), SqlType.ALTER));
	}

	@Test
	void rejectsUnsafeNotNullAndOnUpdateDefaultChanges() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		for (final var column : java.util.List.of(new Column("required").setDataType(DataType.BIGINT).setNotNull(true),
				new Column("required").setDataType(DataType.BIGINT).setArrayDimension(1).setNotNull(true)
						.setDefaultValue("[]"))) {
			final var target = original.clone();
			target.getColumns().add(column);
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
		}
		original.getColumns().get("value").setDefaultValue("1").setOnUpdate("1");
		final var removed = original.clone();
		removed.getColumns().get("value").setDefaultValue(null);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(removed), SqlType.ALTER));
	}
}

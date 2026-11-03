/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class SpannerCheckConstraintTest {
	@Test
	void dropsChangedCheckBeforeColumnsAndAddsItAfterNewColumns() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		original.getColumns().add(new Column("amount").setDataType(DataType.BIGINT));
		original.getConstraints().add(new CheckConstraint("positive", "quantity > 0"));
		final var target = original.clone();
		target.getColumns().remove("quantity");
		((CheckConstraint) target.getConstraints().get(0)).setExpression("amount > 0");
		final var operations = registry.createSql(original.diff(target), SqlType.ALTER);
		assertEquals(3, operations.size());
		assertTrue(operations.get(0).getSqlText().contains("DROP CONSTRAINT"));
		assertTrue(operations.get(1).getSqlText().contains("DROP COLUMN"));
		assertTrue(operations.get(2).getSqlText().contains("CHECK (amount > 0 )"));
		final var extended = target.clone();
		extended.getColumns().add(new Column("total").setDataType(DataType.BIGINT));
		((CheckConstraint) extended.getConstraints().get(0)).setExpression("total > 0");
		final var additions = registry.createSql(target.diff(extended), SqlType.ALTER);
		assertEquals(3, additions.size());
		assertTrue(additions.get(0).getSqlText().contains("DROP CONSTRAINT"));
		assertTrue(additions.get(1).getSqlText().contains("ADD COLUMN"));
		assertTrue(additions.get(2).getSqlText().contains("CHECK (total > 0 )"));
	}

	private Table table() {
		final var table = new Table("Orders").setSchemaName("Sales");
		table.getColumns().add(new Column("quantity").setDataType(DataType.BIGINT));
		return table;
	}

	@Test
	void addsDropsAndChangesCheckThroughXmlAndTableDiff() throws Exception {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		registry.getOptions().setDropIfExists(true);
		final var original = table();
		final var target = original.clone();
		target.getConstraints().add(new CheckConstraint("positive quantity", "quantity > 0"));
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var add = registry.createSql(original.diff(restored), SqlType.ALTER);
		assertEquals(1, add.size());
		assertEquals("ALTER TABLE `Sales`.`Orders` ADD CONSTRAINT `positive quantity` CHECK (quantity > 0 )",
				add.get(0).getSqlText());
		final var drop = registry.createSql(restored.diff(original), SqlType.ALTER);
		assertEquals(1, drop.size());
		assertEquals("ALTER TABLE `Sales`.`Orders` DROP CONSTRAINT `positive quantity`", drop.get(0).getSqlText());
		final var changed = restored.clone();
		((CheckConstraint) changed.getConstraints().get(0)).setExpression("quantity >= 0");
		final var modify = registry.createSql(restored.diff(changed), SqlType.ALTER);
		assertEquals(2, modify.size());
		assertEquals(drop.get(0).getSqlText(), modify.get(0).getSqlText());
		assertTrue(modify.get(1).getSqlText().contains("CHECK (quantity >= 0 )"));
	}

	@Test
	void sharesValidationAndRenderingWithCreateTableAndStandaloneCreate() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var table = table();
		final var check = new CheckConstraint("positive", "quantity > 0").setDeferrability(Deferrability.NotDeferrable);
		table.getConstraints().add(check);
		assertTrue(registry.createSql(table, SqlType.CREATE).get(0).getSqlText().contains("CHECK (quantity > 0 )"));
		assertTrue(registry.createSql(check, SqlType.CREATE).get(0).getSqlText().contains("ADD CONSTRAINT"));
		for (final var invalid : java.util.List.of(check.clone().setEnable(false),
				check.clone().setDeferrability(Deferrability.InitiallyDeferred),
				check.clone().setDeferrability(Deferrability.InitiallyImmediate))) {
			table.getConstraints().clear();
			table.getConstraints().add(invalid);
			assertThrows(UnsupportedOperationException.class, () -> registry.createSql(table, SqlType.CREATE));
			assertThrows(UnsupportedOperationException.class, () -> registry.createSql(invalid, SqlType.CREATE));
			assertThrows(UnsupportedOperationException.class,
					() -> registry.createSql(table().diff(table), SqlType.ALTER));
		}
	}

	@Test
	void rejectsMissingNamesExpressionsAndNonCheckAlterations() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final var check : java.util.List.of(new CheckConstraint("", "quantity > 0"),
				new CheckConstraint("c", " "))) {
			final var target = table();
			target.getConstraints().add(check);
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(table().diff(target), SqlType.ALTER));
		}
		final var target = table();
		target.getConstraints().addPrimaryKeyConstraint("pk", target.getColumns().get(0));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(table().diff(target), SqlType.ALTER));
	}
}

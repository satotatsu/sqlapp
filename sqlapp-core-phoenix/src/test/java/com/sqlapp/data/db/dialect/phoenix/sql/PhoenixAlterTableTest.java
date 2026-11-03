/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixAlterTableTest {
	@Test
	void altersImmutableRowsExplicitlyOnBothVersionsThroughXml() throws Exception {
		for (final var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			final var original = table().setSchemaName("Sales");
			final var target = original.clone();
			target.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, "true");
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
			final var operations = registry.createSql(original.diff(restored), SqlType.ALTER);
			assertEquals(1, operations.size());
			assertEquals("ALTER TABLE \"Sales\".\"events\" SET IMMUTABLE_ROWS=TRUE", operations.get(0).getSqlText());
			final var mutable = target.clone();
			mutable.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, "false");
			assertEquals("ALTER TABLE \"Sales\".\"events\" SET IMMUTABLE_ROWS=FALSE",
					registry.createSql(target.diff(mutable), SqlType.ALTER).get(0).getSqlText());
			final var same = target.clone();
			same.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, "TRUE");
			assertTrue(registry.createSql(target.diff(same), SqlType.ALTER).isEmpty());
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(target.diff(original), SqlType.ALTER));
			mutable.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, "invalid");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(target.diff(mutable), SqlType.ALTER));
		}
	}

	@Test
	void combinesColumnAndImmutableChangesButRejectsOtherProperties() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = table();
		final var target = original.clone();
		target.getColumns().add(new Column("payload").setDataType(DataType.BIGINT));
		target.getSpecifics().put(PhoenixDdlOptions.IMMUTABLE_ROWS, "false");
		assertEquals(2, registry.createSql(original.diff(target), SqlType.ALTER).size());
		target.getSpecifics().put(PhoenixDdlOptions.MULTI_TENANT, "true");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
	}

	private Table table() {
		final var table = new Table("events");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		return table;
	}

	@Test
	void addsAndDropsQualifiedColumnsUsingNativeSyntax() throws Exception {
		final var original = table();
		final var target = original.clone();
		final var column = new Column("payload").setDataType(DataType.VARCHAR).setArrayDimension(1);
		column.getSpecifics().put(PhoenixCreateTableFactory.COLUMN_FAMILY, "details");
		target.getColumns().add(column);
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setQuateColumnName(true);
		registry.getOptions().setCreateIfNotExists(true);
		registry.getOptions().setDropIfExists(true);
		final String add = registry.createSql(original.diff(restored), SqlType.ALTER).get(0).getSqlText();
		assertTrue(add.contains("ADD IF NOT EXISTS \"details\".\"payload\" VARCHAR ARRAY"), add);
		assertFalse(add.contains("ADD COLUMN"), add);
		final String drop = registry.createSql(restored.diff(original), SqlType.ALTER).get(0).getSqlText();
		assertTrue(drop.contains("DROP COLUMN IF EXISTS \"details\".\"payload\""), drop);
	}

	@Test
	void rejectsUnsupportedColumnAndTableChanges() {
		final var original = table();
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var target = original.clone();
		target.getColumns().get(0).setDataType(DataType.BIGINT);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(target), SqlType.ALTER));
		final var renamed = original.clone().setName("renamed");
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(renamed), SqlType.ALTER));
		final var constrained = original.clone();
		final var pk = new UniqueConstraint("pk").setPrimaryKey(true);
		pk.getColumns().add("id");
		constrained.getConstraints().add(pk);
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(constrained), SqlType.ALTER));
	}
}

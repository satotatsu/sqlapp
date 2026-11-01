/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class PhoenixAlterTableTest {
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

/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

class SpannerCreateSchemaFactoryTest {
	@Test
	void dropsOnlyNamedUserSchemasAndHonorsIfExists() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var schema = new Schema("Sales");
		schema.getTables().add(new Table("Orders"));
		registry.getOptions().setDropIfExists(false);
		final var statements = registry.createSql(schema, SqlType.DROP);
		assertEquals(1, statements.size());
		assertEquals("DROP SCHEMA `Sales`", statements.getFirst().getSqlText());
		registry.getOptions().setDropIfExists(true);
		assertEquals("DROP SCHEMA IF EXISTS `Sales`",
				registry.createSql(schema, SqlType.DROP).getFirst().getSqlText());
		for (String invalid : new String[] {null, "", " ", "information_schema", "SPANNER_SYS"})
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(new Schema(invalid), SqlType.DROP));
	}

	@Test
	void createsNamedNamespaceBeforeTablesAndSkipsDefaultNamespace() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (String name : new String[] {null, "", "Sales"}) {
			final var schema = new Schema(name);
			final var table = new Table("Orders");
			table.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
			schema.getTables().add(table);
			final var statements = registry.createSql(schema, SqlType.CREATE);
			if ("Sales".equals(name)) {
				assertEquals(2, statements.size());
				assertEquals("CREATE SCHEMA `Sales`", statements.getFirst().getSqlText());
				assertTrue(statements.getLast().getSqlText().contains("`Sales`.`Orders`"));
			} else {
				assertEquals(1, statements.size());
				assertTrue(statements.getFirst().getSqlText().startsWith("CREATE TABLE IF NOT EXISTS `Orders`"));
			}
		}
	}
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

class PostgresSchemaRecreationTest extends AbstractPostgresSqlFactoryTest {
	@Test
	void retainsArrayDimensionsAndLeavesConstraintNamesUnqualified() {
		Table table = new Table("items").setSchemaName("schemaA");
		table.getColumns().add("tags", c -> c.setDataType(DataType.VARCHAR).setLength(20).setArrayDimension(1));
		table.getColumns().add("matrix", c -> c.setDataType(DataType.INT).setArrayDimension(2));
		table.getColumns().add("scalar", c -> c.setDataType(DataType.INT));
		table.getConstraints().add(new CheckConstraint("items_check", "scalar > 0"));
		sqlFactoryRegistry.getOptions().setDecorateSchemaName(true);
		SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, SqlType.CREATE);
		String sql = factory.createSql(table).get(0).getSqlText();
		assertTrue(sql.contains("VARCHAR(20)[]"), sql);
		assertTrue(sql.contains("INT[][]"), sql);
		assertTrue(sql.contains("scalar INT"), sql);
		String check = sql.substring(sql.indexOf("CONSTRAINT"));
		assertFalse(check.contains("schemaA"), sql);
		assertTrue(check.contains("CHECK"), sql);
		assertEquals(1, table.getColumns().get("tags").getArrayDimension());
	}
	@Test
	void recreatesEnumAndDomainWithSchemaAndEscapedLabels() {
		var registry = sqlFactoryRegistry;
		registry.getOptions().setDecorateSchemaName(true);
		var domain = new com.sqlapp.data.schemas.Domain("positive").setSchemaName("tenant");
		domain.setDataType(DataType.INT).setNullable(false).setDefaultValue("1").setCheck("VALUE > 0");
		SqlFactory<com.sqlapp.data.schemas.Domain> factory = registry.getSqlFactory(domain, SqlType.CREATE);
		String sql = factory.createSql(domain).get(0).getSqlText();
		assertTrue(sql.contains("tenant.positive"), sql);
		assertTrue(sql.contains("CHECK (VALUE > 0)"), sql);
		assertTrue(sql.contains("DEFAULT 1"), sql);
		assertTrue(sql.contains("NOT NULL"), sql);
		var enumeration = new com.sqlapp.data.schemas.Domain("status").setSchemaName("tenant");
		enumeration.setDataType(DataType.ENUM);
		enumeration.getValues().add("it's new");
		String enumSql = factory.createSql(enumeration).get(0).getSqlText();
		assertTrue(enumSql.contains("CREATE TYPE tenant.status AS ENUM"), enumSql);
		assertTrue(enumSql.contains("'it''s new'"), enumSql);
	}
}

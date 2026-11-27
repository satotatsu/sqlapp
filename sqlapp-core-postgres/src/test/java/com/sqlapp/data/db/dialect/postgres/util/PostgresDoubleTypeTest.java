/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class PostgresDoubleTypeTest {
	@ParameterizedTest
	@ValueSource(ints = { 8, 9, 10, 15, 18 })
	void emitsPostgresDoublePrecisionForCanonicalAndForeignNativeNames(int version) {
		var dialect = DialectResolver.getInstance().getDialect("postgres", version, 0, null);
		assertEquals("DOUBLE PRECISION", dialect.getDbDataTypes().getDbType(DataType.DOUBLE).getTypeName());
		for (String nativeName : new String[] { "DOUBLE", "DOUBLE PRECISION", "FLOAT8" }) {
			var column = new Column("price").setDataType(DataType.DOUBLE).setDataTypeName(nativeName);
			assertEquals("DOUBLE PRECISION", new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
			column.setArrayDimension(2);
			assertEquals("DOUBLE PRECISION[][]", new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 8, 9, 10, 15, 18 })
	void retainsExistingTypeResolutionAliasesAndArraySupport(int version) {
		var dialect = DialectResolver.getInstance().getDialect("postgres", version, 0, null);
		for (String name : new String[] { "DOUBLE", "double precision", "float8", "float8[]",
				"double precision[][]" }) {
			var column = new Column("price");
			assertTrue(dialect.setDbType(name, null, null, column), name);
			assertEquals(DataType.DOUBLE, column.getDataType());
			assertEquals(name.endsWith("[][]") ? 2 : name.endsWith("[]") ? 1 : 0, column.getArrayDimension());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 8, 18 })
	void recreatesReportedAccessStyleTableWithDefaultAndPrimaryKey(int version) {
		var dialect = DialectResolver.getInstance().getDialect("postgres", version, 0, null);
		var table = new Table("T_item").setSchemaName("public");
		var item = new Column("item").setDataType(DataType.VARCHAR).setDataTypeName("TEXT").setNotNull(true);
		var price = new Column("price").setDataType(DataType.DOUBLE).setDataTypeName("DOUBLE").setDefaultValue("0");
		table.getColumns().add(item);
		table.getColumns().add(price);
		table.setPrimaryKey("pk_item", item);
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setOutputCommit(false);
		com.sqlapp.data.db.sql.SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
		String ddl = factory.createSql(table).getFirst().getSqlText();
		assertTrue(ddl.contains("DOUBLE PRECISION DEFAULT 0"), ddl);
		assertTrue(ddl.contains("PRIMARY KEY"), ddl);
		assertFalse(ddl.contains("DOUBLE DEFAULT"), ddl);
		assertEquals("DOUBLE", price.getDataTypeName());
		assertEquals(DataType.DOUBLE, price.getDataType());
	}
}

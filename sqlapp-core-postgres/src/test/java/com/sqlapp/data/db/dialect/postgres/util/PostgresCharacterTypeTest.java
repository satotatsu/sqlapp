package com.sqlapp.data.db.dialect.postgres.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class PostgresCharacterTypeTest {
	@Test
	void preservesUnboundedBoundedAndTextTypesIncludingArrays() {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
			for (var type : new DataType[] { DataType.VARCHAR, DataType.LONGVARCHAR }) {
				for (int dimension : new int[] { 0, 1, 2 }) {
					var column = new Column("value").setDataType(type).setArrayDimension(dimension);
					String suffix = "[]".repeat(dimension);
					assertEquals("VARCHAR" + suffix, new PostgresSqlBuilder(dialect).typeDefinition(column).toString());
					assertEquals("VARCHAR(100)" + suffix,
							new PostgresSqlBuilder(dialect).typeDefinition(column.setLength(100)).toString());
					assertEquals("TEXT" + suffix,
							new PostgresSqlBuilder(dialect).typeDefinition(column.setDataTypeName("text")).toString());
				}
			}
		}
	}

	@Test
	void publicCreateFactoryPreservesUnboundedLongVarchar() {
		var dialect = DialectResolver.getInstance().getDialect("postgres", 15, 0, null);
		var table = new Table("strings");
		table.getColumns().add("value", c -> c.setDataType(DataType.LONGVARCHAR));
		com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry()
				.getSqlFactory(table, SqlType.CREATE);
		String sql = factory.createSql(table).get(0).getSqlText().toUpperCase();
		assertTrue(sql.contains("VARCHAR"), sql);
		assertFalse(sql.contains("VARCHAR("), sql);
	}
}

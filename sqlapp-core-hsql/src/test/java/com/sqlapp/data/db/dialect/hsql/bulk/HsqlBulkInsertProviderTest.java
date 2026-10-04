/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.jdbc.bulk.BulkInsertResolver;

class HsqlBulkInsertProviderTest {
	@Test
	void resolvesAndExecutesJdbcBatchInsert() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:hsql_bulk_insert", "SA", "")) {
			assertInstanceOf(HsqlBulkInsertExecutor.class, BulkInsertResolver.resolve(connection));
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE ITEMS (ID INT PRIMARY KEY, NAME VARCHAR(30))");
			}
			final var table = new com.sqlapp.data.schemas.Table("ITEMS");
			table.getColumns().add(new com.sqlapp.data.schemas.Column("ID")
					.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			table.getColumns().add(new com.sqlapp.data.schemas.Column("NAME")
					.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR).setLength(30));
			final var row = table.newRow();
			row.put("ID", 1);
			row.put("NAME", "one");
			table.getRows().add(row);
			assertEquals(1L, BulkInsertResolver.execute(connection, table, null));
			try (var statement = connection.createStatement();
					var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
				rows.next();
				assertEquals(1, rows.getInt(1));
			}
		}
	}
}

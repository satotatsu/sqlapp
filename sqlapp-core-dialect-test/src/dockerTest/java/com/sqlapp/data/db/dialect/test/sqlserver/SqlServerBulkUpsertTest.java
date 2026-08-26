/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

/** Exercises staging-table bulk upsert against SQL Server 2022. */
class SqlServerBulkUpsertTest {
	private static final MSSQLServerContainer SQL_SERVER =
			ReusableTestcontainers.configure(new MSSQLServerContainer(
					"mcr.microsoft.com/mssql/server:2022-latest").acceptLicense());

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(SQL_SERVER);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SQL_SERVER);
	}

	@Test
	void updatesMatchesAndInsertsMissingRowsThroughBulkStaging()
			throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(),
				SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE dbo.SQLAPP_BULK_UPSERT_TEST (
					  ID BIGINT IDENTITY(1,1) NOT NULL UNIQUE,
					  CODE NVARCHAR(20) NOT NULL PRIMARY KEY,
					  NAME NVARCHAR(100) NULL,
					  AMOUNT DECIMAL(12,2) NULL,
					  PAYLOAD VARBINARY(20) NULL
					)
					""");
			statement.execute("""
					INSERT INTO dbo.SQLAPP_BULK_UPSERT_TEST
					  (CODE, NAME, AMOUNT, PAYLOAD)
					VALUES ('A', 'old', 1.00, 0x01)
					""");

			final Table table = createTable();
			table.getRows().add(row -> {
				row.put("CODE", "A");
				row.put("NAME", "更新後");
				row.put("AMOUNT", new BigDecimal("12.34"));
				row.put("PAYLOAD", new byte[] { 0, (byte) 0xff });
			});
			table.getRows().add(row -> {
				row.put("CODE", "B");
				row.put("NAME", null);
				row.put("AMOUNT", null);
			});
			table.getRows().add(row -> {
				row.put("CODE", "C");
				row.put("NAME", "");
				row.put("AMOUNT", new BigDecimal("0.00"));
				row.put("PAYLOAD", new byte[] { 2 });
			});
			table.getRows().add(row -> { row.put("CODE", "D"); row.put("NAME", "first"); });
			table.getRows().add(row -> { row.put("CODE", "D"); row.put("NAME", "discarded"); });

			assertEquals(4, BulkUpsertResolver.execute(connection, table,
					BulkUpsertOption.builder()
							.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.KEEP_FIRST)
							.bulkOption(BulkOption.builder().batchSize(2).build())
							.build()));

			try (var resultSet = statement.executeQuery("SELECT ID, CODE, NAME, "
					+ "AMOUNT, PAYLOAD FROM dbo.SQLAPP_BULK_UPSERT_TEST "
					+ "ORDER BY CODE")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("ID"));
				assertEquals("A", resultSet.getString("CODE"));
				assertEquals("更新後", resultSet.getString("NAME"));
				assertEquals(new BigDecimal("12.34"),
						resultSet.getBigDecimal("AMOUNT"));
				assertArrayEquals(new byte[] { 0, (byte) 0xff },
						resultSet.getBytes("PAYLOAD"));
				resultSet.next();
				assertEquals("B", resultSet.getString("CODE"));
				assertNull(resultSet.getString("NAME"));
				assertNull(resultSet.getBigDecimal("AMOUNT"));
				resultSet.next();
				assertEquals("C", resultSet.getString("CODE"));
				assertEquals("", resultSet.getString("NAME"));
				assertArrayEquals(new byte[] { 2 }, resultSet.getBytes("PAYLOAD"));
				resultSet.next();
				assertEquals("D", resultSet.getString("CODE"));
				assertEquals("first", resultSet.getString("NAME"));
			}
		}
	}

	private static Table createTable() {
		final Table table = new Table("SQLAPP_BULK_UPSERT_TEST");
		table.setSchemaName("dbo");
		final Column id = new Column("ID").setDataType(DataType.BIGINT)
				.setIdentity(true);
		final Column code = new Column("CODE").setDataType(DataType.NVARCHAR)
				.setLength(20).setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(code);
		table.getColumns().add(new Column("NAME").setDataType(DataType.NVARCHAR)
				.setLength(100));
		table.getColumns().add(new Column("AMOUNT").setDataType(DataType.DECIMAL)
				.setLength(12).setScale(2));
		table.getColumns().add(new Column("PAYLOAD")
				.setDataType(DataType.VARBINARY).setLength(20));
		table.setPrimaryKey("PK_SQLAPP_BULK_UPSERT_TEST", code);
		return table;
	}
}

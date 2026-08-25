/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;

/** Exercises Microsoft JDBC SQLServerBulkCopy against SQL Server 2022. */
class SqlServerBulkInsertTest {
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
	void insertsSchemaRowsUsingMicrosoftJdbcBulkCopy() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(),
				SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE dbo.SQLAPP_BULK_TEST (
					  ID BIGINT IDENTITY(1,1) PRIMARY KEY,
					  NAME NVARCHAR(100) NOT NULL,
					  AMOUNT DECIMAL(12,2) NULL
					)
					""");
			final Table table = new Table("SQLAPP_BULK_TEST");
			table.setSchemaName("dbo");
			table.getColumns().add(new Column("ID")
					.setDataType(DataType.BIGINT).setIdentity(true));
			table.getColumns().add(new Column("NAME")
					.setDataType(DataType.NVARCHAR).setLength(100));
			table.getColumns().add(new Column("AMOUNT")
					.setDataType(DataType.DECIMAL).setLength(12).setScale(2));
			table.getRows().add(row -> {
				row.put("NAME", "山田");
				row.put("AMOUNT", new BigDecimal("123.45"));
			});
			table.getRows().add(row -> {
				row.put("NAME", "佐藤");
				row.put("AMOUNT", null);
			});

			assertEquals(2, BulkInsertResolver.execute(connection, table,
					BulkOption.builder().batchSize(1).keepNulls(true)
							.checkConstraints(true).tableLock(true).build()));
			try (var resultSet = statement.executeQuery(
					"SELECT ID, NAME, AMOUNT FROM dbo.SQLAPP_BULK_TEST ORDER BY ID")) {
				resultSet.next();
				assertEquals(1L, resultSet.getLong("ID"));
				assertEquals("山田", resultSet.getString("NAME"));
				assertEquals(new BigDecimal("123.45"),
						resultSet.getBigDecimal("AMOUNT"));
				resultSet.next();
				assertEquals(2L, resultSet.getLong("ID"));
				assertEquals("佐藤", resultSet.getString("NAME"));
				assertEquals(null, resultSet.getBigDecimal("AMOUNT"));
			}
		}
	}

	@Test
	void preservesExplicitIdentityWhenRequested() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(),
				SQL_SERVER.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE dbo.SQLAPP_BULK_IDENTITY_TEST (ID BIGINT IDENTITY(1,1) PRIMARY KEY, NAME NVARCHAR(100))");
			final Table table = new Table("SQLAPP_BULK_IDENTITY_TEST");
			table.setSchemaName("dbo");
			table.getColumns().add(new Column("ID")
					.setDataType(DataType.BIGINT).setIdentity(true));
			table.getColumns().add(new Column("NAME")
					.setDataType(DataType.NVARCHAR).setLength(100));
			table.getRows().add(row -> {
				row.put("ID", 100L);
				row.put("NAME", "explicit");
			});

			assertEquals(1, BulkInsertResolver.execute(connection, table,
					BulkOption.builder().keepIdentity(true).build()));
			try (var resultSet = statement.executeQuery(
					"SELECT ID, NAME FROM dbo.SQLAPP_BULK_IDENTITY_TEST")) {
				resultSet.next();
				assertEquals(100L, resultSet.getLong("ID"));
				assertEquals("explicit", resultSet.getString("NAME"));
			}
		}
	}
}

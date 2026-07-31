/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

/**
 * Isolates the Microsoft JDBC driver's generated-key behavior from sqlapp.
 */
@Testcontainers
class SqlServerBatchGeneratedKeysTest {
	private static final String IMAGE =
			"mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04";

	@Container
	private static final MSSQLServerContainer SQL_SERVER =
			new MSSQLServerContainer(IMAGE).acceptLicense();

	@Test
	void testStandardGetGeneratedKeysAfterExecuteBatchIsUnsupported() throws Exception {
		try (Connection connection = SQL_SERVER.createConnection("")) {
			assertTrue(connection.getMetaData().supportsGetGeneratedKeys(),
					"The driver must advertise generated-key support.");
			try (Statement statement = connection.createStatement()) {
				statement.execute("DROP TABLE IF EXISTS BATCH_GENERATED_KEYS");
				statement.execute("""
						CREATE TABLE BATCH_GENERATED_KEYS (
							ID INT IDENTITY(1, 1) PRIMARY KEY,
							TXT VARCHAR(100) NOT NULL
						)""");
			}

			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO BATCH_GENERATED_KEYS(TXT) VALUES (?)",
					Statement.RETURN_GENERATED_KEYS)) {
				statement.setString(1, "first");
				statement.addBatch();
				statement.setString(1, "second");
				statement.addBatch();

				assertArrayEquals(new int[] { 1, 1 }, statement.executeBatch());

				assertThrows(SQLException.class, statement::getGeneratedKeys,
						"SQL Server advertises generated-key support, but does not expose keys after executeBatch().");
			}
		}
	}

	@Test
	void testOutputIntoTemporaryTableAfterExecuteBatch() throws Exception {
		try (Connection connection = SQL_SERVER.createConnection("")) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("DROP TABLE IF EXISTS BATCH_GENERATED_KEYS");
				statement.execute("""
						CREATE TABLE BATCH_GENERATED_KEYS (
							ID INT IDENTITY(1, 1) PRIMARY KEY,
							TXT VARCHAR(100) NOT NULL
						)""");
				statement.execute("""
						CREATE TABLE #SQLAPP_GENERATED_KEYS (
							ROW_NO BIGINT IDENTITY(1, 1) PRIMARY KEY,
							GENERATED_ID INT NOT NULL
						)""");
			}

			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO BATCH_GENERATED_KEYS(TXT)
					OUTPUT INSERTED.ID INTO #SQLAPP_GENERATED_KEYS(GENERATED_ID)
					VALUES (?)""")) {
				statement.setString(1, "first");
				statement.addBatch();
				statement.setString(1, "second");
				statement.addBatch();
				assertArrayEquals(new int[] { 1, 1 }, statement.executeBatch());
			}

			List<Integer> generatedKeys = new ArrayList<>();
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery("""
							SELECT GENERATED_ID
							FROM #SQLAPP_GENERATED_KEYS
							ORDER BY ROW_NO""")) {
				while (resultSet.next()) {
					generatedKeys.add(resultSet.getInt(1));
				}
			}
			assertEquals(List.of(1, 2), generatedKeys,
					"OUTPUT INTO must capture one generated key per batch entry in execution order.");
		}
	}
}

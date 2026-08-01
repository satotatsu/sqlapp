/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/**
 * Verifies that SQL Server can keep a cursor open while
 * {@link JdbcTreeDataSession} executes inserts on the same connection.
 */
class SqlServerJdbcTreeDataSessionTest {
	private static final String IMAGE =
			"mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04";

	private static final MSSQLServerContainer SQL_SERVER =
			ReusableTestcontainers.configure(new MSSQLServerContainer(IMAGE).acceptLicense());

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(SQL_SERVER);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SQL_SERVER);
	}

	@Test
	void testSelectCursorRemainsUsableDuringHierarchicalInsert()
			throws SQLException {
		try (Connection connection = SQL_SERVER.createConnection("")) {
			createTables(connection);
			Schema schema = SchemaUtils.getSchema(connection, "dbo")
					.orElseThrow(() -> new AssertionError(
							"SQL Server dbo schema was not loaded."));
			Table parent = schema.getTables().get("PARENT_TABLE");
			Table child = schema.getTables().get("CHILD_TABLE");

			try (Statement statement = connection.createStatement(
					ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY)) {
				statement.setFetchSize(1);
				try (ResultSet cursor = statement.executeQuery(
						"SELECT ID, TXT FROM PARENT_TABLE ORDER BY ID")) {
					assertTrue(cursor.next());

					try (JdbcTreeDataSession session =
							new JdbcTreeDataSession(
									connection, parent, child)) {
						session.setRootBatchSize(1);
						session.setTableOperationMode(
								TableOperationMode.INSERT);
						Row parentRow = session.newRow(parent);
						parentRow.put("TXT", "parent-3");
						Row childRow = session.newRow(child);
						childRow.put("TXT", "child-3");
					}

					assertTrue(cursor.next(),
							"The open SELECT cursor must remain usable after INSERT.");
				}
			}

			assertEquals(3, count(connection, "PARENT_TABLE"));
			assertEquals(1, count(connection, "CHILD_TABLE"));
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery(
							"SELECT PARENT_ID FROM CHILD_TABLE")) {
				assertTrue(resultSet.next());
				assertTrue(resultSet.getInt(1) > 0);
			}
		}
	}

	private void createTables(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS CHILD_TABLE");
			statement.execute("DROP TABLE IF EXISTS PARENT_TABLE");
			statement.execute("""
					CREATE TABLE PARENT_TABLE (
					ID INT IDENTITY(1, 1) PRIMARY KEY,
						TXT VARCHAR(256)
					)""");
			statement.execute("""
					CREATE TABLE CHILD_TABLE (
					ID INT IDENTITY(1, 1) PRIMARY KEY,
						PARENT_ID INT NOT NULL,
						TXT VARCHAR(256),
						CONSTRAINT FK_CHILD_PARENT FOREIGN KEY (PARENT_ID)
							REFERENCES PARENT_TABLE(ID)
					)""");
			statement.execute("""
				INSERT INTO PARENT_TABLE(TXT)
				VALUES ('parent-1'), ('parent-2')
					""");
		}
	}

	private int count(Connection connection, String tableName)
			throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(
						"SELECT COUNT(*) FROM " + tableName)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}
}

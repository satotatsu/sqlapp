/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2022;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Table;

/** Verifies the SQL Server 2022 metadata path and Ledger catalog columns. */
class SqlServer2022MetadataReaderTest {
	private static final String DATABASE_NAME = "METADATA_READER_2022_TEST";
	private static final MSSQLServerContainer SQL_SERVER =
			ReusableTestcontainers.configure(new MSSQLServerContainer(
					"mcr.microsoft.com/mssql/server:2022-latest").acceptLicense());

	@BeforeAll
	static void startContainer() throws SQLException {
		ReusableTestcontainers.start(SQL_SERVER);
		try (Connection connection = SQL_SERVER.createConnection("");
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE DATABASE " + DATABASE_NAME);
		}
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(SQL_SERVER);
	}

	@Test
	void testReadsLedgerMetadataOnSqlServer2022() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl() + ";databaseName=" + DATABASE_NAME,
				SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE METADATA_LEDGER_2022 (
						ID BIGINT NOT NULL PRIMARY KEY,
						VALUE NVARCHAR(100) NOT NULL
					) WITH (LEDGER = ON (APPEND_ONLY = ON))
					""");
			statement.execute("CREATE VIEW METADATA_LEDGER_2022_VIEW AS SELECT ID, VALUE FROM METADATA_LEDGER_2022");
			statement.execute("""
					CREATE PROCEDURE METADATA_2022_PROCEDURE @P_ID BIGINT AS
					SELECT VALUE FROM METADATA_LEDGER_2022 WHERE ID = @P_ID
					""");

			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(SqlServer2022.class, dialect);
			var reader = dialect.getCatalogReader();
			reader.setCatalogName(connection.getCatalog());
			Catalog catalog = reader.getAllFull(connection).stream()
					.filter(current -> connectionCatalogEquals(connection, current))
					.findFirst().orElseThrow();
			Table table = catalog.getSchemas().get("dbo").getTables()
					.get("METADATA_LEDGER_2022");
			assertNotNull(table);
			assertEquals("APPEND_ONLY_LEDGER_TABLE",
					table.getSpecifics().get("ledger_type"));
			var schema = catalog.getSchemas().get("dbo");
			var view = schema.getViews().get("METADATA_LEDGER_2022_VIEW");
			assertNotNull(view);
			assertNotNull(view.getColumns().get("VALUE"));
			assertTrue(String.join("\n", view.getStatement())
					.toUpperCase(Locale.ROOT).contains("METADATA_LEDGER_2022"));
			var procedure = schema.getProcedures().get("METADATA_2022_PROCEDURE");
			assertNotNull(procedure);
			assertNotNull(procedure.getArguments().get("@P_ID"));
			assertTrue(String.join("\n", procedure.getStatement())
					.toUpperCase(Locale.ROOT).contains("METADATA_LEDGER_2022"));
		}
	}

	private boolean connectionCatalogEquals(final Connection connection,
			final Catalog catalog) {
		try {
			return connection.getCatalog().equalsIgnoreCase(catalog.getName());
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}
}

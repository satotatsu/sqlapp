/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sqlserver;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlserver.SqlServer2019;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Catalog;

/** Ensures the version-specific metadata path remains usable on SQL Server 2019. */
class SqlServer2019MetadataReaderTest {
	private static final String DATABASE_NAME = "METADATA_READER_2019_TEST";
	private static final MSSQLServerContainer SQL_SERVER =
			ReusableTestcontainers.configure(new MSSQLServerContainer(
					"mcr.microsoft.com/mssql/server:2019-latest").acceptLicense());

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
	void testMetadataReaderRemainsUsableOnSqlServer2019() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl() + ";databaseName=" + DATABASE_NAME,
				SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE METADATA_2019 (
						ID BIGINT NOT NULL PRIMARY KEY,
						VALUE NVARCHAR(100)
					)
					""");

			Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertInstanceOf(SqlServer2019.class, dialect);
			var reader = dialect.getCatalogReader();
			reader.setCatalogName(connection.getCatalog());
			Catalog catalog = reader.getAllFull(connection).stream()
					.filter(current -> connectionCatalogEquals(connection, current))
					.findFirst().orElseThrow();
			assertNotNull(catalog.getSchemas().get("dbo").getTables()
					.get("METADATA_2019"));
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

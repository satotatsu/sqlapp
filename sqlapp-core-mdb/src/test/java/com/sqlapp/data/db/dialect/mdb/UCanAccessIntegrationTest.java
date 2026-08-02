/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Schema;

class UCanAccessIntegrationTest {

	@TempDir
	Path tempDirectory;

	@Test
	void readsAccdbWithoutOdbc() throws Exception {
		final URL resource = getClass().getResource("/AccessSample.accdb");
		assertNotNull(resource);
		final Path database = tempDirectory.resolve("AccessSample.accdb");
		Files.copy(Path.of(resource.toURI()), database);

		try (Connection connection = DriverManager.getConnection("jdbc:ucanaccess://" + database.toAbsolutePath())) {
			final DatabaseMetaData metadata = connection.getMetaData();
			assertTrue(metadata.getDatabaseProductName().contains("UCanAccess"),
					metadata.getDatabaseProductName());
			final Dialect dialect = DialectResolver.getInstance().getDialect(connection);
			assertTrue(dialect instanceof Mdb, dialect.getClass().getName());
			final Schema schema = dialect.getCatalogReader().getSchemaReader()
					.getAllFull(connection).get(0);
			assertNotNull(schema.getTables().get("T_customer"));
			assertNotNull(schema.getTables().get("T_item"));
			assertNotNull(schema.getTables().get("T_stock"));

			boolean hasUserTable = false;
			try (ResultSet tables = metadata.getTables(null, null, "%", new String[] { "TABLE" })) {
				while (tables.next()) {
					hasUserTable = true;
					break;
				}
			}
			assertTrue(hasUserTable);

		}
	}

	@Test
	void writesAccdbAndReturnsAutoNumberWithoutOdbc() throws Exception {
		final Path database = tempDirectory.resolve("Generated.accdb");
		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath() + ";newDatabaseVersion=V2010")) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE [SQLAPP_UCANACCESS_TEST] ([ID] COUNTER PRIMARY KEY, [NAME] TEXT(50))");
			}
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO [SQLAPP_UCANACCESS_TEST] ([NAME]) VALUES (?)",
					Statement.RETURN_GENERATED_KEYS)) {
				statement.setString(1, "ucanaccess");
				assertEquals(1, statement.executeUpdate());
				try (ResultSet keys = statement.getGeneratedKeys()) {
					assertTrue(keys.next());
					assertEquals(1L, keys.getLong(1));
				}
			}
			try (Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery(
							"SELECT [ID], [NAME] FROM [SQLAPP_UCANACCESS_TEST]")) {
				assertTrue(rows.next());
				assertEquals(1L, rows.getLong(1));
				assertEquals("ucanaccess", rows.getString(2));
			}
		}
	}
}

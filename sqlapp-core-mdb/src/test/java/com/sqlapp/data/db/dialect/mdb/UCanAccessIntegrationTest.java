/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

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
			assertFalse(dialect.getCatalogReader().getAllFull(connection).isEmpty());
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

	@Test
	void executesGeneratedAccessDdlAndTruncateSql() throws Exception {
		final Path database = tempDirectory.resolve("GeneratedSql.accdb");
		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			final Dialect dialect = DialectResolver.getInstance()
					.getDialect(connection);
			final Table table = new Table("ORDER DETAIL");
			table.setDialect(dialect);
			final Column id = new Column("ID")
					.setDataType(DataType.INT).setIdentity(true);
			table.getColumns().add(id);
			table.getColumns().add(new Column("DISPLAY NAME")
					.setDataType(DataType.NVARCHAR).setLength(50L)
					.setNotNull(true));
			table.getColumns().add(new Column("DESCRIPTION")
					.setDataType(DataType.LONGNVARCHAR));
			table.getColumns().add(new Column("ENABLED")
					.setDataType(DataType.BOOLEAN));
			table.getColumns().add(new Column("ATTACHMENT")
					.setDataType(DataType.BLOB));
			table.getColumns().add(new Column("CREATED AT")
					.setDataType(DataType.DATETIME)
					.setDefaultValue(dialect.getCurrentDateTimeFunction()));
			table.setPrimaryKey("PK_ORDER_DETAIL", id);
			table.getIndexes().add("IDX_ORDER_DETAIL_NAME",
					table.getColumns().get("DISPLAY NAME"));

			final List<SqlOperation> creates = dialect.createSqlFactoryRegistry()
					.createSql(table, SqlType.CREATE);
			final SqlOperation create = creates.get(0);
			assertTrue(create.getSqlText().contains("[ORDER DETAIL]"),
					create.getSqlText());
			assertTrue(create.getSqlText().contains("COUNTER"),
					create.getSqlText());
			assertTrue(create.getSqlText().contains("MEMO"),
					create.getSqlText());
			assertTrue(create.getSqlText().contains("OLE"),
					create.getSqlText());
			assertTrue(create.getSqlText().contains("DEFAULT NOW()"),
					create.getSqlText());
			try (Statement statement = connection.createStatement()) {
				for (final SqlOperation operation : creates) {
					statement.execute(operation.getSqlText());
				}
				statement.executeUpdate(
						"INSERT INTO [ORDER DETAIL] ([DISPLAY NAME]) VALUES ('first')");
			}
			final Table loaded = dialect.getCatalogReader().getSchemaReader()
					.getAllFull(connection).get(0).getTables()
					.get("ORDER DETAIL");
			assertNotNull(loaded);
			assertTrue(loaded.getColumns().get("ID").isIdentity());
			assertNotNull(loaded.getConstraints().getPrimaryKeyConstraint());
			assertNotNull(loaded.getIndexes().get("IDX_ORDER_DETAIL_NAME"),
					loaded.getIndexes().toString());

			final SqlOperation truncate = dialect.createSqlFactoryRegistry()
					.createSql(table, SqlType.TRUNCATE).get(0);
			assertTrue(truncate.getSqlText().startsWith("DELETE FROM"),
					truncate.getSqlText());
			try (Statement statement = connection.createStatement()) {
				assertEquals(1, statement.executeUpdate(truncate.getSqlText()));
			}
		}
	}

	@Test
	void exposesOnlyLastGeneratedKeyForJdbcBatch() throws Exception {
		final Path database = tempDirectory.resolve("BatchKeys.accdb");
		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			try (Statement statement = connection.createStatement()) {
				statement.execute(
						"CREATE TABLE [BATCH_KEY_TEST] ([ID] COUNTER PRIMARY KEY, [NAME] TEXT(50))");
			}
			final Dialect dialect = DialectResolver.getInstance()
					.getDialect(connection);
			assertFalse(dialect.supportsBatchExecuteGeneratedKeys());
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO [BATCH_KEY_TEST] ([NAME]) VALUES (?)",
					Statement.RETURN_GENERATED_KEYS)) {
				statement.setString(1, "first");
				statement.addBatch();
				statement.setString(1, "second");
				statement.addBatch();
				assertEquals(2, statement.executeBatch().length);
				try (ResultSet keys = statement.getGeneratedKeys()) {
					assertTrue(keys.next());
					assertEquals(2L, keys.getLong(1));
					assertFalse(keys.next());
				}
			}
		}
	}
}

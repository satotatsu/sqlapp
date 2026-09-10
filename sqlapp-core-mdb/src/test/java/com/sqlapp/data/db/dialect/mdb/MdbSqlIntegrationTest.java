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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;
import com.sqlapp.jdbc.sql.BindParameter;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.util.CommonUtils;

class MdbSqlIntegrationTest {

	@TempDir
	Path tempDirectory;

	@Test
	void generatedDdlDmlAndMetadataRoundTrip() throws Exception {
		final Path database = tempDirectory.resolve("generated.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table table = createTable(dialect);
		final List<SqlOperation> creates = dialect.createSqlFactoryRegistry()
				.createSql(table, SqlType.CREATE);
		assertTrue(creates.get(0).getSqlText().contains("ID COUNTER PRIMARY KEY"),
				creates.get(0).getSqlText());
		assertTrue(creates.stream().anyMatch(operation -> operation.getSqlText()
				.startsWith("CREATE INDEX IDX_ORDER_DETAIL_NAME")));

		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			try (Statement statement = connection.createStatement()) {
				for (final SqlOperation operation : creates) {
					statement.execute(operation.getSqlText());
				}
			}

			for (int i = 1; i <= 2; i++) {
				final Row row = table.newRow();
				row.put("DISPLAY_NAME", "row-" + i);
				row.put("DESCRIPTION", "description-" + i);
				row.put("ENABLED", i % 2 == 0);
				row.put("ATTACHMENT", new byte[] { (byte) i });
				row.put("CREATED_AT", Timestamp.valueOf(
						"2026-09-10 12:00:0" + i));
				table.getRows().add(row);
			}
			final SqlParameterCollection parameters = CommonUtils.first(
					dialect.createSqlFactoryRegistry()
							.createSqlNodes(table, SqlType.INSERT_ROWS))
					.eval(table.getRows());
			assertTrue(parameters.getSql().replaceAll("\\s+", " ")
					.contains("VALUES (?"),
					parameters.getSql());
			assertFalse(parameters.getSql().contains("(VALUES(0))"),
					parameters.getSql());
			try (PreparedStatement statement = connection
					.prepareStatement(parameters.getSql())) {
				final List<BindParameter> bindParameters = parameters
						.getBindParameters().get(0).getBindParameters();
				for (int i = 0; i < bindParameters.size(); i++) {
					statement.setObject(i + 1, bindParameters.get(i).getValue());
				}
				assertEquals(2, statement.executeUpdate());
			}

			final String truncateSql = dialect.createSqlFactoryRegistry()
					.createSql(table, SqlType.TRUNCATE).get(0).getSqlText();
			assertTrue(truncateSql.startsWith("DELETE FROM"), truncateSql);
			try (Statement statement = connection.createStatement()) {
				assertEquals(2, statement.executeUpdate(truncateSql));
				try (ResultSet resultSet = statement.executeQuery(
						"SELECT COUNT(*) FROM [ORDER DETAIL]")) {
					assertTrue(resultSet.next());
					assertEquals(0, resultSet.getInt(1));
				}
			}
		}

		final Table loaded = MdbFileLoader.loadTable(database, "ORDER DETAIL");
		assertTrue(loaded.getColumns().get("ID").isIdentity());
		assertNotNull(loaded.getConstraints().getPrimaryKeyConstraint());
		assertNotNull(loaded.getIndexes().get("IDX_ORDER_DETAIL_NAME"));
	}

	@Test
	void generatedSingleRowCrudAndGeneratedKeyExecute() throws Exception {
		final Path database = tempDirectory.resolve("crud.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table table = new Table("CRUD TARGET");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.INT)
				.setIdentity(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("DISPLAY NAME")
				.setDataType(DataType.NVARCHAR).setLength(80L)
				.setNotNull(true));
		table.getColumns().add(new Column("ACTIVE")
				.setDataType(DataType.BOOLEAN));
		table.getColumns().add(new Column("UPDATED AT")
				.setDataType(DataType.DATETIME));
		table.setPrimaryKey("PK_CRUD_TARGET", id);

		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(table, SqlType.CREATE));

			final Row row = table.newRow();
			row.put("DISPLAY NAME", "first");
			row.put("ACTIVE", true);
			row.put("UPDATED AT", Timestamp.valueOf("2026-09-10 13:14:15"));
			final SqlParameterCollection insert = parameters(dialect, table,
					SqlType.INSERT, row);
			assertTrue(insert.getSql().contains("[DISPLAY NAME]"), insert.getSql());
			assertEquals(3, insert.getParameterSize(), insert.toString());
			try (PreparedStatement statement = connection.prepareStatement(
					insert.getSql(), Statement.RETURN_GENERATED_KEYS)) {
				bind(statement, insert);
				assertEquals(1, statement.executeUpdate());
				try (ResultSet keys = statement.getGeneratedKeys()) {
					assertTrue(keys.next());
					row.put("ID", keys.getInt(1));
					assertTrue(((Number) row.get("ID")).intValue() > 0);
				}
			}

			row.put("DISPLAY NAME", "updated");
			row.put("ACTIVE", false);
			final SqlParameterCollection update = parameters(dialect, table,
					SqlType.UPDATE, row);
			try (PreparedStatement statement = connection
					.prepareStatement(update.getSql())) {
				bind(statement, update);
				assertEquals(1, statement.executeUpdate());
			}

			final SqlParameterCollection select = parameters(dialect, table,
					SqlType.SELECT, row);
			try (PreparedStatement statement = connection
					.prepareStatement(select.getSql())) {
				bind(statement, select);
				try (ResultSet resultSet = statement.executeQuery()) {
					assertTrue(resultSet.next());
					assertEquals("updated", resultSet.getString("DISPLAY NAME"));
					assertFalse(resultSet.getBoolean("ACTIVE"));
					assertEquals(Timestamp.valueOf("2026-09-10 13:14:15"),
							resultSet.getTimestamp("UPDATED AT"));
				}
			}

			final SqlParameterCollection delete = parameters(dialect, table,
					SqlType.DELETE, row);
			try (PreparedStatement statement = connection
					.prepareStatement(delete.getSql())) {
				bind(statement, delete);
				assertEquals(1, statement.executeUpdate());
			}
			try (Statement statement = connection.createStatement();
					ResultSet resultSet = statement.executeQuery(
							"SELECT COUNT(*) FROM [CRUD TARGET]")) {
				assertTrue(resultSet.next());
				assertEquals(0, resultSet.getInt(1));
			}
		}
	}

	@Test
	void conditionalInsertExecutesAndMergeIsRejected() throws Exception {
		final Path database = tempDirectory.resolve("conditional.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table table = new Table("CONDITIONAL_TARGET");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.INT)
				.setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("TXT").setDataType(DataType.NVARCHAR)
				.setLength(80L));
		table.setPrimaryKey("PK_CONDITIONAL_TARGET", id);
		final Row row = table.newRow();
		row.put("ID", 1);
		row.put("TXT", "first");

		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(table, SqlType.CREATE));
			final SqlParameterCollection insert = parameters(dialect, table,
					SqlType.INSERT_SELECT_NOT_EXISTS, row);
			assertTrue(insert.getSql().contains("FROM (VALUES(0))"),
					insert.getSql());
			try (PreparedStatement statement = connection
					.prepareStatement(insert.getSql())) {
				bind(statement, insert);
				assertEquals(1, statement.executeUpdate());
				bind(statement, insert);
				assertEquals(0, statement.executeUpdate());
			}
		}

		final UnsupportedOperationException mergeError = assertThrows(
				UnsupportedOperationException.class,
				() -> dialect.createSqlFactoryRegistry()
						.createSql(table, SqlType.MERGE));
		assertTrue(mergeError.getMessage().contains("MERGE"));
	}

	@Test
	void generatedRelationshipsViewsAndIndexDropExecute() throws Exception {
		final Path database = tempDirectory.resolve("objects.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table parent = new Table("PARENT TABLE");
		parent.setDialect(dialect);
		final Column parentId = new Column("ID").setDataType(DataType.INT)
				.setNotNull(true);
		parent.getColumns().add(parentId);
		parent.setPrimaryKey("PK_PARENT", parentId);

		final Table child = new Table("CHILD TABLE");
		child.setDialect(dialect);
		final Column childId = new Column("ID").setDataType(DataType.INT)
				.setIdentity(true);
		final Column childParentId = new Column("PARENT_ID")
				.setDataType(DataType.INT).setNotNull(true);
		child.getColumns().add(childId);
		child.getColumns().add(childParentId);
		child.setPrimaryKey("PK_CHILD", childId);
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT",
				childParentId, parentId).setDeleteRule(CascadeRule.Cascade)
				.setUpdateRule(CascadeRule.Cascade);
		final Index index = child.getIndexes().add("IDX_CHILD_PARENT",
				childParentId);

		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(parent, SqlType.CREATE));
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(child, SqlType.CREATE));
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("INSERT INTO [PARENT TABLE] (ID) VALUES (1)");
				statement.executeUpdate("INSERT INTO [CHILD TABLE] (PARENT_ID) VALUES (1)");
			}

			final View view = new View("CHILD VIEW");
			view.setDialect(dialect);
			view.setStatement("SELECT ID, PARENT_ID FROM [CHILD TABLE]");
			final UnsupportedOperationException viewError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(view, SqlType.CREATE));
			assertTrue(viewError.getMessage().contains("UCanAccess 5.1.6"));

			final UnsupportedOperationException indexError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(index, SqlType.DROP));
			assertTrue(indexError.getMessage().contains("DROP INDEX"));
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("DELETE FROM [PARENT TABLE] WHERE ID=1");
				try (ResultSet resultSet = statement.executeQuery(
						"SELECT COUNT(*) FROM [CHILD TABLE]")) {
					assertTrue(resultSet.next());
					assertEquals(0, resultSet.getInt(1));
				}
			}
		}
		final var persistedRows = MdbFileLoader
				.loadTable(database, "CHILD TABLE").getRows().iterator();
		assertFalse(persistedRows.hasNext());
	}

	@Test
	void generatedAlterTableOperationsExecuteAndPersist() throws Exception {
		final Path database = tempDirectory.resolve("alter.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table parent = new Table("ALTER PARENT");
		parent.setDialect(dialect);
		final Column parentId = new Column("ID").setDataType(DataType.INT)
				.setNotNull(true);
		parent.getColumns().add(parentId);
		parent.setPrimaryKey("PK_ALTER_PARENT", parentId);
		final Table original = new Table("ALTER TARGET");
		original.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.INT)
				.setNotNull(true);
		original.getColumns().add(id);
		original.getColumns().add(new Column("OLD_VALUE")
				.setDataType(DataType.VARCHAR).setLength(20L));
		original.setPrimaryKey("PK_ALTER_TARGET", id);

		try (Connection connection = DriverManager.getConnection(
				"jdbc:ucanaccess://" + database.toAbsolutePath()
						+ ";newDatabaseVersion=V2010")) {
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(parent, SqlType.CREATE));
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(original, SqlType.CREATE));

			final Table added = original.clone();
			added.getColumns().add(new Column("NEW_VALUE")
					.setDataType(DataType.VARCHAR).setLength(40L));
			added.getColumns().add(new Column("PARENT_ID")
					.setDataType(DataType.INT));
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(original.diff(added)));

			final Table altered = added.clone();
			altered.getColumns().get("NEW_VALUE").setLength(80L);
			final UnsupportedOperationException alterError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(added.diff(altered)));
			assertTrue(alterError.getMessage()
					.contains("column definition alteration"));

			final Table constrained = added.clone();
			constrained.getConstraints().addForeignKeyConstraint(
					"FK_ALTER_PARENT", constrained.getColumns().get("PARENT_ID"),
					parentId).setDeleteRule(CascadeRule.Cascade);
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(added.diff(constrained)));

			final Table unconstrained = constrained.clone();
			unconstrained.getConstraints().remove("FK_ALTER_PARENT");
			final UnsupportedOperationException constraintError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(constrained.diff(unconstrained)));
			assertTrue(constraintError.getMessage().contains("DROP CONSTRAINT"));

			final Table indexed = constrained.clone();
			indexed.getIndexes().add("IDX_ALTER_VALUE",
					indexed.getColumns().get("NEW_VALUE"));
			execute(connection, dialect.createSqlFactoryRegistry()
					.createSql(constrained.diff(indexed)));
			final Table unindexed = indexed.clone();
			unindexed.getIndexes().remove("IDX_ALTER_VALUE");
			final UnsupportedOperationException indexError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(indexed.diff(unindexed)));
			assertTrue(indexError.getMessage().contains("DROP INDEX"));

			final Table dropped = indexed.clone();
			dropped.getColumns().remove("OLD_VALUE");
			final UnsupportedOperationException dropColumnError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(indexed.diff(dropped)));
			assertTrue(dropColumnError.getMessage().contains("DROP COLUMN"));

			final Table renamed = indexed.clone();
			renamed.getColumns().get("NEW_VALUE").setName("RENAMED_VALUE");
			final UnsupportedOperationException renameError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(indexed.diff(renamed)));
			assertTrue(renameError.getMessage().contains("UCanAccess 5.1.6"));

			final Table renamedTable = indexed.clone().setName("RENAMED TABLE");
			final UnsupportedOperationException tableRenameError = assertThrows(
					UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry()
							.createSql(indexed.diff(renamedTable)));
			assertTrue(tableRenameError.getMessage().contains("table rename"));
		}

		final Table loaded = MdbFileLoader.loadTable(database, "ALTER TARGET");
		assertNotNull(loaded.getColumns().get("NEW_VALUE"));
		assertEquals(40L, loaded.getColumns().get("NEW_VALUE").getLength());
		assertNotNull(loaded.getColumns().get("OLD_VALUE"));
		assertNotNull(loaded.getIndexes().get("IDX_ALTER_VALUE"));
		assertNotNull(loaded.getConstraints().get("FK_ALTER_PARENT"));
	}

	private void execute(final Connection connection,
			final List<SqlOperation> operations) throws Exception {
		try (Statement statement = connection.createStatement()) {
			for (final SqlOperation operation : operations) {
				statement.execute(operation.getSqlText());
			}
		}
	}

	private SqlParameterCollection parameters(final Dialect dialect,
			final Table table, final SqlType sqlType, final Row row) {
		return CommonUtils.first(dialect.createSqlFactoryRegistry()
				.createSqlNodes(table, sqlType)).eval(row);
	}

	private void bind(final PreparedStatement statement,
			final SqlParameterCollection parameters) throws Exception {
		parameters.setBind(statement);
	}

	private Table createTable(final Dialect dialect) {
		final Table table = new Table("ORDER DETAIL");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.INT)
				.setIdentity(true);
		table.getColumns().add(id);
		table.getColumns().add(new Column("DISPLAY_NAME")
				.setDataType(DataType.NVARCHAR).setLength(50L)
				.setNotNull(true));
		table.getColumns().add(new Column("DESCRIPTION")
				.setDataType(DataType.LONGNVARCHAR));
		table.getColumns().add(new Column("ENABLED")
				.setDataType(DataType.BOOLEAN));
		table.getColumns().add(new Column("ATTACHMENT")
				.setDataType(DataType.BLOB));
		table.getColumns().add(new Column("CREATED_AT")
				.setDataType(DataType.DATETIME)
				.setDefaultValue(dialect.getCurrentDateTimeFunction()));
		table.setPrimaryKey("PK_ORDER_DETAIL", id);
		table.getIndexes().add("IDX_ORDER_DETAIL_NAME",
				table.getColumns().get("DISPLAY_NAME"));
		return table;
	}
}

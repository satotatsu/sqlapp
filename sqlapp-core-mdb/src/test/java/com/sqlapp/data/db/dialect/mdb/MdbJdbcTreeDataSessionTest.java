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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** End-to-end UCanAccess coverage for the tree data session. */
class MdbJdbcTreeDataSessionTest {

	@TempDir
	Path tempDirectory;

	@Test
	void explicitKeysUseBatchesAndReusePreparedStatements() throws Exception {
		final Path database = tempDirectory.resolve("tree-explicit.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table[] tables = createTables(dialect, false);
		final Table parent = tables[0];
		final Table child = tables[1];

		try (Connection connection = open(database)) {
			connection.setAutoCommit(false);
			create(connection, dialect, parent, child);
			final Set<PreparedStatement> statements = Collections
					.newSetFromMap(new IdentityHashMap<>());
			final AtomicInteger executions = new AtomicInteger();
			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection,
					parent, child)) {
				session.setRootBatchSize(3);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setPreparedStatementBeforeExecuteHandler(statement -> {
					statements.add(statement);
					executions.incrementAndGet();
				});
				for (int i = 1; i <= 5; i++) {
					final Row parentRow = session.newRow(parent);
					parentRow.put("ID", i);
					parentRow.put("TXT", "parent-" + i);
					final Row childRow = session.newRow(child);
					childRow.put("ID", i * 10);
					childRow.put("TXT", "child-" + i);
				}
			}

			assertEquals(2, statements.size());
			assertEquals(4, executions.get());
			try (Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery("""
							SELECT p.ID, p.TXT, c.PARENT_ID, c.TXT
							FROM TREE_PARENT p
							INNER JOIN TREE_CHILD c ON c.PARENT_ID = p.ID
							ORDER BY p.ID
							""")) {
				for (int i = 1; i <= 5; i++) {
					assertTrue(rows.next());
					assertEquals(i, rows.getInt(1));
					assertEquals("parent-" + i, rows.getString(2));
					assertEquals(i, rows.getInt(3));
					assertEquals("child-" + i, rows.getString(4));
				}
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void singleAutoNumberRowPropagatesToChild() throws Exception {
		final Path database = tempDirectory.resolve("tree-identity.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table[] tables = createTables(dialect, true);
		final Table parent = tables[0];
		final Table child = tables[1];

		try (Connection connection = open(database)) {
			connection.setAutoCommit(false);
			create(connection, dialect, parent, child);
			final Row parentRow;
			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection,
					parent, child)) {
				session.setTableOperationMode(TableOperationMode.INSERT);
				parentRow = session.newRow(parent);
				parentRow.put("TXT", "parent");
				final Row childRow = session.newRow(child);
				childRow.put("TXT", "child");
			}
			assertNotNull(parentRow.get("ID"));
			try (Statement statement = connection.createStatement();
					ResultSet row = statement.executeQuery("""
							SELECT p.ID, c.PARENT_ID
							FROM TREE_PARENT p
							INNER JOIN TREE_CHILD c ON c.PARENT_ID = p.ID
							""")) {
				assertTrue(row.next());
				assertEquals(row.getInt(1), row.getInt(2));
			}
		}
	}

	@Test
	void multipleAutoNumberRowsFallBackToReusableSingleRowStatement()
			throws Exception {
		final Path database = tempDirectory.resolve("tree-identity-batch.accdb");
		final Dialect dialect = DialectHolder.defaultDialect;
		final Table parent = createTables(dialect, true)[0];

		try (Connection connection = open(database)) {
			connection.setAutoCommit(false);
			create(connection, dialect, parent);
			final Set<PreparedStatement> statements = Collections
					.newSetFromMap(new IdentityHashMap<>());
			final AtomicInteger executions = new AtomicInteger();
			final Row[] inserted = new Row[2];
			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection,
					parent)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setPreparedStatementBeforeExecuteHandler(statement -> {
					statements.add(statement);
					executions.incrementAndGet();
				});
				for (int i = 0; i < inserted.length; i++) {
					inserted[i] = session.newRow(parent);
					inserted[i].put("TXT", "parent-" + (i + 1));
				}
			}
			assertEquals(1, statements.size());
			assertEquals(2, executions.get());
			assertNotNull(inserted[0].get("ID"));
			assertNotNull(inserted[1].get("ID"));
			assertFalse(inserted[0].get("ID").equals(inserted[1].get("ID")));
			try (Statement statement = connection.createStatement();
					ResultSet rows = statement.executeQuery(
							"SELECT COUNT(*) FROM TREE_PARENT")) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
		}
	}

	private Connection open(final Path database) throws Exception {
		return DriverManager.getConnection("jdbc:ucanaccess://"
				+ database.toAbsolutePath() + ";newDatabaseVersion=V2010");
	}

	private void create(final Connection connection, final Dialect dialect,
			final Table... tables) throws Exception {
		try (Statement statement = connection.createStatement()) {
			for (final Table table : tables) {
				for (final SqlOperation operation : dialect.createSqlFactoryRegistry()
						.createSql(table, SqlType.CREATE)) {
					statement.execute(operation.getSqlText());
				}
			}
		}
		connection.commit();
	}

	private Table[] createTables(final Dialect dialect,
			final boolean identity) {
		final Table parent = new Table("TREE_PARENT");
		parent.setDialect(dialect);
		final Column parentId = new Column("ID").setDataType(DataType.INT)
				.setIdentity(identity).setNotNull(true);
		parent.getColumns().add(parentId);
		parent.getColumns().add(new Column("TXT").setDataType(DataType.NVARCHAR)
				.setLength(80L));
		parent.setPrimaryKey("PK_TREE_PARENT", parentId);

		final Table child = new Table("TREE_CHILD");
		child.setDialect(dialect);
		final Column childId = new Column("ID").setDataType(DataType.INT)
				.setIdentity(identity).setNotNull(true);
		final Column childParentId = new Column("PARENT_ID")
				.setDataType(DataType.INT).setNotNull(true);
		child.getColumns().add(childId);
		child.getColumns().add(childParentId);
		child.getColumns().add(new Column("TXT").setDataType(DataType.NVARCHAR)
				.setLength(80L));
		child.setPrimaryKey("PK_TREE_CHILD", childId);
		child.getConstraints().addForeignKeyConstraint("FK_TREE_PARENT",
				childParentId, parentId);
		return new Table[] { parent, child };
	}
}

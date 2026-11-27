/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.sql.JdbcTreeDataCopySession.HoldCursorStrategy;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/**
 * Copying hierarchies between separate connections into tables with generated
 * keys, where re-copying a root would create duplicates.
 */
class JdbcTreeDataCopySessionRestartTest {
	private static final String ROOT_SQL = """
			SELECT ID, NAME FROM SRC_PARENT
			WHERE 1=1
			  /*if checkpoint != null*/ AND ID > /*checkpoint.ID*/0 /*end*/
			ORDER BY ID
			""";

	@Test
	void separateConnectionsWithoutRestartPolicyAreRejectedBeforeCopying() throws Exception {
		try (var source = source(); var target = target()) {
			var failure = assertThrows(IllegalStateException.class,
					() -> copy(source, target, JdbcTreeDataRestart.none(), HoldCursorStrategy.DIALECT, Set.of(), -1));
			assertTrue(failure.getMessage().contains("Separate source and target connections"), failure.getMessage());
			assertEquals(0, count(target, "TGT_PARENT"));
		}
	}

	@ParameterizedTest
	@EnumSource(value = HoldCursorStrategy.class, names = { "HOLD", "REOPEN" })
	void checkpointOnTheTargetConnectionResumesWithoutDuplicatesAndLeavesTheSourceUnchanged(
			HoldCursorStrategy cursor) throws Exception {
		try (var source = source(); var target = target()) {
			var restart = restart(target);
			var first = new ArrayList<Integer>();
			// Roots 1 and 2 were committed with the checkpoint; root 3 was rolled back.
			assertThrows(IllegalStateException.class, () -> copy(source, target, restart, cursor, Set.of(), 4, first));
			assertEquals(List.of(1, 2), parents(target));
			var second = new ArrayList<Integer>();
			copy(source, target, restart, cursor, Set.of(), -1, second);
			assertEquals(List.of(3, 4, 5), second);
			assertEquals(List.of(1, 2, 3, 4, 5), parents(target));
			assertEquals(10, count(target, "TGT_CHILD"));
			assertEquals(0, orphans(target));
			var third = new ArrayList<Integer>();
			copy(source, target, restart, cursor, Set.of(), -1, third);
			assertEquals(List.of(), third);
			assertEquals(5, count(target, "TGT_PARENT"));
			// The source is only read.
			assertEquals(5, count(source, "SRC_PARENT"));
			assertEquals(10, count(source, "SRC_CHILD"));
		}
	}

	@Test
	void rootsWithoutTargetRowsAreRecordedBeforeTheCursorIsReopened() throws Exception {
		try (var source = source(); var target = target()) {
			var restart = restart(target);
			var copied = new ArrayList<Integer>();
			// Roots 2 and 3 produce no target rows, so batch 2..3 has no target batch.
			copy(source, target, restart, HoldCursorStrategy.REOPEN, Set.of(2, 3), -1, copied);
			assertEquals(List.of(1, 2, 3, 4, 5), copied);
			assertEquals(List.of(1, 4, 5), parents(target));
			copy(source, target, restart, HoldCursorStrategy.REOPEN, Set.of(), -1, new ArrayList<>());
			assertEquals(List.of(1, 4, 5), parents(target));
		}
	}

	@Test
	void unorderedSourceRootsAreRejected() throws Exception {
		try (var source = source(); var target = target()) {
			var restart = restart(target);
			var failure = assertThrows(IllegalStateException.class, () -> copy(source, target, restart,
					ROOT_SQL.replace("ORDER BY ID", "ORDER BY ID DESC"), HoldCursorStrategy.HOLD, Set.of(), -1,
					new ArrayList<>()));
			assertTrue(failure.getMessage().contains("strictly increasing"), failure.getMessage());
			assertEquals(0, count(target, "TGT_PARENT"));
		}
	}

	@Test
	void aCheckpointStoreOnTheSourceConnectionIsRejected() throws Exception {
		try (var source = source(); var target = target()) {
			var restart = JdbcTreeDataRestart.checkpoint("copy", List.of("ID"),
					new JdbcBulkMigrationCheckpointStore(source, "TREE_CHECKPOINTS"));
			var failure = assertThrows(IllegalArgumentException.class,
					() -> copy(source, target, restart, HoldCursorStrategy.HOLD, Set.of(), -1));
			assertTrue(failure.getMessage().contains("session connection"), failure.getMessage());
		}
	}

	private static JdbcTreeDataRestart restart(Connection target) throws SQLException {
		return JdbcTreeDataRestart.checkpoint("copy", List.of("ID"),
				new JdbcBulkMigrationCheckpointStore(target, "TREE_CHECKPOINTS"));
	}

	private static void copy(Connection source, Connection target, JdbcTreeDataRestart restart,
			HoldCursorStrategy cursor, Set<Integer> skipped, int failAt) throws SQLException {
		copy(source, target, restart, cursor, skipped, failAt, new ArrayList<>());
	}

	private static void copy(Connection source, Connection target, JdbcTreeDataRestart restart,
			HoldCursorStrategy cursor, Set<Integer> skipped, int failAt, List<Integer> copied) throws SQLException {
		copy(source, target, restart, ROOT_SQL, cursor, skipped, failAt, copied);
	}

	private static void copy(Connection source, Connection target, JdbcTreeDataRestart restart, String rootSql,
			HoldCursorStrategy cursor, Set<Integer> skipped, int failAt, List<Integer> copied) throws SQLException {
		Table parent = table(source, "SRC_PARENT", "ID INT", "NAME VARCHAR");
		parent.setPrimaryKey((String) null, parent.getColumns().get("ID"));
		Table child = table(source, "SRC_CHILD", "PARENT_ID INT", "NO INT", "TXT VARCHAR");
		child.setPrimaryKey((String) null, child.getColumns().get("PARENT_ID"), child.getColumns().get("NO"));
		child.getConstraints().addForeignKeyConstraint("FK_SRC_CHILD", child.getColumns().get("PARENT_ID"),
				parent.getColumns().get("ID"));
		Table parentTo = table(target, "TGT_PARENT", "ID INT", "SRC_ID INT", "NAME VARCHAR");
		parentTo.getColumns().get("ID").setIdentity(true);
		parentTo.setPrimaryKey((String) null, parentTo.getColumns().get("ID"));
		Table childTo = table(target, "TGT_CHILD", "ID INT", "PARENT_ID INT", "TXT VARCHAR");
		childTo.getColumns().get("ID").setIdentity(true);
		childTo.setPrimaryKey((String) null, childTo.getColumns().get("ID"));
		childTo.getConstraints().addForeignKeyConstraint("FK_TGT_CHILD", childTo.getColumns().get("PARENT_ID"),
				parentTo.getColumns().get("ID"));
		var reader = new JdbcTreeDataSession(source, parent, child);
		reader.setTableOperationMode(TableOperationMode.NONE);
		var writer = new JdbcTreeDataSession(target, parentTo, childTo);
		writer.setTableOperationMode(TableOperationMode.INSERT);
		try (var copy = new JdbcTreeDataCopySession(reader, writer)) {
			copy.setRestart(restart);
			copy.setRootBatchSize(2);
			copy.setCommitEveryRootBatches(1);
			copy.setHoldCursorStrategy(cursor);
			reader.select(parent, rootSql);
			reader.select(child);
			copy.execute(active -> {
				while (active.next(parent)) {
					Row row = active.getRow(parent);
					int id = (Integer) row.get("ID");
					if (id == failAt)
						throw new IllegalStateException("business failure at " + id);
					copied.add(id);
					if (skipped.contains(id)) {
						while (active.next(child)) {
							active.getRow(child);
						}
						continue;
					}
					Row copiedParent = active.newRow(parentTo);
					copiedParent.put("SRC_ID", id);
					copiedParent.put("NAME", row.get("NAME"));
					while (active.next(child)) {
						Row childRow = active.getRow(child);
						active.newRow(childTo).put("TXT", childRow.get("TXT"));
					}
				}
			});
		}
	}

	private static Table table(Connection connection, String name, String... columns) throws SQLException {
		var schema = new Schema("PUBLIC");
		var table = new Table(name);
		schema.getTables().add(table);
		table.setDialect(DialectResolver.getInstance().getDialect(connection));
		for (String column : columns) {
			String[] parts = column.split(" ");
			table.getColumns().add(new Column(parts[0])
					.setDataType(parts[1].equals("INT") ? DataType.INT : DataType.VARCHAR).setLength(
							parts[1].equals("INT") ? null : 20));
		}
		return table;
	}

	private static Connection source() throws SQLException {
		var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SRC_PARENT(ID INT PRIMARY KEY, NAME VARCHAR(20))");
			statement.execute("CREATE TABLE SRC_CHILD(PARENT_ID INT NOT NULL, NO INT NOT NULL, TXT VARCHAR(20),"
					+ " PRIMARY KEY(PARENT_ID, NO), FOREIGN KEY(PARENT_ID) REFERENCES SRC_PARENT(ID))");
			for (int id = 1; id <= 5; id++) {
				statement.execute("INSERT INTO SRC_PARENT VALUES(" + id + ",'P" + id + "')");
				statement.execute("INSERT INTO SRC_CHILD VALUES(" + id + ",1,'C" + id + "-1'),(" + id + ",2,'C" + id
						+ "-2')");
			}
		}
		connection.setAutoCommit(false);
		return connection;
	}

	private static Connection target() throws SQLException {
		var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE TGT_PARENT(ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
					+ " SRC_ID INT NOT NULL, NAME VARCHAR(20))");
			statement.execute("CREATE TABLE TGT_CHILD(ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
					+ " PARENT_ID INT NOT NULL, TXT VARCHAR(20), FOREIGN KEY(PARENT_ID) REFERENCES TGT_PARENT(ID))");
		}
		connection.setAutoCommit(false);
		return connection;
	}

	private static List<Integer> parents(Connection target) throws SQLException {
		var result = new ArrayList<Integer>();
		try (var statement = target.createStatement();
				var rs = statement.executeQuery("SELECT SRC_ID FROM TGT_PARENT ORDER BY SRC_ID")) {
			while (rs.next())
				result.add(rs.getInt(1));
		}
		return result;
	}

	private static int orphans(Connection target) throws SQLException {
		try (var statement = target.createStatement(); var rs = statement.executeQuery(
				"SELECT COUNT(*) FROM TGT_PARENT P WHERE (SELECT COUNT(*) FROM TGT_CHILD C WHERE C.PARENT_ID=P.ID) <> 2")) {
			rs.next();
			return rs.getInt(1);
		}
	}

	private static int count(Connection connection, String table) throws SQLException {
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			rs.next();
			return rs.getInt(1);
		}
	}
}

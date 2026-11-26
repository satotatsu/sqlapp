/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** {@link JdbcTreeDataCopySession#verifyCounts(Table, Table)}. */
class JdbcTreeDataCopySessionCountVerificationTest {

	@Test
	void reportsAndVerifiesCopiedRows() throws Exception {
		try (var connection = database()) {
			var tables = tables(connection);
			var result = copy(connection, tables, TableOperationMode.INSERT, copy -> {
				copy.verifyCounts(tables.get(0), tables.get(1));
				copy.setRootBatchSize(1);
				copy.setCommitEveryRootBatches(1);
			}, id -> true);
			assertEquals(1, result.counts().size());
			var count = result.counts().get(0);
			assertEquals("SRC", count.source().table());
			assertEquals("TGT", count.target().table());
			assertEquals(3, count.read());
			assertEquals(3, count.copied());
			assertTrue(count.verified());
			assertEquals(3, targetRows(connection));
		}
	}

	@Test
	void unverifiedPairsAreReportedWithoutFailing() throws Exception {
		try (var connection = database()) {
			var tables = tables(connection);
			var result = copy(connection, tables, TableOperationMode.INSERT, copy -> {
			}, id -> id != 2);
			var count = result.counts().get(0);
			assertEquals(3, count.read());
			assertEquals(2, count.copied());
			assertFalse(count.verified());
		}
	}

	@Test
	void skippedRowsFailBeforeTheCommit() throws Exception {
		for (int batch : new int[] { 1, 100 }) {
			try (var connection = database()) {
				var tables = tables(connection);
				var failure = assertThrows(IllegalStateException.class,
						() -> copy(connection, tables, TableOperationMode.INSERT, copy -> {
							copy.verifyCounts(tables.get(0), tables.get(1));
							copy.setRootBatchSize(batch);
							copy.setCommitEveryRootBatches(1);
						}, id -> id != 2));
				// Batch 1 fails at the periodic commit after the skipped root, batch 100 at the
				// final commit.
				var expected = batch == 1 ? "(read 2, copied 1, inserted 1)" : "(read 3, copied 2, inserted 2)";
				assertTrue(failure.getMessage().contains("Row count mismatch SRC -> TGT " + expected),
						failure.getMessage());
				assertEquals(0, targetRows(connection));
			}
		}
	}

	@Test
	void rejectsModesAndRowsTheCountCannotDescribe() throws Exception {
		try (var connection = database()) {
			var tables = tables(connection);
			var mode = assertThrows(IllegalStateException.class,
					() -> copy(connection, tables, TableOperationMode.INSERT_IGNORE,
							copy -> copy.verifyCounts(tables.get(0), tables.get(1)), id -> true));
			assertTrue(mode.getMessage().contains("TableOperationMode.INSERT"), mode.getMessage());
			try (var copy = copySession(connection, tables, TableOperationMode.INSERT)) {
				copy.verifyCounts(tables.get(0), tables.get(1));
				assertThrows(IllegalArgumentException.class, () -> copy.verifyCounts(tables.get(1), tables.get(1)));
				assertThrows(IllegalStateException.class, () -> copy.execute(active -> active.newRow(tables.get(1))));
			}
			assertEquals(0, targetRows(connection));
		}
	}

	private interface RowFilter {
		boolean copy(int id);
	}

	private static JdbcTreeDataCopyExecutionResult copy(Connection connection, List<Table> tables,
			TableOperationMode mode, SQLConsumer<JdbcTreeDataCopySession> configure, RowFilter filter)
			throws SQLException {
		var source = tables.get(0);
		var target = tables.get(1);
		try (var copy = copySession(connection, tables, mode)) {
			configure.accept(copy);
			return copy.execute(active -> {
				while (active.next(source)) {
					var row = active.getRow(source);
					if (filter.copy(((Number) row.get("ID")).intValue())) {
						active.newCopy(row, target);
					}
				}
			});
		}
	}

	private static JdbcTreeDataCopySession copySession(Connection connection, List<Table> tables,
			TableOperationMode mode) {
		var reader = new JdbcTreeDataSession(connection, tables.get(0));
		reader.setTableOperationMode(TableOperationMode.NONE);
		reader.select(tables.get(0), "SELECT ID, NAME FROM SRC ORDER BY ID");
		var writer = new JdbcTreeDataSession(connection, tables.get(1));
		writer.setTableOperationMode(mode);
		var copy = new JdbcTreeDataCopySession(reader, writer);
		copy.setProcessedRootHandler(rows -> {
		});
		return copy;
	}

	private static List<Table> tables(Connection connection) throws SQLException {
		var dialect = DialectResolver.getInstance().getDialect(connection);
		var schema = new Schema("PUBLIC");
		var tables = new java.util.ArrayList<Table>();
		for (var name : List.of("SRC", "TGT")) {
			var table = new Table(name);
			schema.getTables().add(table);
			table.setDialect(dialect);
			table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
			table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(20));
			table.setPrimaryKey((String) null, table.getColumns().get("ID"));
			tables.add(table);
		}
		return tables;
	}

	private static Connection database() throws SQLException {
		var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "sa", "");
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SRC(ID INT PRIMARY KEY, NAME VARCHAR(20))");
			statement.execute("CREATE TABLE TGT(ID INT PRIMARY KEY, NAME VARCHAR(20))");
			statement.execute("INSERT INTO SRC VALUES(1,'a'),(2,'b'),(3,'c')");
		}
		connection.setAutoCommit(false);
		return connection;
	}

	private static int targetRows(Connection connection) throws SQLException {
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT COUNT(*) FROM TGT")) {
			rs.next();
			return rs.getInt(1);
		}
	}
}

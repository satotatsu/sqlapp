/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.bulk.*;

class BulkJobTransactionOwnershipTest {
	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void onlyChunksCommitOrRollbackAndReleasePolicyIsPreserved(boolean fail, boolean owned) throws Exception {
		final var actual = database();
		try (var connection = actual.getConnection(); var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY" + (fail ? " CHECK(ID<2)" : "") + ")");
		}
		final var invalid = new AtomicInteger();
		final var commits = new AtomicInteger();
		final var rollbacks = new AtomicInteger();
		final var closed = new AtomicInteger();
		final DataSource monitored = (DataSource) java.lang.reflect.Proxy.newProxyInstance(
				DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class, AutoCloseable.class },
				(proxy, method, args) -> {
					if (method.getName().equals("close")) {
						closed.incrementAndGet();
						return null;
					}
					if (method.getName().equals("getConnection"))
						return connection(actual.getConnection(), invalid, commits, rollbacks);
					try {
						return method.invoke(actual, args);
					} catch (java.lang.reflect.InvocationTargetException failure) {
						throw failure.getCause();
					}
				});
		final var command = new ExecuteBulkMigrationJobCommand();
		command.setDataSource(monitored);
		command.setCloseDataSource(owned);
		command.setPlan(plan());
		if (fail)
			assertThrows(RuntimeException.class, command::run);
		else
			command.run();
		assertEquals(0, invalid.get(), "No commit/rollback is allowed while autoCommit=true");
		// Successful completion commits two data chunks and the completed checkpoint.
		assertEquals(fail ? 1 : 3, commits.get());
		assertEquals(fail ? 1 : 0, rollbacks.get());
		assertEquals(owned ? 1 : 0, closed.get());
		try (var connection = actual.getConnection();
				var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			assertTrue(rows.next());
			assertEquals(fail ? 1 : 2, rows.getInt(1));
		}
	}

	@Test
	void failedBorrowStillClosesOwnedDataSource() {
		final var closed = new AtomicInteger();
		final DataSource unavailable = (DataSource) java.lang.reflect.Proxy.newProxyInstance(
				DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class, AutoCloseable.class },
				(proxy, method, args) -> {
					if (method.getName().equals("close")) {
						closed.incrementAndGet();
						return null;
					}
					throw new SQLException("Synthetic borrow failure");
				});
		final var command = new ExecuteBulkMigrationJobCommand();
		command.setDataSource(unavailable);
		command.setCloseDataSource(true);
		command.setPlan(BulkMigrationJobPlanner.plan(List.of()));
		assertThrows(RuntimeException.class, command::run);
		assertEquals(1, closed.get());
	}

	private static Connection connection(Connection actual, AtomicInteger invalid, AtomicInteger commits,
			AtomicInteger rollbacks) {
		return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					if (method.getName().equals("commit") || method.getName().equals("rollback")) {
						if (actual.getAutoCommit()) {
							invalid.incrementAndGet();
							throw new SQLException("Transaction operation while autoCommit=true", "25000");
						}
						(method.getName().equals("commit") ? commits : rollbacks).incrementAndGet();
					}
					try {
						return method.invoke(actual, args);
					} catch (java.lang.reflect.InvocationTargetException failure) {
						throw failure.getCause();
					}
				});
	}

	private static BulkMigrationJobPlan plan() {
		final var table = new Table("ITEMS").setSchemaName("PUBLIC");
		final var id = new Column("ID").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(id);
		table.setPrimaryKey(id);
		for (int value : List.of(1, 2)) {
			final var row = table.newRow();
			row.put("ID", value);
			table.getRows().add(row);
		}
		return BulkMigrationJobPlanner.plan(List.of(BulkMigrationJobTask.builder().taskId("items").sourceTable(table)
				.requireEmptyTarget(true).options(ChunkedBulkMigrationOption.builder().migrationId("items")
						.mode(BulkMigrationMode.INSERT).resume(false).chunkSize(1).build())
				.build()));
	}

	private static JDBCDataSource database() {
		final var value = new JDBCDataSource();
		value.setUrl("jdbc:hsqldb:mem:transaction_owner_" + UUID.randomUUID());
		value.setUser("SA");
		value.setPassword("");
		return value;
	}
}

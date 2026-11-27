/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferDirectSchemaTest {
	@TempDir
	Path directory;
	private final TransferJobExecutionService service = new TransferJobExecutionService();

	@Test
	void missingTargetSchemaNamesTheRequestedSchemaAndAvailableChoicesWithoutCreatingAnything() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		sql(to, "CREATE SCHEMA ARCHIVE AUTHORIZATION DBA");
		final var failure = assertThrows(TransferSchemaPreparation.ValidationException.class, () -> service
				.review(job("[ITEMS]", "targetSchema: archive\n"), Map.of("from", from, "to", to), directory));
		assertTrue(failure.getMessage().contains("Target schema 'archive'"));
		assertTrue(failure.getMessage().contains("'ARCHIVE'"));
		assertTrue(failure.getMessage().contains("exact name/case"));
		assertTrue(failure.getMessage().contains("owner and privileges"));
		assertFalse(failure.getMessage().contains("jdbc:"));
		assertFalse(exists(to, "ARCHIVE", "ITEMS"));
		assertFalse(exists(to, "PUBLIC", "ITEMS"));
	}

	@Test
	void missingSchemaSuggestionsAreBoundedAndDeterministic() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		for (int index = 11; index >= 0; index--)
			sql(to, "CREATE SCHEMA S" + String.format("%02d", index) + " AUTHORIZATION DBA");
		final var failure = assertThrows(TransferSchemaPreparation.ValidationException.class, () -> service
				.review(job("[ITEMS]", "targetSchema: MISSING\n"), Map.of("from", from, "to", to), directory));
		final var names = failure.getMessage().substring(failure.getMessage().indexOf("Existing schemas"));
		assertTrue(names.contains("'S00'"));
		assertFalse(names.contains("'S11'"));
		assertTrue(names.split(",").length <= 10);
	}

	@Test
	void reviewsMissingTablesWithoutWritesThenCreatesForeignKeysAndVerifiesTheCopy() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40) DEFAULT 'item')",
				"CREATE TABLE ORDERS(ID INTEGER NOT NULL PRIMARY KEY, ITEM_ID INTEGER REFERENCES ITEMS(ID))",
				"INSERT INTO ITEMS(ID) VALUES(1)", "INSERT INTO ORDERS VALUES(10,1)");
		sql(to, "CREATE SCHEMA ARCHIVE AUTHORIZATION DBA");
		final var bindings = Map.of("from", from, "to", to);
		final var review = service.review(job("[ORDERS, ITEMS]", "targetSchema: ARCHIVE\n"), bindings, directory);
		final var plan = review.prepared().schemaPreparation();
		assertFalse(plan.statements().isEmpty());
		assertFalse(plan.warnings().isEmpty());
		assertEquals("transfer-table-2", plan.statements().getFirst().taskId());
		assertFalse(exists(to, "ARCHIVE", "ITEMS"));
		final var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertTrue(result.verificationResult().isMatch());
		assertEquals(2, result.copyResult().getProcessedRows());
		assertEquals(plan.statements().size(), result.schemaPreparation().completedStatements());
		assertNull(result.schemaPreparation().attemptedStatement());
		assertEquals(1, count(to, "ARCHIVE.ITEMS"));
		assertEquals(1, count(to, "ARCHIVE.ORDERS"));
		assertThrows(SQLException.class, () -> sql(to, "INSERT INTO ARCHIVE.ORDERS VALUES(20,999)"));
		final var report = new TransferJobReportIO().writeOutcome(review, result);
		assertTrue(Files.readString(report).contains("schemaPreparation"));
		assertFalse(Files.readString(report).contains("jdbc:"));
	}

	@Test
	void changedMissingSetRejectsBeforeCreatingAnyAdditionalTable() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE OTHER(ID INTEGER NOT NULL PRIMARY KEY)");
		final var bindings = Map.of("from", from, "to", to);
		final var review = service.review(job("[ITEMS, OTHER]", ""), bindings, directory);
		sql(to, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
		assertFalse(exists(to, "PUBLIC", "OTHER"));
		assertEquals(0, count(to, "ITEMS"));
	}

	@Test
	void existingNonemptyTableBlocksReviewBeforeAnyDdl() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE OTHER(ID INTEGER NOT NULL PRIMARY KEY)");
		sql(to, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		assertThrows(Exception.class,
				() -> service.review(job("[OTHER, ITEMS]", ""), Map.of("from", from, "to", to), directory));
		assertFalse(exists(to, "PUBLIC", "OTHER"));
		assertEquals(1, count(to, "ITEMS"));
	}

	@Test
	void ddlFailureRetainsCompletedAndUncertainStatementsWithoutStartingCopy() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)",
				"CREATE TABLE OTHER(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		final var ddlCount = new AtomicInteger();
		final var faulty = faulty(to, ddlCount, null);
		final var bindings = Map.of("from", from, "to", faulty);
		final var review = service.review(job("[ITEMS, OTHER]", ""), bindings, directory);
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.SCHEMA_PREPARATION, failure.outcome().phase());
		assertEquals(1, failure.outcome().schemaPreparation().completedStatements());
		assertEquals(2, failure.outcome().schemaPreparation().attemptedStatement());
		assertNull(failure.outcome().copyResult());
		assertTrue(failure.outcome().committedProgress().isEmpty());
		assertEquals(0, count(to, "ITEMS"));
		assertFalse(exists(to, "PUBLIC", "OTHER"));
		assertNull(failure.getCause());
	}

	@Test
	void copyFailureAfterDdlKeepsSchemaAndCommittedRowsInTheOutcome() throws Exception {
		final var from = database();
		final var to = database();
		sql(from, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
		final var faulty = faulty(to, null, new AtomicInteger());
		final var bindings = Map.of("from", from, "to", faulty);
		final var review = service.review(job("[ITEMS]", ""), bindings, directory);
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.COPY, failure.outcome().phase());
		assertEquals(1, failure.outcome().schemaPreparation().completedStatements());
		assertEquals(1, count(to, "ITEMS"));
		assertFalse(failure.outcome().committedProgress().isEmpty());
	}

	private Path job(String tables, String extra) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\nschemaMode: CREATE_MISSING\nchunkSize: 1\ntables: "
						+ tables + "\n" + extra);
	}

	private static DataSource faulty(JDBCDataSource actual, AtomicInteger ddl, AtomicInteger inserts) {
		return new JDBCDataSource() {
			@Override
			public Connection getConnection() throws SQLException {
				return wrap(actual.getConnection(), Connection.class, ddl, inserts);
			}
		};
	}

	private static <T> T wrap(T actual, Class<T> type, AtomicInteger ddl, AtomicInteger inserts) {
		return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(proxy, method, args) -> {
					if (ddl != null && method.getName().equals("execute") && args != null
							&& args[0] instanceof String sql && sql.startsWith("CREATE") && ddl.incrementAndGet() == 2)
						throw new SQLException("Synthetic DDL failure", "42000");
					if (inserts != null && method.getName().equals("executeBatch") && inserts.incrementAndGet() == 2)
						throw new SQLException("Synthetic insert failure", "23505");
					try {
						final var value = method.invoke(actual, args);
						if (value instanceof PreparedStatement statement)
							return wrap(statement, PreparedStatement.class, ddl, inserts);
						if (value instanceof Statement statement)
							return wrap(statement, Statement.class, ddl, inserts);
						return value;
					} catch (java.lang.reflect.InvocationTargetException failure) {
						throw failure.getCause();
					}
				}));
	}

	private static JDBCDataSource database() {
		final var ds = new JDBCDataSource();
		ds.setUrl("jdbc:hsqldb:mem:direct_schema_" + UUID.randomUUID());
		ds.setUser("SA");
		ds.setPassword("");
		return ds;
	}

	private static void sql(JDBCDataSource ds, String... statements) throws SQLException {
		try (var c = ds.getConnection(); var s = c.createStatement()) {
			for (var statement : statements)
				s.execute(statement);
		}
	}

	private static int count(JDBCDataSource ds, String table) throws SQLException {
		try (var c = ds.getConnection();
				var s = c.createStatement();
				var r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
			r.next();
			return r.getInt(1);
		}
	}

	private static boolean exists(JDBCDataSource ds, String schema, String table) throws SQLException {
		try (var c = ds.getConnection();
				var r = c.getMetaData().getTables(null, schema, table, new String[] { "TABLE" })) {
			return r.next();
		}
	}
}

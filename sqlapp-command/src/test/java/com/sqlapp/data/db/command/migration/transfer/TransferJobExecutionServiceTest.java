/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes copies only between disposable embedded HSQL databases. */
class TransferJobExecutionServiceTest {
	@TempDir
	Path directory;
	private final TransferJobExecutionService service = new TransferJobExecutionService();

	@Test
	void executesReviewedDefinitionAndVerifiesWithoutClosingCallerDataSourcesOrAllowingAutomaticReplay()
			throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))",
				"INSERT INTO ITEMS VALUES(1,'one'),(2,'two')");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, NAME VARCHAR(40))");
		final var bindings = Map.of("from", source, "to", target);
		final var review = service.review(job(""), bindings, directory);
		assertEquals(0, count(target));
		final var result = service.execute(review, bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertEquals(2, result.copyResult().getProcessedRows());
		assertTrue(result.verificationResult().isMatch());
		assertEquals(review.prepared().planFingerprint(), result.copyResult().getPlanFingerprint());
		assertEquals(1, result.completedTasks().size());
		assertEquals(2, result.committedProgress().get("transfer-table-1").getProcessedRowsAfter());
		assertEquals(2, count(source));
		assertEquals(2, count(target));
		assertFalse(source.closed);
		assertFalse(target.closed);
		final var replay = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.REVALIDATION, replay.outcome().phase());
		assertEquals(2, count(target));
	}

	@Test
	void changedDefinitionAndArtifactsRejectBeforeCopy() throws Exception {
		for (String kind : new String[] { "definition", "copy", "schema", "saved" }) {
			final var source = database();
			final var target = database();
			sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
			sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
			final var bindings = Map.of("from", source, "to", target);
			final var review = service.review(job(""), bindings, directory);
			final Path changed = switch (kind) {
			case "definition" -> review.definitionFile();
			case "copy" -> review.prepared().dataJob().file();
			case "schema" -> review.prepared().sourceSchemaFile();
			default -> review.prepared().job().file();
			};
			Files.writeString(changed,
					Files.readString(changed) + (kind.equals("schema") ? "\n<!-- edited -->" : "\n# edited"));
			final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
					() -> service.execute(review, bindings), kind);
			assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
			assertTrue(failure.outcome().committedProgress().isEmpty());
			assertEquals(0, count(target));
		}
	}

	@Test
	void changedRuntimeBindingAndSourceMetadataRejectBeforeCopyWithoutExposingDriverSecrets() throws Exception {
		final var source = database();
		final var target = database();
		final var other = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		sql(other, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var bindings = Map.of("from", source, "to", target);
		final var review = service.review(job(""), bindings, directory);
		assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, Map.of("from", source, "to", other)));
		assertEquals(0, count(other));
		final var borrowed = new java.util.concurrent.atomic.AtomicInteger();
		final var changedAfterPreparation = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws SQLException {
				return borrowed.incrementAndGet() == 1 ? target.getConnection() : other.getConnection();
			}
		};
		assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, Map.of("from", source, "to", changedAfterPreparation)));
		assertTrue(borrowed.get() >= 2);
		assertEquals(0, count(other));
		assertEquals(0, count(target));
		final var broken = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws SQLException {
				throw new SQLException("secret-password jdbc:private");
			}
		};
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, Map.of("from", source, "to", broken)));
		assertFalse(failure.toString().contains("secret-password"));
		assertFalse(failure.toString().contains("jdbc:private"));
		assertNull(failure.getCause());
		sql(source, "ALTER TABLE ITEMS ADD EXTRA INTEGER");
		assertThrows(TransferJobExecutionService.ExecutionException.class, () -> service.execute(review, bindings));
		assertEquals(0, count(target));
	}

	@Test
	void copyFailureRetainsCommittedChunkProgressAndDoesNotClaimRollback() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1),(2)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY CHECK(ID < 2))");
		final var bindings = Map.of("from", source, "to", target);
		final var review = service.review(job(""), bindings, directory);
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.COPY, failure.outcome().phase());
		assertEquals("transfer-table-1", failure.outcome().stoppedTaskId());
		assertEquals(1, failure.outcome().committedProgress().get("transfer-table-1").getProcessedRowsAfter());
		assertTrue(failure.outcome().completedTasks().isEmpty());
		assertEquals(1, count(target));
		assertEquals(2, count(source));
	}

	@Test
	void verificationMismatchRetainsCompletedCopyAndFailsTheOverallInvocation() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, AMOUNT DECIMAL(10,2))",
				"INSERT INTO ITEMS VALUES(1,1.25)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY, AMOUNT DECIMAL(10,0))");
		final var bindings = Map.of("from", source, "to", target);
		final var review = service.review(job(""), bindings, directory);
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, bindings));
		assertEquals(TransferJobExecutionService.Phase.VERIFICATION, failure.outcome().phase());
		assertEquals(1, failure.outcome().copyResult().getProcessedRows());
		assertNotNull(failure.outcome().verificationResult());
		assertFalse(failure.outcome().verificationResult().isMatch());
		assertEquals(1, count(target));
	}

	@Test
	void connectionReleaseFailureAfterSuccessfulVerificationIsFinalizationNotRollback() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var review = service.review(job(""), Map.of("from", source, "to", target), directory);
		final var borrowed = new java.util.concurrent.atomic.AtomicInteger();
		final var closeFails = new JDBCDataSource() {
			@Override
			public java.sql.Connection getConnection() throws SQLException {
				final var connection = target.getConnection();
				if (borrowed.incrementAndGet() == 1)
					return connection; // Fresh read-only preparation closes normally.
				return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
						java.sql.Connection.class.getClassLoader(), new Class<?>[] { java.sql.Connection.class },
						(proxy, method, arguments) -> {
							if (method.getName().equals("close")) {
								connection.close();
								throw new SQLException("Synthetic connection release failure");
							}
							try {
								return method.invoke(connection, arguments);
							} catch (java.lang.reflect.InvocationTargetException failure) {
								throw failure.getCause();
							}
						});
			}
		};
		final var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
				() -> service.execute(review, Map.of("from", source, "to", closeFails)));
		assertEquals(TransferJobExecutionService.Phase.FINALIZATION, failure.outcome().phase());
		assertEquals(1, failure.outcome().copyResult().getProcessedRows());
		assertTrue(failure.outcome().verificationResult().isMatch());
		assertEquals(1, count(target));
	}

	@Test
	void explicitVerificationOptOutIsRetainedInOutcome() throws Exception {
		final var source = database();
		final var target = database();
		sql(source, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)", "INSERT INTO ITEMS VALUES(1)");
		sql(target, "CREATE TABLE ITEMS(ID INTEGER NOT NULL PRIMARY KEY)");
		final var bindings = Map.of("from", source, "to", target);
		final var result = service.execute(service.review(job("verify: false\n"), bindings, directory), bindings);
		assertEquals(TransferJobExecutionService.Phase.COMPLETE, result.phase());
		assertNull(result.verificationResult());
		assertFalse(result.prepared().job().configuration().verify());
	}

	private Path job(String options) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"), "formatVersion: 1\nsource: from\ntarget: to\n"
				+ "tables: [ITEMS]\nschemaMode: REQUIRE_EXISTING\nchunkSize: 1\n" + options);
	}

	private static final class OwnedDataSource extends JDBCDataSource implements AutoCloseable {
		boolean closed;

		@Override
		public void close() {
			closed = true;
		}
	}

	private static OwnedDataSource database() {
		final var source = new OwnedDataSource();
		source.setUrl("jdbc:hsqldb:mem:transfer_execute_" + UUID.randomUUID());
		source.setUser("SA");
		source.setPassword("");
		return source;
	}

	private static void sql(JDBCDataSource source, String... statements) throws Exception {
		try (var connection = source.getConnection(); var statement = connection.createStatement()) {
			for (String sql : statements)
				statement.execute(sql);
		}
	}

	private static int count(JDBCDataSource source) throws Exception {
		try (var connection = source.getConnection();
				var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM ITEMS")) {
			rows.next();
			return rows.getInt(1);
		}
	}
}

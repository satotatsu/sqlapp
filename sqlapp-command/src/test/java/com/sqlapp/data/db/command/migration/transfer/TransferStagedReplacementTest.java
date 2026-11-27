/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransferStagedReplacementTest {
	@TempDir
	Path directory;

	private JDBCDataSource db() {
		var db = new JDBCDataSource();
		db.setUrl("jdbc:hsqldb:mem:staged_" + UUID.randomUUID());
		db.setUser("SA");
		return db;
	}

	private void sql(JDBCDataSource db, String... texts) throws Exception {
		try (var c = db.getConnection(); var s = c.createStatement()) {
			for (String text : texts)
				s.execute(text);
		}
	}

	private long count(JDBCDataSource db, String query) throws Exception {
		try (var c = db.getConnection(); var s = c.createStatement(); var r = s.executeQuery(query)) {
			assertTrue(r.next());
			return r.getLong(1);
		}
	}

	private Path job(String extra) throws Exception {
		return Files.writeString(directory.resolve("transfer.yaml"),
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [CHILD, PARENT]\nschemaMode: CREATE_MISSING\nchunkSize: 1\ntargetPreparation: STAGED_REPLACE\n"
						+ extra);
	}

	private void source(JDBCDataSource db) throws Exception {
		sql(db, "CREATE TABLE PARENT(ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))",
				"CREATE TABLE CHILD(PARENT_ID INTEGER REFERENCES PARENT(ID), TXT VARCHAR(20))",
				"INSERT INTO PARENT VALUES(1,'new'),(2,NULL)", "INSERT INTO CHILD VALUES(1,''),(1,''),(2,NULL)");
	}

	private void occupied(JDBCDataSource db, boolean check) throws Exception {
		sql(db, "CREATE TABLE PARENT(ID INTEGER NOT NULL PRIMARY KEY, TXT VARCHAR(20))",
				"CREATE TABLE CHILD(PARENT_ID INTEGER REFERENCES PARENT(ID), TXT VARCHAR(20)"
						+ (check ? " CHECK(TXT <> '')" : "") + ")",
				"INSERT INTO PARENT VALUES(99,'old')", "INSERT INTO CHILD VALUES(99,'old')");
	}

	@ParameterizedTest
	@ValueSource(strings = { "LOCKS", "MVLOCKS" })
	void replacesOccupiedRelatedTablesAndCleansStages(String transactionMode) throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			occupied(to, false);
			sql(to, "SET DATABASE TRANSACTION CONTROL " + transactionMode);
			var service = new TransferJobExecutionService();
			var bindings = Map.of("from", from, "to", to);
			var review = service.review(job("insertMethod: JDBC_BATCH\n"), bindings, directory);
			assertEquals(1, count(to, "SELECT COUNT(*) FROM PARENT WHERE ID=99"));
			var outcome = service.execute(review, bindings);
			assertEquals(TransferStagedReplacement.State.COMMITTED, outcome.staging().state());
			assertTrue(outcome.verificationResult().isMatch());
			assertNull(outcome.copyResult());
			assertEquals(0, count(to, "SELECT COUNT(*) FROM PARENT WHERE ID=99"));
			assertEquals(3, count(to, "SELECT COUNT(*) FROM CHILD"));
			assertEquals(2, outcome.staging().cleanedStages().size());
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@Test
	void finalInsertFailureRollsBackAllDeletesAndRetainsStages() throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			occupied(to, true);
			var service = new TransferJobExecutionService();
			var bindings = Map.of("from", from, "to", to);
			var review = service.review(job(""), bindings, directory);
			var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
					() -> service.execute(review, bindings));
			assertEquals(TransferStagedReplacement.State.ROLLED_BACK, failure.outcome().staging().state());
			assertTrue(failure.outcome().staging().appliedRows().isEmpty());
			assertEquals("CHILD",
					review.staging().targets().stream()
							.filter(target -> target.taskId().equals(failure.outcome().stoppedTaskId())).findFirst()
							.orElseThrow().table());
			assertEquals(1, count(to, "SELECT COUNT(*) FROM PARENT WHERE ID=99"));
			assertEquals(1, count(to, "SELECT COUNT(*) FROM CHILD WHERE PARENT_ID=99"));
			assertNotNull(failure.outcome().staging().stageOutcome().verificationResult());
			var retained = assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> service.review(review.definitionFile(), bindings, directory));
			assertTrue(retained.getMessage().contains("staging table for this job already exists"));
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@Test
	void rejectsUnselectedIncomingCascadeBeforeWrites() throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			occupied(to, false);
			sql(to, "CREATE TABLE OUTSIDE(ID INTEGER REFERENCES PARENT(ID) ON DELETE CASCADE)",
					"INSERT INTO OUTSIDE VALUES(99)");
			var service = new TransferJobExecutionService();
			assertThrows(IllegalArgumentException.class,
					() -> service.review(job(""), Map.of("from", from, "to", to), directory));
			assertEquals(1, count(to, "SELECT COUNT(*) FROM OUTSIDE"));
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@Test
	void detectsNewExternalReferenceBeforeWritingAnyStagingRows() throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			occupied(to, false);
			var service = new TransferJobExecutionService();
			var bindings = Map.of("from", from, "to", to);
			var review = service.review(job(""), bindings, directory);
			sql(to, "CREATE TABLE OUTSIDE(ID INTEGER REFERENCES PARENT(ID) ON DELETE CASCADE)",
					"INSERT INTO OUTSIDE VALUES(99)");

			var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
					() -> service.execute(review, bindings));
			assertEquals(TransferJobExecutionService.Phase.REVALIDATION, failure.outcome().phase());
			assertEquals(TransferFailureDiagnostic.Kind.REPLACEMENT_TARGET_CHANGED,
					failure.outcome().failureDiagnostic().kind());
			assertNull(failure.outcome().staging().stageOutcome());
			assertEquals(1, count(to, "SELECT COUNT(*) FROM OUTSIDE WHERE ID=99"));
			assertEquals(1, count(to, "SELECT COUNT(*) FROM PARENT WHERE ID=99"));
			assertEquals(0,
					count(to, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'SQLAPP_STG_%'"));
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void missingCommitAcknowledgmentPreservesEvidenceRegardlessOfServerOutcome(boolean serverCommitted)
			throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			occupied(to, false);
			var service = new TransferJobExecutionService();
			var review = service.review(job(""), Map.of("from", from, "to", to), directory);
			var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
					() -> service.execute(review, Map.of("from", from, "to", lostCommitReply(to, serverCommitted))));

			var outcome = failure.outcome();
			assertEquals(TransferStagedReplacement.State.COMMIT_UNCERTAIN, outcome.staging().state());
			assertTrue(outcome.verificationResult().isMatch());
			assertTrue(outcome.staging().appliedRows().isEmpty());
			assertTrue(outcome.staging().cleanedStages().isEmpty());
			assertEquals("08006", outcome.failureDiagnostic().sqlState());
			assertTrue(failure.getMessage().contains("Commit was attempted but not confirmed"));
			assertFalse(failure.getMessage().contains("private driver detail"));
			assertEquals(serverCommitted ? 0 : 1, count(to, "SELECT COUNT(*) FROM PARENT WHERE ID=99"));
			assertEquals(serverCommitted ? 3 : 1, count(to, "SELECT COUNT(*) FROM CHILD"));
			assertEquals(2,
					count(to, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'SQLAPP_STG_%'"));
			var snapshot = new TransferJobReportIO().writeOutcomeSnapshot(review, outcome);
			var report = snapshot.file();
			assertEquals("sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(Files.readAllBytes(report)),
					snapshot.fingerprint());
			assertTrue(Files.readString(report).contains("COMMIT_UNCERTAIN"));
			assertFalse(Files.readString(report).contains("private driver detail"));
			String recordedReport = Files.readString(report);
			new TransferJobReportIO().writeReview(review);
			var comparison = TransferStagedReplacement.compareRetainedStage(review, outcome, to);
			assertEquals(comparison, TransferStagedReplacement.compareRetainedStage(report, to));
			var reportComparison = TransferStagedReplacement.compareRetainedStageReport(report, to);
			assertEquals(snapshot.fingerprint(), reportComparison.sourceResultFingerprint());
			assertEquals(comparison, reportComparison.comparison());
			assertEquals(2, comparison.tables().size());
			for (var table : comparison.tables()) {
				boolean parent = table.target().table().equals("PARENT");
				assertEquals(parent ? 2 : 3, table.stagingRows());
				assertEquals(serverCommitted ? table.stagingRows() : 1, table.targetRows());
				assertEquals(serverCommitted, table.matchesTarget());
				assertTrue(table.matchesRecordedStaging());
			}
			assertEquals(recordedReport, Files.readString(report));
			assertEquals(2,
					count(to, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'SQLAPP_STG_%'"));
			if (serverCommitted) {
				var parent = comparison.tables().stream().filter(t -> t.target().table().equals("PARENT")).findFirst()
						.orElseThrow();
				sql(to, "UPDATE \"" + parent.target().stageTable() + "\" SET TXT='edited' WHERE ID=1",
						"UPDATE PARENT SET TXT='edited' WHERE ID=1");
				var edited = TransferStagedReplacement.compareRetainedStage(review, outcome, to).tables().stream()
						.filter(t -> t.target().table().equals("PARENT")).findFirst().orElseThrow();
				assertTrue(edited.matchesTarget());
				assertFalse(edited.matchesRecordedStaging());
			}
			var relocated = Files.createDirectory(directory.resolve("relocated"));
			for (var name : List.of("transfer-result.json", "transfer-review.json", "transfer.yaml", "data-job.yaml",
					"source-schema.xml"))
				Files.copy(report.getParent().resolve(name), relocated.resolve(name));
			Files.delete(review.definitionFile());
			var relocatedResult = relocated.resolve("transfer-result.json");
			var restored = TransferStagedReplacement.compareRetainedStage(relocatedResult, to);
			assertEquals(serverCommitted,
					restored.tables().stream().allMatch(TransferStagedReplacement.TableComparison::matchesTarget));
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> TransferStagedReplacement.compareRetainedStage(relocatedResult, from));

			var forbiddenConnection = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
					new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
						throw new AssertionError("A mismatched saved bundle should be rejected before connecting");
					});
			var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
			var saved = (tools.jackson.databind.node.ObjectNode) mapper.readTree(recordedReport);
			var differentIdentity = (tools.jackson.databind.node.ObjectNode) saved.path("targetIdentity");
			differentIdentity.put("connectionFingerprint", review.sourceIdentity().connectionFingerprint());
			Files.writeString(relocatedResult, mapper.writeValueAsString(saved));
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> TransferStagedReplacement.compareRetainedStage(relocatedResult, forbiddenConnection));
			saved.remove("targetIdentity");
			Files.writeString(relocatedResult, mapper.writeValueAsString(saved));
			assertEquals(restored, TransferStagedReplacement.compareRetainedStage(relocatedResult, to));

			Files.writeString(relocated.resolve("data-job.yaml"), "\n# modified\n", StandardOpenOption.APPEND);
			assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> TransferStagedReplacement.compareRetainedStage(relocatedResult, forbiddenConnection));
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	static DataSource lostCommitReply(DataSource delegate, boolean serverCommitted) {
		return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
				new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
					Object value = call(delegate, method, args);
					if (!(value instanceof Connection connection))
						return value;
					var replacement = new AtomicBoolean();
					return Proxy.newProxyInstance(Connection.class.getClassLoader(),
							new Class<?>[] { Connection.class }, (wrapped, operation, arguments) -> {
								if (operation.getName().equals("commit") && replacement.get()) {
									if (serverCommitted)
										call(connection, operation, arguments);
									throw new SQLException("private driver detail", "08006");
								}
								Object result = call(connection, operation, arguments);
								if (!(result instanceof Statement statement)
										|| !operation.getName().equals("createStatement"))
									return result;
								return Proxy.newProxyInstance(Statement.class.getClassLoader(),
										new Class<?>[] { Statement.class }, (wrappedStatement, command, parameters) -> {
											if (command.getName().equals("execute")
													&& parameters[0] instanceof String sql
													&& sql.startsWith("LOCK TABLE "))
												replacement.set(true);
											return call(statement, command, parameters);
										});
							});
				});
	}

	private static Object call(Object delegate, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(delegate, args);
		} catch (InvocationTargetException failure) {
			throw failure.getCause();
		}
	}

	@Test
	void createsMissingRelatedTargetsAfterVerifiedStaging() throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			var service = new TransferJobExecutionService();
			var bindings = Map.of("from", from, "to", to);
			var outcome = service.execute(service.review(job(""), bindings, directory), bindings);
			assertEquals(TransferStagedReplacement.State.COMMITTED, outcome.staging().state());
			assertEquals(2, count(to, "SELECT COUNT(*) FROM PARENT"));
			assertEquals(3, count(to, "SELECT COUNT(*) FROM CHILD"));
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@Test
	void stagingFailurePreservesTargetRows() throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			occupied(to, false);
			var service = new TransferJobExecutionService();
			var bindings = Map.of("from", from, "to", to);
			var review = service.review(job(""), bindings, directory);
			var stage = review.staging().targets().getFirst().stageTable();
			sql(to, "CREATE TABLE \"" + stage + "\"(ID INTEGER)");
			var failure = assertThrows(TransferJobExecutionService.ExecutionException.class,
					() -> service.execute(review, bindings));
			assertEquals(TransferStagedReplacement.State.STAGING_FAILED, failure.outcome().staging().state());
			assertEquals(1, count(to, "SELECT COUNT(*) FROM PARENT WHERE ID=99"));
			assertEquals(1, count(to, "SELECT COUNT(*) FROM CHILD WHERE PARENT_ID=99"));
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}

	@Test
	void rejectsUnverifiedConfigurations() {
		var io = new TransferJobConfigurationIO();
		assertThrows(IllegalArgumentException.class, () -> io.validate(
				"formatVersion: 1\nsource: from\ntarget: to\ntables: [T]\nschemaMode: CREATE_MISSING\ntargetPreparation: STAGED_REPLACE\nverify: false\n"));
	}

	@Test
	void sharedFinalizationRejectsUnsafeApplyAndCleanupBeforeBorrowingATarget() throws Exception {
		var forbiddenTarget = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
				new Class<?>[] { DataSource.class }, (proxy, method, args) -> {
					throw new AssertionError("An unsafe attempt must be rejected before borrowing a write connection");
				});
		var execution = new TransferStagedReplacement.Finalization(
				new TransferSchemaPreparation.Plan("unused", List.of()));
		assertThrows(IllegalStateException.class, () -> TransferStagedReplacement.applyVerifiedStage(forbiddenTarget,
				null, null, null, null, null, () -> {
				}, execution));
		execution.state = TransferStagedReplacement.State.COMMIT_UNCERTAIN;
		assertThrows(IllegalStateException.class,
				() -> TransferStagedReplacement.cleanupStages(forbiddenTarget, null, execution));
	}

	@Test
	void savedSchemaRejectsDoctypeEvenWhenItsFingerprintMatches() throws Exception {
		var schema = directory.resolve("source-schema.xml");
		byte[] xml = "<!DOCTYPE schema SYSTEM \"file:///unused-external-schema.dtd\"><schema/>"
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Files.write(schema, xml);
		String fingerprint = "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(xml);
		assertThrows(IllegalArgumentException.class, () -> SavedStagingComparison.checkSchema(schema, fingerprint));
	}

	@Test
	void rejectsMvccTargetWithoutTouchingRows() throws Exception {
		var from = db();
		var to = db();
		try {
			source(from);
			occupied(to, false);
			sql(to, "SET DATABASE TRANSACTION CONTROL MVCC");
			var failure = assertThrows(TransferSchemaPreparation.ValidationException.class,
					() -> new TransferJobExecutionService().review(job(""), Map.of("from", from, "to", to), directory));
			assertTrue(failure.getMessage().contains("MVCC"));
			assertEquals(1, count(to, "SELECT COUNT(*) FROM PARENT WHERE ID=99"));
		} finally {
			try {
				sql(from, "SHUTDOWN");
			} finally {
				sql(to, "SHUTDOWN");
			}
		}
	}
}

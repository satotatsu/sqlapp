/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfiguration;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationResolver;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReportIO;
import com.sqlapp.data.db.command.migration.bulk.FileBulkMigrationCheckpointStore;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.util.MessageDigests;
import com.sqlapp.util.YamlConverter;

/** Only isolated in-memory HSQLDB databases are accessed. */
class DataMigrationExecutionServiceTest {
	@TempDir
	Path directory;
	private final String sourceUrl = "jdbc:hsqldb:mem:data_source_" + UUID.randomUUID();
	private final String targetUrl = "jdbc:hsqldb:mem:data_target_" + UUID.randomUUID();
	private final DataMigrationPlanningService service = new DataMigrationPlanningService();
	private final DataMigrationExecutionService executor = new DataMigrationExecutionService();
	private BulkMigrationJobConfiguration configuration;
	private Path job;

	@BeforeEach
	void initialize() throws Exception {
		execute(sourceUrl, "CREATE TABLE ITEMS(ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
		execute(sourceUrl, "INSERT INTO ITEMS VALUES(1, 'source')");
		execute(targetUrl, "CREATE TABLE CUSTOMERS(CUSTOMER_ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
		final var schema = new Schema("PUBLIC");
		final var table = table("ITEMS");
		schema.getTables().add(table);
		schema.writeXml(directory.resolve("schema.xml").toFile());
		configuration = new BulkMigrationJobConfiguration();
		configuration.setJobId("desktop-data-plan");
		configuration.setSchemaFile("schema.xml");
		configuration.setSchemaFingerprint(
				"sha256:" + MessageDigests.SHA256.checksumAsString(directory.resolve("schema.xml").toFile()));
		final var task = task("items", "PUBLIC.ITEMS");
		task.setTargetTable("PUBLIC.CUSTOMERS");
		task.setColumnMappings(Map.of("ID", "CUSTOMER_ID"));
		configuration.setTasks(List.of(task));
		job = directory.resolve("job.yaml");
		writeJob();
	}

	@Test
	void executesReviewedMappingsWithDatabaseLeaseAndSavesStandardAudit() throws Exception {
		final var lease = new BulkMigrationJobConfiguration.Lease();
		lease.setMode(com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode.DATABASE);
		lease.setOwnerId("desktop-test");
		configuration.setLease(lease);
		final var verification = new BulkMigrationJobConfiguration.Verification();
		verification.setTargetFile("verification.json");
		configuration.setVerification(verification);
		writeJob();
		final var reviewed = plan();
		assertNull(DataMigrationExecutionService.restriction(reviewed));
		final var result = run(reviewed);
		assertTrue(result.successful(), result.review().summary());
		assertEquals(1, result.execution().processedRows());
		assertTrue(result.review().summary().contains("Verification: MATCH"));
		assertEquals(1, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS WHERE CUSTOMER_ID=1 AND NAME='source'"));
		final Path audit = directory.resolve("execution.json");
		executor.save(result, audit);
		assertEquals(result.execution(),
				new com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReportIO().read(audit));
		assertTrue(Files.exists(directory.resolve("verification.json")));
		assertThrows(IllegalArgumentException.class, () -> executor.save(result, directory.resolve("audit.txt")));
	}

	@Test
	void verificationFailureAfterCopyPreservesSuccessfulCopyAuditAndReportsFailure() throws Exception {
		final var verification = new BulkMigrationJobConfiguration.Verification();
		verification.setTargetFile("verification.json");
		configuration.setVerification(verification);
		writeJob();
		execute(targetUrl, "INSERT INTO CUSTOMERS VALUES(99, 'extra')");
		final var result = run(plan());
		assertFalse(result.successful());
		assertNotNull(result.execution(), result.review().summary());
		assertTrue(result.review().summary().contains("Verification: MISMATCH"));
		assertNotNull(result.verificationResult());
		assertTrue(result.review().details().contains("actualRows"));
		assertTrue(result.review().summary().contains("Operation failed"));
		assertEquals(2, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	@Test
	void pausesAtDurableDatabaseCheckpointAndResumesAfterFreshInspection() throws Exception {
		pauseAndResume(BulkMigrationCheckpointMode.DATABASE);
	}

	@Test
	void pausesAtSavedFileCheckpointAndResumesAfterFreshInspection() throws Exception {
		pauseAndResume(BulkMigrationCheckpointMode.FILE);
	}

	private void pauseAndResume(BulkMigrationCheckpointMode mode) throws Exception {
		final var task = configuration.getTasks().getFirst();
		task.setChunkSize(1);
		task.setMode(com.sqlapp.jdbc.bulk.BulkMigrationMode.INSERT);
		task.setCheckpointMode(mode);
		if (mode == BulkMigrationCheckpointMode.FILE)
			task.setCheckpointDirectory("checkpoints");
		writeJob();
		execute(sourceUrl, "INSERT INTO ITEMS VALUES(2, 'second'), (3, 'third')");
		final var reviewed = plan();
		assertNull(reviewed.executionSnapshot().pauseRestriction());
		final var control = new DataMigrationExecutionControl();
		// A pending user request must be honored at the first durable boundary.
		control.configurePause(true);
		control.jobListener().onTaskStarted("items", 0, 1);
		assertTrue(control.requestPause());
		final var paused = executor.execute(reviewed, spec(sourceUrl), new char[0], spec(targetUrl), new char[0],
				control);
		assertTrue(paused.paused(), paused.review().summary());
		assertFalse(paused.successful());
		assertTrue(paused.review().summary().contains("Operation paused"));
		assertTrue(paused.review().details().contains("lastProgress"));
		assertEquals(1, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
		assertEquals(1, control.progress().checkpointRows());
		assertEquals(1, control.progress().completedChunks());
		assertFalse(control.progress().pauseAvailable());
		final Path audit = directory.resolve("paused.json");
		executor.save(paused, audit);
		assertEquals("PAUSED", new com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReportIO()
				.read(audit, null).status());
		final var current = plan();
		assertEquals(BulkMigrationJobTaskState.IN_PROGRESS, current.report().tasks().getFirst().state());
		final var resumed = run(current);
		assertTrue(resumed.successful(), resumed.review().summary());
		assertEquals(2, resumed.execution().processedRows());
		assertEquals(3, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	@Test
	void nonresumableAndUnsupportedUpsertJobsExplainWhyPauseIsUnavailable() throws Exception {
		assertNotNull(plan().executionSnapshot().pauseRestriction());
		configuration.getTasks().getFirst()
				.setDuplicateKeyStrategy(com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy.KEEP_LAST);
		writeJob();
		assertNull(plan().executionSnapshot().pauseRestriction());
		configuration.getTasks().getFirst().setResume(false);
		writeJob();
		assertNotNull(plan().executionSnapshot().pauseRestriction());
		final var control = new DataMigrationExecutionControl();
		assertFalse(control.requestPause());
		final var result = executor.execute(plan(), spec(sourceUrl), new char[0], spec(targetUrl), new char[0],
				control);
		assertTrue(result.successful(), result.review().summary());
		assertFalse(control.pauseRequested());
	}

	@Test
	void changedConfigurationIsRejectedBeforeWrites() throws Exception {
		final var reviewed = plan();
		configuration.getTasks().getFirst().setChunkSize(20);
		writeJob();
		final var result = run(reviewed);
		assertFalse(result.successful());
		assertFalse(result.hasAudit());
		assertTrue(result.operationFailure().contains("fingerprint"));
		assertEquals(0, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	@Test
	void changedSchemaIsRejectedBeforeWrites() throws Exception {
		final var reviewed = plan();
		Files.writeString(directory.resolve("schema.xml"), "changed");
		final var result = run(reviewed);
		assertFalse(result.successful());
		assertTrue(result.operationFailure().contains("fingerprint"));
		assertEquals(0, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	@Test
	void differentTargetIsRejectedBeforeWrites() throws Exception {
		final var reviewed = plan();
		final String other = "jdbc:hsqldb:mem:other_" + UUID.randomUUID();
		execute(other, "CREATE TABLE CUSTOMERS(CUSTOMER_ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
		final var result = executor.execute(reviewed, spec(sourceUrl), new char[0], spec(other), new char[0]);
		assertFalse(result.successful());
		assertTrue(result.operationFailure().contains("identity"));
		assertEquals(0, count(other, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	@Test
	void partialFailureRetainsCommittedChunksAndFailureAudit() throws Exception {
		configuration.getTasks().getFirst().setChunkSize(1);
		configuration.getTasks().getFirst()
				.setDuplicateKeyStrategy(com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy.KEEP_LAST);
		writeJob();
		execute(sourceUrl, "INSERT INTO ITEMS VALUES(2, 'fails')");
		execute(targetUrl, "ALTER TABLE CUSTOMERS ADD CONSTRAINT REJECT_TWO CHECK(CUSTOMER_ID <> 2)");
		final var result = run(plan());
		assertFalse(result.successful());
		assertNotNull(result.failure(), result.review().summary());
		assertEquals(1, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
		assertTrue(result.review().summary().contains("Committed chunks are retained"));
		final Path audit = directory.resolve("failure.json");
		executor.save(result, audit);
		assertEquals(result.failure(),
				new com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReportIO().read(audit, null));
		assertFalse(plan().report().tasks().isEmpty());
		execute(targetUrl, "ALTER TABLE CUSTOMERS DROP CONSTRAINT REJECT_TWO");
		final var resumed = run(plan());
		assertTrue(resumed.successful(), resumed.review().summary());
		assertEquals(1, resumed.execution().processedRows());
		assertEquals(2, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	@Test
	void requiresPinnedSchemaAndClearsSecretsOnRejectedExecution() throws Exception {
		configuration.setSchemaFingerprint(null);
		writeJob();
		final var reviewed = plan();
		assertTrue(DataMigrationExecutionService.restriction(reviewed).contains("schemaFingerprint"));
		final char[] source = "source-secret".toCharArray();
		final char[] target = "target-secret".toCharArray();
		final var result = executor.execute(reviewed, spec(sourceUrl), source, spec(targetUrl), target);
		assertFalse(result.successful());
		assertArrayEquals(new char[source.length], source);
		assertArrayEquals(new char[target.length], target);
		assertThrows(IllegalArgumentException.class, () -> executor.save(result, directory.resolve("none.json")));
	}

	@Test
	void approvalArtifactsRemainRequiredAndAreNotBypassed() throws Exception {
		final var provenance = new BulkMigrationJobConfiguration.Provenance();
		provenance.setAssessmentReportFingerprint("sha256:" + "a".repeat(64));
		configuration.setProvenance(provenance);
		writeJob();
		final var reviewed = plan();
		assertTrue(DataMigrationExecutionService.restriction(reviewed).contains("approval"));
		assertFalse(run(reviewed).successful());
		assertEquals(0, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	private DataMigrationExecutionService.Outcome run(DataMigrationPlanningService.Result reviewed) {
		return executor.execute(reviewed, spec(sourceUrl), new char[0], spec(targetUrl), new char[0]);
	}

	private DataMigrationPlanningService.Result plan() {
		return service.plan(new DataMigrationPlanningService.Request(job, null), spec(sourceUrl), new char[0],
				spec(targetUrl), new char[0]);
	}

	private static PlanningJdbc.Spec spec(final String url) {
		return new PlanningJdbc.Spec(url, "SA", "");
	}

	private static Table table(final String name) {
		final var table = new Table(name);
		table.getColumns().add(new Column("ID").setDataType(DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("NAME").setDataType(DataType.VARCHAR).setLength(30));
		table.setPrimaryKey("PK_" + name, table.getColumns().get("ID"));
		return table;
	}

	private static BulkMigrationJobConfiguration.Task task(final String id, final String table) {
		final var task = new BulkMigrationJobConfiguration.Task();
		task.setId(id);
		task.setTable(table);
		task.setChunkSize(10);
		task.setSourceFingerprint("source-v1");
		task.setTargetFingerprint("target-v1");
		return task;
	}

	private BulkMigrationCheckpoint checkpoint(final String id, final String fingerprint) {
		return BulkMigrationCheckpoint.builder().migrationId(id).sourceFingerprint(fingerprint)
				.targetFingerprint("target-v1").processedRows(1).completedChunks(1).chunkSize(10).lastChunkHash("hash")
				.resumeToken("token").build().validate();
	}

	private void writeJob() throws Exception {
		new YamlConverter().writeJsonValue(job.toFile(), configuration);
	}

	private static void execute(final String url, final String sql) throws Exception {
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static long count(final String url, final String sql) throws Exception {
		try (var connection = DriverManager.getConnection(url, "SA", "");
				var statement = connection.createStatement();
				var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next());
			return rows.getLong(1);
		}
	}
}

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
class DataMigrationPlanningServiceTest {
	@TempDir
	Path directory;
	private final String sourceUrl = "jdbc:hsqldb:mem:data_source_" + UUID.randomUUID();
	private final String targetUrl = "jdbc:hsqldb:mem:data_target_" + UUID.randomUUID();
	private final DataMigrationPlanningService service = new DataMigrationPlanningService();
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
		final var task = task("items", "PUBLIC.ITEMS");
		task.setTargetTable("PUBLIC.CUSTOMERS");
		task.setColumnMappings(Map.of("ID", "CUSTOMER_ID"));
		configuration.setTasks(List.of(task));
		job = directory.resolve("job.yaml");
		writeJob();
	}

	@Test
	void resolvesMappingsAndReadsStateWithoutCopyingRowsOrCreatingControlTables() throws Exception {
		final byte[] original = Files.readAllBytes(job);
		final var result = plan();
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED, result.report().tasks().getFirst().state());
		assertTrue(result.report().compatible());
		assertEquals("PUBLIC.ITEMS", result.review().rows().getFirst().get(2));
		assertEquals("PUBLIC.CUSTOMERS", result.review().rows().getFirst().get(3));
		assertTrue(result.review().rows().getFirst().get(8).contains("CUSTOMER_ID"));
		assertTrue(result.review().summary().contains("No rows were migrated"));
		assertEquals(List.of(0, 5), result.review().numericColumns());
		assertEquals("sha256:" + MessageDigests.SHA256.checksumAsString(job.toFile()),
				result.report().provenance().configurationFingerprint());
		try (var source = DriverManager.getConnection(sourceUrl, "SA", "")) {
			assertEquals(new BulkMigrationJobConfigurationResolver().resolveJob(job.toFile(), source).plan()
					.getFingerprint(), result.report().planFingerprint());
		}
		assertEquals(1, count(sourceUrl, "SELECT COUNT(*) FROM ITEMS"));
		assertEquals(0, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
		assertEquals(1, count(targetUrl, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'"));
		assertArrayEquals(original, Files.readAllBytes(job));
	}

	@Test
	void readsExistingDatabaseCheckpointsAndReportsIncompatibilityWithoutResettingThem() throws Exception {
		final var checkpoint = checkpoint("items", "source-v1");
		try (var connection = DriverManager.getConnection(targetUrl, "SA", "")) {
			new JdbcBulkMigrationCheckpointStore(connection, "SQLAPP_BULK_MIGRATION_CHECKPOINT").save(checkpoint);
		}
		assertEquals(BulkMigrationJobTaskState.IN_PROGRESS, plan().report().tasks().getFirst().state());
		configuration.getTasks().getFirst().setSourceFingerprint("source-v2");
		writeJob();
		final var incompatible = plan();
		assertFalse(incompatible.report().compatible());
		assertEquals(BulkMigrationJobTaskState.INCOMPATIBLE, incompatible.report().tasks().getFirst().state());
		try (var connection = DriverManager.getConnection(targetUrl, "SA", "")) {
			final var saved = JdbcBulkMigrationCheckpointStore.readOnly(connection, "SQLAPP_BULK_MIGRATION_CHECKPOINT")
					.load("items").orElseThrow();
			assertEquals(checkpoint, saved);
		}
	}

	@Test
	void fileCheckpointInspectionNeitherCreatesDirectoriesNorRewritesFiles() throws Exception {
		final var task = configuration.getTasks().getFirst();
		task.setCheckpointMode(BulkMigrationCheckpointMode.FILE);
		task.setCheckpointDirectory("checkpoints");
		writeJob();
		final Path checkpointDirectory = directory.resolve("checkpoints");
		assertEquals(BulkMigrationJobTaskState.NOT_STARTED, plan().report().tasks().getFirst().state());
		assertFalse(Files.exists(checkpointDirectory));
		new FileBulkMigrationCheckpointStore(checkpointDirectory).save(checkpoint("items", "source-v1"));
		final Path file;
		try (var files = Files.list(checkpointDirectory)) {
			file = files.findFirst().orElseThrow();
		}
		final byte[] before = Files.readAllBytes(file);
		assertEquals(BulkMigrationJobTaskState.IN_PROGRESS, plan().report().tasks().getFirst().state());
		assertArrayEquals(before, Files.readAllBytes(file));
	}

	@Test
	void delegatesForeignKeyDependencyOrderingToSharedPlanner() throws Exception {
		final var schema = new Schema("PUBLIC");
		final var parent = table("PARENTS");
		final var child = table("CHILDREN");
		child.getColumns().add(new Column("PARENT_ID").setDataType(DataType.INT).setNotNull(true));
		child.getConstraints().addForeignKeyConstraint("FK_CHILD_PARENT", child.getColumns().get("PARENT_ID"),
				parent.getColumns().get("ID"));
		schema.getTables().add(parent);
		schema.getTables().add(child);
		schema.writeXml(directory.resolve("schema.xml").toFile());
		execute(targetUrl, "CREATE TABLE PARENTS(ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30))");
		execute(targetUrl,
				"CREATE TABLE CHILDREN(ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(30), PARENT_ID INT NOT NULL)");
		configuration.setTasks(List.of(task("child", "PUBLIC.CHILDREN"), task("parent", "PUBLIC.PARENTS")));
		writeJob();
		assertEquals(List.of("parent", "child"),
				plan().report().tasks().stream().map(value -> value.taskId()).toList());
	}

	@Test
	void rejectsInvalidTargetsAndChangedConfiguration() throws Exception {
		configuration.getTasks().getFirst().setTargetTable("PUBLIC.MISSING");
		writeJob();
		assertTrue(assertThrows(IllegalArgumentException.class, this::plan).getMessage().contains("target table"));
		final var request = new DataMigrationPlanningService.Request(job, "sha256:" + "0".repeat(64));
		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> service.plan(request, spec(sourceUrl), new char[0], spec(targetUrl), new char[0])).getMessage()
				.contains("fingerprint"));
		assertEquals(0, count(targetUrl, "SELECT COUNT(*) FROM CUSTOMERS"));
	}

	@Test
	void savesStandardReportWithoutChangingSnapshotOrOverwritingConfiguration() throws Exception {
		// YAML also accepts JSON files; this must not allow overwriting the selected
		// input.
		job = directory.resolve("job.json");
		writeJob();
		final var result = plan();
		final Path output = directory.resolve("status.json");
		service.save(result, output);
		assertEquals(result.report(), new BulkMigrationOperationalReportIO().read(output));
		assertEquals(result.report().generatedAt(), new BulkMigrationOperationalReportIO().read(output).generatedAt());
		assertThrows(IllegalArgumentException.class, () -> service.save(result, job));
		assertThrows(IllegalArgumentException.class, () -> service.save(result, directory.resolve("schema.xml")));
		assertFalse(Files.readString(output).contains(sourceUrl));
		assertTrue(new ReportReviewService().read(output, ReportReviewService.Type.BULK_OPERATIONAL).summary()
				.contains("desktop-data-plan"));
	}

	@Test
	void validatesInputsAndClearsBothPasswordsOnConnectionSetupFailure() {
		assertThrows(IllegalArgumentException.class,
				() -> new DataMigrationPlanningService.Request(directory.resolve("missing"), null));
		assertThrows(IllegalArgumentException.class, () -> new DataMigrationPlanningService.Request(job, "invalid"));
		final char[] sourcePassword = "source_secret".toCharArray();
		final char[] targetPassword = "target_secret".toCharArray();
		final var error = assertThrows(IllegalArgumentException.class,
				() -> service.plan(new DataMigrationPlanningService.Request(job, null),
						new PlanningJdbc.Spec(sourceUrl, "SA", "missing.Driver"), sourcePassword, spec(targetUrl),
						targetPassword));
		assertArrayEquals(new char[sourcePassword.length], sourcePassword);
		assertArrayEquals(new char[targetPassword.length], targetPassword);
		assertFalse(error.getMessage().contains("source_secret"));
		assertFalse(error.getMessage().contains("target_secret"));
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

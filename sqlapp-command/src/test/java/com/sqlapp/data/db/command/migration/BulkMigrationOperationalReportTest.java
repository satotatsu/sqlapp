/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLifecycle;
import com.sqlapp.jdbc.bulk.BulkMigrationJobOperation;
import com.sqlapp.jdbc.bulk.BulkMigrationJobOperationPhase;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlanner;
import com.sqlapp.jdbc.bulk.BulkMigrationJobStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTask;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationProgressSnapshot;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.util.JsonConverter;

class BulkMigrationOperationalReportTest {
	@TempDir
	Path directory;

	@Test
	@SuppressWarnings("unchecked")
	void buildsAndAtomicallyWritesStableJsonSnapshot() {
		final Instant generatedAt = Instant.parse("2026-08-31T10:00:00Z");
		final BulkMigrationJobPlan plan = plan();
		final BulkMigrationCheckpoint checkpoint = BulkMigrationCheckpoint.builder()
				.migrationId("顧客移行").sourceFingerprint("source")
				.targetFingerprint("target").processedRows(25).completedChunks(1)
				.chunkSize(100).lastChunkHash("abc123").resumeToken("[25]").build();
		final BulkMigrationJobStatus status = new BulkMigrationJobStatus(
				plan.getFingerprint(), List.of(new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.IN_PROGRESS, checkpoint)));
		final BulkMigrationMaintenanceState maintenance = new BulkMigrationMaintenanceState(
				plan.getFingerprint(), BulkMigrationMaintenanceStatus.PREPARED,
				generatedAt.minusSeconds(1), null);
		final BulkMigrationProgressSnapshot progress = new BulkMigrationProgressSnapshot(
				"顧客移行", 25, 100L, Duration.ofSeconds(5), 5.0, 0.25,
				Duration.ofSeconds(15));
		final var report = new BulkMigrationOperationalReportBuilder(
				Clock.fixed(generatedAt, ZoneOffset.UTC)).build(plan, status, maintenance, progress);
		final Path output = directory.resolve("nested").resolve("status.json");

		new BulkMigrationOperationalReportIO().write(output, report);

		final Map<String, Object> json = new JsonConverter().fromJsonString(
				output.toFile(), Map.class);
		assertEquals(1, ((Number) json.get("formatVersion")).intValue());
		assertEquals(plan.getFingerprint(), json.get("planFingerprint"));
		assertEquals(25, ((Number) json.get("processedRows")).longValue());
		assertTrue((Boolean) json.get("compatible"));
		final List<Map<String, Object>> jsonTasks =
				(List<Map<String, Object>>) json.get("tasks");
		assertEquals("顧客移行", jsonTasks.get(0).get("migrationId"));
		assertEquals("CUSTOMERS", jsonTasks.get(0).get("tableName"));
		assertEquals("UPSERT", jsonTasks.get(0).get("mode"));
		final Map<String, Object> jsonCheckpoint =
				(Map<String, Object>) jsonTasks.get(0).get("checkpoint");
		assertEquals("source", jsonCheckpoint.get("sourceFingerprint"));
		final Map<String, Object> jsonProgress = (Map<String, Object>) json.get("progress");
		assertEquals("顧客移行", jsonProgress.get("migrationId"));
		assertEquals(15_000,
				((Number) jsonProgress.get("estimatedRemainingMillis")).longValue());
		final List<Map<String, Object>> operations =
				(List<Map<String, Object>>) json.get("operations");
		assertEquals("DISABLE_CONSTRAINTS", operations.get(0).get("id"));
		assertEquals("BEFORE", operations.get(0).get("phase"));
	}

	@Test
	void rejectsStatusAndMaintenanceFromAnotherPlan() {
		final BulkMigrationJobPlan plan = plan();
		final BulkMigrationJobStatus wrongStatus = new BulkMigrationJobStatus("other",
				List.of());
		final var builder = new BulkMigrationOperationalReportBuilder();
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, wrongStatus, null, null));

		final BulkMigrationJobStatus status = new BulkMigrationJobStatus(
				plan.getFingerprint(), List.of());
		final var wrongMaintenance = new BulkMigrationMaintenanceState("other",
				BulkMigrationMaintenanceStatus.PREPARED, Instant.now(), null);
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, status, wrongMaintenance, null));
	}

	@Test
	void rejectsForeignTasksCheckpointsAndProgress() {
		final BulkMigrationJobPlan plan = plan();
		final var builder = new BulkMigrationOperationalReportBuilder();
		assertThrows(IllegalArgumentException.class, () -> builder.build(plan,
				new BulkMigrationJobStatus(plan.getFingerprint(), List.of()), null, null));

		final var foreignCheckpoint = BulkMigrationCheckpoint.builder()
				.migrationId("other").processedRows(0).build();
		final var status = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, foreignCheckpoint)));
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, status, null, null));

		final var correctStatus = new BulkMigrationJobStatus(plan.getFingerprint(), List.of(
				new BulkMigrationJobTaskStatus("customers",
						BulkMigrationJobTaskState.NOT_STARTED, null)));
		final var foreignProgress = new BulkMigrationProgressSnapshot("other", 0, null,
				Duration.ZERO, 0, null, null);
		assertThrows(IllegalArgumentException.class,
				() -> builder.build(plan, correctStatus, null, foreignProgress));
	}

	private static BulkMigrationJobPlan plan() {
		final Table table = new Table("CUSTOMERS");
		table.getColumns().add(new Column("ID"));
		table.setPrimaryKey("PK_CUSTOMERS", table.getColumns().get("ID"));
		final var task = BulkMigrationJobTask.builder().taskId("customers")
				.sourceTable(table).options(ChunkedBulkMigrationOption.builder()
						.migrationId("顧客移行").sourceFingerprint("source")
						.targetFingerprint("target").build()).build();
		final BulkMigrationJobLifecycle lifecycle = new BulkMigrationJobLifecycle() {
			@Override
			public String getConfigurationFingerprint() {
				return "test-lifecycle-v1";
			}

			@Override
			public List<BulkMigrationJobOperation> plan(List<BulkMigrationJobTask> tasks) {
				return List.of(new BulkMigrationJobOperation("DISABLE_CONSTRAINTS",
						BulkMigrationJobOperationPhase.BEFORE, "Disable constraints", true));
			}
		};
		return BulkMigrationJobPlanner.plan(List.of(task), lifecycle);
	}
}

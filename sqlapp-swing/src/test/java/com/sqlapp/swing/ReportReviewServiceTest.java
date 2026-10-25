/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobExecutionReportIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobFailureReportIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReportIO;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReport;
import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReport;
import com.sqlapp.data.db.command.migration.schema.MigrationExecutionReportIO;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan;
import com.sqlapp.data.db.command.migration.schema.MigrationPlanIO;
import com.sqlapp.data.db.command.migration.schema.Status;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobTaskState;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.util.JsonConverter;

class ReportReviewServiceTest {
	@TempDir
	Path directory;
	private final ReportReviewService service = new ReportReviewService();

	@Test
	void readsSchemaPlanWithBlockersPoliciesRepeatablesAndFingerprint() throws Exception {
		final var entry = new MigrationPlan.Entry(20, "add_customer", "20.sql", 1, false, true, false, "checksum");
		final var repeatable = new MigrationPlan.RepeatableEntry("R__view.sql", "R__view.sql", 2, true, "new", "old");
		final var plan = new MigrationPlan(true, 10L, 20L, 0, 0, List.of(entry),
				List.of(new MigrationPlan.HistoryIssue(15, Status.Errored)), null, null, List.of(), true, true, true,
				new MigrationPlan.DatabaseIdentity("HSQLDB", "2.7", "db-fingerprint"), List.of(repeatable));
		final Path file = directory.resolve("plan.json");
		new MigrationPlanIO().write(file, plan);
		final String original = Files.readString(file);
		final var review = service.read(file, ReportReviewService.Type.SCHEMA_PLAN);
		assertTrue(review.summary().contains("KNOWN BLOCKERS PRESENT"));
		assertTrue(review.summary().contains("require rollback: true"));
		assertTrue(review.summary().contains("sha256:"));
		assertEquals(2, review.rows().size());
		assertEquals(List.of(2), review.numericColumns());
		assertEquals("false", review.rows().get(0).get(3));
		assertTrue(review.details().contains("db-fingerprint"));
		assertEquals(original, Files.readString(file));
	}

	@Test
	void distinguishesPartialSchemaExecutionAndNoExecution() {
		final Path file = directory.resolve("schema-execution.json");
		final long now = System.currentTimeMillis();
		final var report = new MigrationExecutionReport(3, now, now, false, true, null, "plan", List.of(10L, 20L),
				List.of(10L), null);
		new MigrationExecutionReportIO().write(file, report);
		final var review = service.read(file, ReportReviewService.Type.SCHEMA_EXECUTION);
		assertTrue(review.summary().contains("FAILED"));
		assertEquals("COMMITTED", review.rows().get(0).get(1));
		assertEquals("NOT RECORDED AS COMMITTED", review.rows().get(1).get(1));

		new MigrationExecutionReportIO().write(file,
				new MigrationExecutionReport(3, now, now, true, false, null, null, List.of(), List.of(), null));
		assertTrue(service.read(file, ReportReviewService.Type.SCHEMA_EXECUTION).summary()
				.contains("NO EXECUTION REQUESTED"));
	}

	@Test
	void readsOperationalSnapshotWithoutConfusingMissingCheckpointWithZero() {
		final var task = new BulkMigrationOperationalReport.Task("customer", "customer-migration", null, "PUBLIC",
				"CUSTOMER", BulkMigrationMode.UPSERT, 100, BulkMigrationCheckpointMode.DATABASE,
				BulkMigrationJobTaskState.NOT_STARTED, null);
		final var report = new BulkMigrationOperationalReport(3, Instant.now(), "job", "plan", true, 0, 0, 1,
				List.of(task), List.of(), null, null, List.of(), null);
		final Path file = directory.resolve("operational.json");
		new BulkMigrationOperationalReportIO().write(file, report);
		final var review = service.read(file, ReportReviewService.Type.BULK_OPERATIONAL);
		assertTrue(review.summary().contains("Completed tasks: 0/1"));
		assertEquals("NOT_STARTED", review.rows().getFirst().get(4));
		assertEquals("not recorded", review.rows().getFirst().get(6));
		assertEquals(List.of(6), review.numericColumns());
	}

	@Test
	void showsInvocationRowsSeparatelyFromPreviouslyCommittedRows() {
		final Path file = directory.resolve("execution.json");
		final var report = new BulkMigrationJobExecutionReport(1, Instant.now(), "job", "plan", 15, 0,
				List.of(new BulkMigrationJobExecutionReport.Task("customer", 10, 15, 2, false)), null);
		new BulkMigrationJobExecutionReportIO().writeSnapshot(file, report, null);
		final var review = service.read(file, ReportReviewService.Type.BULK_EXECUTION);
		assertTrue(review.summary().contains("COMPLETED"));
		assertTrue(review.summary().contains("invocation: 15"));
		assertEquals(List.of("customer", "10", "15", "2", "false"), review.rows().getFirst());
		assertEquals(List.of(1, 2, 3), review.numericColumns());
	}

	@Test
	void preservesFailedAndPausedOutcomesAndCompletedPrefix() {
		final Path file = directory.resolve("failure.json");
		for (final String outcome : List.of("FAILED", "PAUSED")) {
			final var report = new BulkMigrationJobFailureReport(1, Instant.now(), outcome, "job", "plan", "orders",
					"example.Failure", "failure detail",
					List.of(new BulkMigrationJobExecutionReport.Task("customer", 0, 5, 1, false)), null);
			new BulkMigrationJobFailureReportIO().writeSnapshot(file, report, null);
			final var review = service.read(file, ReportReviewService.Type.BULK_FAILURE);
			assertTrue(review.summary().contains(outcome));
			assertTrue(review.summary().contains("Stopped task: orders"));
			assertTrue(review.summary().contains("failure detail"));
			assertTrue(review.summary().contains("committed chunks may exist"));
			assertEquals("customer", review.rows().getFirst().getFirst());
			assertEquals(List.of(1, 2, 3), review.numericColumns());
		}
	}

	@Test
	void showsVerificationMismatchAndRetainsChunkEvidence() throws Exception {
		final var chunk = new BulkMigrationVerificationReport.Chunk(0, 2, 1, "expected", "actual", null, null, null,
				null);
		final var task = new BulkMigrationVerificationReport.Task("customer", List.of("ID"), null, null, false, 2, 1, 1,
				List.of(chunk));
		final var report = new BulkMigrationVerificationReport(6, Instant.now(), "plan", "DEFAULT", false, 2, 1, 1,
				List.of(task));
		final Path file = json("verification.json", report);
		final var review = service.read(file, ReportReviewService.Type.BULK_VERIFICATION);
		assertTrue(review.summary().contains("MISMATCH"));
		assertEquals(List.of("customer", "MISMATCH", "2", "1", "1", "1"), review.rows().getFirst());
		assertTrue(review.details().contains("expectedHash"));
		assertTrue(review.details().contains("expected"));
	}

	@Test
	void distinguishesTruncatedChunkEvidenceFromTotalMismatchCount() throws Exception {
		final var chunk = new BulkMigrationVerificationReport.Chunk(12, 2, 1, "expected", "actual", "[21]", "[22]",
				"[21]", "[21]");
		final var task = new BulkMigrationVerificationReport.Task("customer", List.of("ID"), null, null, false, 10, 8,
				3, List.of(chunk));
		final var report = new BulkMigrationVerificationReport(6, Instant.now(), "plan", "DEFAULT", false, 10, 8, 1,
				List.of(task));
		final var direct = service.verification(report);
		final var saved = service.read(json("truncated.json", report), ReportReviewService.Type.BULK_VERIFICATION);
		assertEquals(direct, saved);
		assertTrue(saved.summary().contains("1 of 3; omitted: 2"));
		assertEquals(List.of("customer", "MISMATCH", "10", "8", "3", "1"), saved.rows().getFirst());
		final var details = saved.tables().getFirst();
		assertEquals("Mismatch chunks", details.title());
		assertEquals(List.of(1, 2, 3), details.numericColumns());
		assertEquals(List.of("customer", "12", "2", "1", "expected", "actual", "[21]", "[22]", "[21]", "[21]"),
				details.rows().getFirst());
		assertTrue(details.summary().contains("zero-based"));
	}

	@Test
	void showsDifferentKeysetFingerprintsWithoutOverridingSharedMatchStatus() throws Exception {
		final var task = new BulkMigrationVerificationReport.Task("customer", List.of("ID"), "expected-keyset",
				"actual-keyset", true, 2, 2, 0, List.of());
		final var report = new BulkMigrationVerificationReport(6, Instant.now(), "plan", "DEFAULT", true, 2, 2, 0,
				List.of(task));
		final var review = service.read(json("keysets.json", report), ReportReviewService.Type.BULK_VERIFICATION);
		assertEquals("MATCH", review.rows().getFirst().get(1));
		assertTrue(review.summary().contains("Keyset fingerprints differ"));
		assertTrue(review.tables().getFirst().rows().isEmpty());
		assertTrue(review.tables().getFirst().summary().contains("none omitted"));
	}

	@Test
	void matchingReportShowsEmptyEvidenceWithoutMissingDetailsWarning() {
		final var task = new BulkMigrationVerificationReport.Task("customer", List.of("ID"), null, null, true, 2, 2, 0,
				List.of());
		final var report = new BulkMigrationVerificationReport(6, Instant.now(), "plan", "DEFAULT", true, 2, 2, 0,
				List.of(task));
		final var review = service.verification(report);
		assertTrue(review.summary().contains("0 of 0; none omitted"));
		assertTrue(review.tables().getFirst().rows().isEmpty());
		assertFalse(review.summary().contains("Keyset fingerprints differ"));
	}

	@Test
	void rejectsMissingMalformedNullWrongTypeAndUnsupportedReports() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> service.read(directory.resolve("missing.json"), ReportReviewService.Type.SCHEMA_PLAN));
		final Path file = directory.resolve("invalid.json");
		for (final String text : List.of("{", "null", "{}", "{\"formatVersion\":999}")) {
			Files.writeString(file, text);
			for (final var type : ReportReviewService.Type.values()) {
				assertThrows(RuntimeException.class, () -> service.read(file, type), type.toString() + ": " + text);
			}
		}
		final var execution = new BulkMigrationJobExecutionReport(1, Instant.now(), "job", "plan", 0, 0, List.of(),
				null);
		new BulkMigrationJobExecutionReportIO().writeSnapshot(file, execution, null);
		assertThrows(RuntimeException.class, () -> service.read(file, ReportReviewService.Type.BULK_VERIFICATION));
	}

	@Test
	void rejectsTamperedPlanFingerprintAndInconsistentExecutionCounts() throws Exception {
		final Path file = directory.resolve("plan.json");
		new MigrationPlanIO().write(file, new MigrationPlan(false, null, 10L, 0, 0, List.of(), List.of(), null));
		Files.writeString(file, Files.readString(file).replace("sha256:", "changed:"));
		assertThrows(RuntimeException.class, () -> service.read(file, ReportReviewService.Type.SCHEMA_PLAN));
		final Path invalid = json("execution.json",
				new BulkMigrationJobExecutionReport(1, Instant.now(), "job", "plan", 100, 0, List.of(), null));
		assertThrows(RuntimeException.class, () -> service.read(invalid, ReportReviewService.Type.BULK_EXECUTION));
	}

	private Path json(final String name, final Object value) throws Exception {
		final Path file = directory.resolve(name);
		new JsonConverter().writeJsonValue(file.toFile(), value);
		return file;
	}
}

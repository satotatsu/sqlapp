/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sqlapp.data.db.command.migration.bulk.*;
import com.sqlapp.data.db.command.migration.schema.*;
import com.sqlapp.data.db.command.migration.transfer.TransferStagedReplacement;
import com.sqlapp.data.db.command.migration.transfer.TransferStagingComparisonReportIO;
import com.sqlapp.util.JsonConverter;

class ReportReviewAutoLoaderTest {
	@TempDir
	Path directory;
	private final ReportReviewAutoLoader loader = new ReportReviewAutoLoader();

	@Test
	void detectsAllFormatsAndUsesValidatedReviewsWithoutChangingSource() throws Exception {
		final var files = fixtures();
		for (var entry : files.entrySet()) {
			final byte[] original = Files.readAllBytes(entry.getValue());
			final Path misleadingName = directory.resolve("schema-plan.json");
			Files.write(misleadingName, original);
			final var detected = loader.read(misleadingName);
			assertEquals(entry.getKey(), detected.type());
			assertEquals(new ReportReviewService().read(entry.getValue(), entry.getKey()), detected.review());
			assertArrayEquals(original, Files.readAllBytes(entry.getValue()));
		}
	}

	@Test
	void stagingComparisonSeparatesTargetDifferencesFromStagingChanges() throws Exception {
		var file = fixtures().get(ReportReviewService.Type.STAGING_COMPARISON);
		var view = loader.read(file).review();
		assertTrue(view.summary().contains("Recorded commit: COMMIT_UNCERTAIN"));
		assertTrue(view.summary().contains("Target differences: 1. Staging changes: 1."));
		assertTrue(view.summary().contains("Staging data has changed since the recorded verification"));
		assertTrue(view.summary().contains("The prior commit remains unconfirmed"));
		assertEquals(3, view.rows().size());
		assertEquals(List.of(3, 4), view.numericColumns());
		var differences = view.tables().getFirst();
		assertEquals("Differences", differences.title());
		assertEquals(List.of("Different", "Unchanged"), differences.rows().get(0).subList(5, 7));
		assertEquals(List.of("Match", "Changed"), differences.rows().get(1).subList(5, 7));

		var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
		var json = mapper.readTree(Files.readString(file));
		var row = (tools.jackson.databind.node.ObjectNode) json.path("comparison").path("tables").get(0);
		row.remove("matchesTarget");
		Files.writeString(file, mapper.writeValueAsString(json));
		assertThrows(IllegalArgumentException.class, () -> loader.read(file));
	}

	@Test
	void stagingComparisonDistinguishesSameNamedTablesByCatalogAndTaskWithoutLosingNumericSort() throws Exception {
		var comparison = new TransferStagedReplacement.Comparison(List.of(
				new TransferStagedReplacement.TableComparison(
						new TransferStagedReplacement.Target("task-a", "catalog-a", "PUBLIC", "ITEMS", "STAGE_A"),
						12, 12, true, true),
				new TransferStagedReplacement.TableComparison(
						new TransferStagedReplacement.Target("task-b", "catalog-b", "PUBLIC", "ITEMS", "STAGE_B"),
						2, 1, false, true)));
		var hash = "sha256:" + "a".repeat(64);
		var report = new TransferStagingComparisonReportIO.Report(1, hash,
				TransferStagedReplacement.State.COMMIT_UNCERTAIN,
				new MigrationPlan.DatabaseIdentity("HSQL", "2.7", hash), comparison);
		var file = directory.resolve("comparison.json");
		new TransferStagingComparisonReportIO().write(file, report);
		var view = loader.read(file).review();
		assertEquals(List.of("Catalog", "Task ID"), view.columns().subList(7, 9));
		assertEquals(List.of("catalog-a", "task-a"), view.rows().getFirst().subList(7, 9));
		assertEquals(List.of("catalog-b", "task-b"), view.tables().getFirst().rows().getFirst().subList(7, 9));
		assertEquals(view.columns(), view.tables().getFirst().columns());
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			var panel = new ReportReviewView();
			panel.showReview(view);
			panel.tasks.getRowSorter().toggleSortOrder(3);
			assertEquals("task-b", panel.tasks.getValueAt(0, 8));
			panel.taskTable.filter.setText("catalog-a");
			assertEquals(1, panel.tasks.getRowCount());
			assertEquals("task-a", panel.tasks.getValueAt(0, 8));
			panel.taskTable.filter.setText("task-b");
			assertEquals(1, panel.tasks.getRowCount());
			assertEquals("catalog-b", panel.tasks.getValueAt(0, 7));
		});
	}

	@Test
	void rejectsAmbiguousUnknownMalformedAndMultipleJsonObjects() throws Exception {
		final Path file = directory.resolve("plan.json");
		for (String text : List.of("{}", "{\"formatVersion\":1}", "[]", "null", "{\"plan\":{},\"expectedRows\":0}",
				"{\"plan\":{},\"plan\":{}}", "{\"plan\":{}} {}", "{broken")) {
			Files.writeString(file, text);
			assertThrows(RuntimeException.class, () -> loader.read(file), text);
		}
		Files.writeString(file, "{\"plan\":{},\"completedAt\":null}");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> loader.read(file)).getMessage()
				.contains("Ambiguous"));
		Files.writeString(file, "{\"somethingElse\":true}");
		assertTrue(assertThrows(IllegalArgumentException.class, () -> loader.read(file)).getMessage()
				.contains("Choose format"));
	}

	@Test
	void rejectsBadVersionFingerprintUtf8AndOversizedFiles() throws Exception {
		final var files = fixtures();
		final Path plan = files.get(ReportReviewService.Type.SCHEMA_PLAN);
		final String original = Files.readString(plan);
		Files.writeString(plan,
				original.replaceFirst("\"planFingerprint\"\\s*:\\s*\"[^\"]+\"", "\"planFingerprint\":\"bad\""));
		assertThrows(RuntimeException.class, () -> loader.read(plan));
		final Path execution = files.get(ReportReviewService.Type.BULK_EXECUTION);
		Files.writeString(execution,
				Files.readString(execution).replaceFirst("\"formatVersion\"\\s*:\\s*1", "\"formatVersion\":999"));
		assertThrows(RuntimeException.class, () -> loader.read(execution));
		Files.write(plan, new byte[] { '{', '"', (byte) 0xC3, '(', '"', ':', '0', '}' });
		assertThrows(RuntimeException.class, () -> loader.read(plan));
		for (var encoding : List.of(java.nio.charset.StandardCharsets.UTF_16,
				java.nio.charset.StandardCharsets.UTF_16LE, java.nio.charset.StandardCharsets.UTF_16BE)) {
			Files.write(plan, original.getBytes(encoding));
			assertThrows(RuntimeException.class, () -> loader.read(plan));
		}
		try (var channel = Files.newByteChannel(plan, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
			channel.position(ReportReviewAutoLoader.MAX_BYTES);
			channel.write(java.nio.ByteBuffer.wrap(new byte[] { ' ' }));
		}
		assertThrows(RuntimeException.class, () -> loader.read(plan));
	}

	@Test
	void validatesCapturedBytesEvenIfOriginalChangesAndDeletesSnapshotOnFailure() throws Exception {
		final Path source = fixtures().get(ReportReviewService.Type.SCHEMA_PLAN);
		final byte[] original = Files.readAllBytes(source);
		final var snapshot = new AtomicReference<Path>();
		final var capture = new ReportReviewAutoLoader((file, type) -> {
			snapshot.set(file);
			assertNotEquals(source, file);
			try {
				assertArrayEquals(original, Files.readAllBytes(file));
				Files.writeString(source, "{}");
			} catch (java.io.IOException failure) {
				throw new RuntimeException(failure);
			}
			return new ReportReviewService().read(file, type);
		});
		assertEquals(ReportReviewService.Type.SCHEMA_PLAN, capture.read(source).type());
		assertFalse(Files.exists(snapshot.get()));
		assertEquals("{}", Files.readString(source));
		Files.write(source, original);
		final var failing = new ReportReviewAutoLoader((file, type) -> {
			snapshot.set(file);
			throw new IllegalStateException("Validation failed");
		});
		assertThrows(IllegalStateException.class, () -> failing.read(source));
		assertFalse(Files.exists(snapshot.get()));
	}

	private EnumMap<ReportReviewService.Type, Path> fixtures() throws Exception {
		final var files = new EnumMap<ReportReviewService.Type, Path>(ReportReviewService.Type.class);
		for (var type : ReportReviewService.Type.values())
			files.put(type, directory.resolve(type.name() + ".json"));
		final long now = System.currentTimeMillis();
		new MigrationPlanIO().write(files.get(ReportReviewService.Type.SCHEMA_PLAN),
				new MigrationPlan(false, null, 30L, 0, 0, List.of(), List.of(), null));
		new MigrationExecutionReportIO().write(files.get(ReportReviewService.Type.SCHEMA_EXECUTION),
				new MigrationExecutionReport(3, now, now, true, false, null, null, List.of(), List.of(), null));
		new BulkMigrationOperationalReportIO().write(files.get(ReportReviewService.Type.BULK_OPERATIONAL),
				new BulkMigrationOperationalReport(3, Instant.now(), "job", "plan", true, 0, 0, 0, List.of(), List.of(),
						null, null, List.of(), null));
		new BulkMigrationJobExecutionReportIO().writeSnapshot(files.get(ReportReviewService.Type.BULK_EXECUTION),
				new BulkMigrationJobExecutionReport(1, Instant.now(), "job", "plan", 0, 0, List.of(), null), null);
		new BulkMigrationJobFailureReportIO()
				.writeSnapshot(
						files.get(ReportReviewService.Type.BULK_FAILURE), new BulkMigrationJobFailureReport(1,
								Instant.now(), "PAUSED", "job", "plan", null, "Paused", "paused", List.of(), null),
						null);
		new BulkMigrationVerificationReportIO().write(files.get(ReportReviewService.Type.BULK_VERIFICATION),
				new BulkMigrationVerificationReport(6, Instant.now(), "plan", "DEFAULT", true, 0, 0, 0, List.of()));
		var comparisons = List.of(
				comparison("MATCHED", 7, 7, true, true),
				comparison("TARGET_DIFFERS", 12, 2, false, true),
				comparison("STAGING_CHANGED", 10, 10, true, false));
		new TransferStagingComparisonReportIO().write(files.get(ReportReviewService.Type.STAGING_COMPARISON),
				new TransferStagingComparisonReportIO.Report(1, "sha256:" + "a".repeat(64),
						TransferStagedReplacement.State.COMMIT_UNCERTAIN,
						new MigrationPlan.DatabaseIdentity("HSQL", "2.7", "sha256:" + "b".repeat(64)),
						new TransferStagedReplacement.Comparison(comparisons)));
		return files;
	}

	private static TransferStagedReplacement.TableComparison comparison(String name, long stagingRows, long targetRows,
			boolean matches, boolean stagingUnchanged) {
		return new TransferStagedReplacement.TableComparison(
				new TransferStagedReplacement.Target(name, null, "PUBLIC", name, "STG_" + name),
				stagingRows, targetRows, matches, stagingUnchanged);
	}
}

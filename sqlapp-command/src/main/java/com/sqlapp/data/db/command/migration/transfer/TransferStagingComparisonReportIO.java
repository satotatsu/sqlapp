/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared format for viewing a saved staging comparison without database access.
 */
public final class TransferStagingComparisonReportIO {
	private static final long MAX_BYTES = 16L * 1024 * 1024;
	private static final JsonMapper MAPPER = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
			.build();

	public record Report(int formatVersion, String sourceResultFingerprint,
			TransferStagedReplacement.State recordedCommitState, DatabaseIdentity targetIdentity,
			TransferStagedReplacement.Comparison comparison) {
		public Report {
			if (formatVersion != 1 || !fingerprint(sourceResultFingerprint)
					|| recordedCommitState != TransferStagedReplacement.State.COMMIT_UNCERTAIN || targetIdentity == null
					|| !fingerprint(targetIdentity.connectionFingerprint()) || !present(targetIdentity.productName())
					|| !present(targetIdentity.productVersion()) || comparison == null || comparison.tables().isEmpty())
				throw invalid();
			var tasks = new HashSet<String>();
			for (var row : comparison.tables()) {
				var target = row.target();
				if (target == null || !present(target.taskId()) || !present(target.schema()) || !present(target.table())
						|| !present(target.stageTable()) || !tasks.add(target.taskId()) || row.stagingRows() < 0
						|| row.targetRows() < 0 || row.matchesTarget() && row.stagingRows() != row.targetRows())
					throw invalid();
			}
		}
	}

	public Path write(Path file, Report report) throws IOException {
		return TransferJobReportIO.write(file,
				java.util.Map.of("formatVersion", report.formatVersion(), "sourceResultFingerprint",
						report.sourceResultFingerprint(), "recordedCommitState", report.recordedCommitState(),
						"targetIdentity", report.targetIdentity(), "comparison", report.comparison()));
	}

	/**
	 * Publishes in a fresh run directory so existing evidence cannot be
	 * overwritten.
	 */
	public Path writeNew(Path workspace, Report report) throws IOException {
		var directory = Files.createTempDirectory(Files.createDirectories(workspace.toAbsolutePath().normalize()),
				"comparison-");
		return write(directory.resolve("staging-comparison.json"), report);
	}

	public Report read(Path file) {
		try {
			var bytes = BoundedMigrationFile.read(file, MAX_BYTES, "16 MiB report limit", "Staging comparison report");
			String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
			var tree = MAPPER.readTree(text.startsWith("\uFEFF") ? text.substring(1) : text);
			var rows = tree.path("comparison").path("tables");
			if (!tree.path("formatVersion").isIntegralNumber() || !rows.isArray())
				throw invalid();
			for (var row : rows) {
				if (!row.path("stagingRows").isIntegralNumber() || !row.path("targetRows").isIntegralNumber()
						|| !row.path("matchesTarget").isBoolean() || !row.path("matchesRecordedStaging").isBoolean())
					throw invalid();
			}
			return MAPPER.treeToValue(tree, Report.class);
		} catch (IOException | RuntimeException failure) {
			throw invalid();
		}
	}

	private static boolean present(String value) {
		return value != null && !value.isBlank();
	}

	private static boolean fingerprint(String value) {
		return value != null && value.matches("sha256:[0-9a-f]{64}");
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("Select a valid version 1 staging-comparison.json (UTF-8, up to 16 MiB).");
	}
}

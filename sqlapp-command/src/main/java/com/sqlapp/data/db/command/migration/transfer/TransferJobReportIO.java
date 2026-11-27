/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;

/**
 * Shared versioned diagnostic reports; saved JSON is evidence, not execution
 * authorization.
 */
public final class TransferJobReportIO {
	public record SavedOutcome(Path file, String fingerprint) {
	}

	public Path writeReview(TransferJobExecutionService.Review review) throws IOException {
		return write(review.prepared().directory().resolve("transfer-review.json"), "review", review);
	}

	public Path writeOutcome(TransferJobExecutionService.Review review, TransferJobExecutionService.Outcome outcome)
			throws IOException {
		return writeOutcomeSnapshot(review, outcome).file();
	}

	public SavedOutcome writeOutcomeSnapshot(TransferJobExecutionService.Review review,
			TransferJobExecutionService.Outcome outcome) throws IOException {
		var file = review.prepared().directory().resolve("transfer-result.json");
		var document = outcome.staging() != null
				&& outcome.staging().state() == TransferStagedReplacement.State.COMMIT_UNCERTAIN
						? Map.of("formatVersion", 1, "outcome", outcome, "targetIdentity", review.targetIdentity())
						: Map.of("formatVersion", 1, "outcome", outcome);
		var bytes = new com.sqlapp.util.JsonConverter().toJsonString(document).getBytes(StandardCharsets.UTF_8);
		AtomicMigrationFile.write(file, temporary -> Files.write(temporary, bytes));
		return new SavedOutcome(file, "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
	}

	private static Path write(Path file, String kind, Object value) throws IOException {
		return write(file, Map.of("formatVersion", 1, kind, value));
	}

	static Path write(Path file, Map<String, Object> report) throws IOException {
		AtomicMigrationFile.write(file, temporary -> Files.writeString(temporary,
				new com.sqlapp.util.JsonConverter().toJsonString(report), java.nio.charset.StandardCharsets.UTF_8));
		return file;
	}
}

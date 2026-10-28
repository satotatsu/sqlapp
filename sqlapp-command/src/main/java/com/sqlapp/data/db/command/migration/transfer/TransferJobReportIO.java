/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import java.nio.file.*;
import java.io.IOException;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;

/**
 * Shared versioned diagnostic reports; saved JSON is evidence, not execution
 * authorization.
 */
public final class TransferJobReportIO {
	public Path writeReview(TransferJobExecutionService.Review review) throws IOException {
		return write(review.prepared().directory().resolve("transfer-review.json"), "review", review);
	}

	public Path writeOutcome(TransferJobExecutionService.Review review, TransferJobExecutionService.Outcome outcome)
			throws IOException {
		return write(review.prepared().directory().resolve("transfer-result.json"), "outcome", outcome);
	}

	private static Path write(Path file, String kind, Object value) throws IOException {
		AtomicMigrationFile.write(file,
				temporary -> Files.writeString(temporary,
						new com.sqlapp.util.JsonConverter()
								.toJsonString(java.util.Map.of("formatVersion", 1, kind, value)),
						java.nio.charset.StandardCharsets.UTF_8));
		return file;
	}
}

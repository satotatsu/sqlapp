/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Detects a bounded snapshot, then delegates validation to the existing typed
 * reader.
 */
final class ReportReviewAutoLoader {
	static final long MAX_BYTES = 16L * 1024 * 1024;
	private final java.util.function.BiFunction<Path, ReportReviewService.Type, ReportReviewService.Review> reader;

	ReportReviewAutoLoader() {
		this(new ReportReviewService()::read);
	}

	ReportReviewAutoLoader(
			java.util.function.BiFunction<Path, ReportReviewService.Type, ReportReviewService.Review> reader) {
		this.reader = Objects.requireNonNull(reader);
	}

	record Detected(ReportReviewService.Type type, ReportReviewService.Review review) {
		Detected {
			Objects.requireNonNull(type);
			Objects.requireNonNull(review);
		}
	}

	Detected read(Path file) {
		if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file))
			throw new IllegalArgumentException("Select a readable report JSON file.");
		try {
			final byte[] bytes = BoundedMigrationFile.read(file, MAX_BYTES, "16 MiB automatic detection limit",
					"Report JSON");
			final String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
					.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
			final var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
					.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
			final var json = mapper.readTree(text.startsWith("\uFEFF") ? text.substring(1) : text);
			if (json == null || !json.isObject())
				throw new IllegalArgumentException("The report must contain one JSON object.");
			final var candidates = new ArrayList<ReportReviewService.Type>();
			if (json.has("plan"))
				candidates.add(ReportReviewService.Type.SCHEMA_PLAN);
			if (json.has("startedAtEpochMillis") || json.has("finishedAtEpochMillis"))
				candidates.add(ReportReviewService.Type.SCHEMA_EXECUTION);
			if (json.has("compatible") || json.has("totalTasks"))
				candidates.add(ReportReviewService.Type.BULK_OPERATIONAL);
			if (json.has("completedAt") || json.has("alreadyCompleteTasks"))
				candidates.add(ReportReviewService.Type.BULK_EXECUTION);
			if (json.has("failedAt") || json.has("failureType"))
				candidates.add(ReportReviewService.Type.BULK_FAILURE);
			if (json.has("match") || json.has("expectedRows") || json.has("isolation"))
				candidates.add(ReportReviewService.Type.BULK_VERIFICATION);
			if (candidates.size() != 1)
				throw new IllegalArgumentException((candidates.isEmpty() ? "Unknown" : "Ambiguous")
						+ " report format. Use Choose format to select the intended reader explicitly.");
			final var type = candidates.getFirst();
			// Typed readers accept paths. Use one immutable captured file so classification
			// and validation cannot accidentally observe different revisions of the source.
			try (var snapshot = new Snapshot(Files.createTempFile("sqlapp-report-review-", ".json"))) {
				Files.write(snapshot.file(), bytes);
				return new Detected(type, reader.apply(snapshot.file(), type));
			}
		} catch (IOException failure) {
			throw new IllegalArgumentException("Unable to read report JSON: " + file, failure);
		}
	}

	private record Snapshot(Path file) implements AutoCloseable {
		@Override
		public void close() throws IOException {
			Files.deleteIfExists(file);
		}
	}
}

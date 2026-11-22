/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.util.JsonConverter;

/**
 * Outcome of one pipeline run, rewritten after every step. A failed step names
 * where the run stopped; the following steps are {@link Status#NOT_RUN}. A run
 * is not resumed: run the pipeline again from the start.
 *
 * @param formatVersion       1
 * @param runId               identifier of the run and name of its directory
 * @param pipelineFingerprint approved fingerprint of the pipeline inputs
 * @param status              overall status
 * @param steps               load, models and publish, in order
 */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record MigrationPipelineReport(int formatVersion, String runId, String pipelineFingerprint, Status status,
		List<StepReport> steps) {
	public enum Status {
		NOT_RUN, RUNNING, SUCCEEDED, FAILED, SKIPPED
	}

	public enum Step {
		LOAD, MODELS, PUBLISH
	}

	/**
	 * @param step             step
	 * @param status           status
	 * @param startedAt        ISO-8601 start time
	 * @param elapsedMillis    duration
	 * @param inputFingerprint fingerprint of the definition the step ran
	 * @param reviewFile       transfer review report
	 * @param resultFile       transfer result report
	 * @param detail           counts, such as built models and passed unit tests
	 * @param message          failure message
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record StepReport(Step step, Status status, String startedAt, Long elapsedMillis, String inputFingerprint,
			String reviewFile, String resultFile, String detail, String message) {
		static StepReport of(final Step step, final Status status) {
			return new StepReport(step, status, null, null, null, null, null, null, null);
		}
	}

	public MigrationPipelineReport {
		steps = List.copyOf(steps);
	}

	public StepReport step(final Step step) {
		return steps.stream().filter(value -> value.step() == step).findFirst().orElseThrow();
	}

	MigrationPipelineReport with(final StepReport value, final Status overall) {
		return new MigrationPipelineReport(formatVersion, runId, pipelineFingerprint, overall,
				steps.stream().map(step -> step.step() == value.step() ? value : step).toList());
	}

	void write(final Path file) throws IOException {
		final String json = new JsonConverter().toJsonString(this);
		AtomicMigrationFile.write(file, temporary -> Files.writeString(temporary, json, StandardCharsets.UTF_8));
	}
}

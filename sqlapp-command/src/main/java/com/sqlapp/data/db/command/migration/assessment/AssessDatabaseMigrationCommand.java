/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSourceProvider;
import com.sqlapp.data.schemas.migration.assessment.DatabaseMigrationAssessmentProvider;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.*;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/** Offline logical migration preflight composed from source and target providers. */
@Getter
@Setter
public class AssessDatabaseMigrationCommand extends AbstractCommand {
	private File inputFile;
	private File outputFile;
	private String targetVersion;
	private String targetDatabase;
	private boolean failOnBlockers = true;
	@Setter(lombok.AccessLevel.NONE)
	private Report report;

	public record Report(int formatVersion, String sourceFingerprint, String sourceProduct, String targetProduct,
			String targetVersion, Method migrationMethod, boolean dataScanned, boolean relationshipsCollected,
			String status, MigrationAssessment assessment) { }

	@Override
	protected void doRun() {
		report = null;
		if (inputFile == null || !inputFile.isFile()) {
			throw new CommandException("inputFile must be an existing source file");
		}
		if (outputFile == null || targetDatabase == null || targetDatabase.isBlank()
				|| targetVersion == null || targetVersion.isBlank()) {
			throw new CommandException("outputFile, targetDatabase and targetVersion are required");
		}
		try {
			final var input = inputFile.toPath().toRealPath();
			final var output = outputFile.toPath().toAbsolutePath().normalize();
			if (input.equals(output) || Files.exists(output) && Files.isSameFile(input, output)) {
				throw new CommandException("outputFile must not overwrite inputFile");
			}
			final String fingerprint = AssessMigrationCommand.fingerprint(inputFile);
			final var source = MigrationAssessmentSourceProvider.resolve(input).load(input);
			final var target = DatabaseMigrationAssessmentProvider.resolve(source.sourceProduct(), targetDatabase, targetVersion);
			final String version = target.normalizeTargetVersion(targetVersion);
			final var targetAssessment = target.assess(source, version);
			final var findings = new ArrayList<>(targetAssessment.findings());
			final var inventory = new ArrayList<>(targetAssessment.inventory());
			findings.addAll(source.assessment().findings());
			inventory.addAll(source.assessment().inventory());
			if (!fingerprint.equals(AssessMigrationCommand.fingerprint(inputFile))) {
				throw new CommandException("inputFile changed during assessment; retry with a stable copy");
			}
			final var assessment = new MigrationAssessment(findings, inventory);
			final var result = new Report(1, fingerprint, source.sourceProduct(), target.targetProduct(), version,
					Method.LOGICAL_MIGRATION, source.dataScanned(), source.relationshipsCollected(),
					assessment.hasBlockers() ? "BLOCKED" : "REVIEW_REQUIRED", assessment);
			writeReport(result);
		} catch (final Exception e) {
			throw e instanceof CommandException commandException ? commandException
					: new CommandException("Database migration assessment failed: " + e.getMessage(), e);
		}
	}

	/** Publish evidence before applying the failure policy. */
	void writeReport(final Report result) throws IOException {
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		AtomicMigrationFile.write(outputFile.toPath(), temporary -> converter.writeJsonValue(temporary.toFile(), result));
		report = result;
		info("Database migration assessment: ", report.status());
		if (failOnBlockers && report.assessment().hasBlockers()) {
			throw new CommandException("Migration blockers found; review report: " + outputFile);
		}
	}
}

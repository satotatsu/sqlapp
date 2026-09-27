/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSourceProvider;
import com.sqlapp.data.schemas.migration.assessment.DatabaseMigrationAssessmentProvider;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationDataProfile;
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
	private File htmlOutputFile;
	private File mappingFile;
	private File mappingTemplateFile;
	private String targetVersion;
	private String targetDatabase;
	private boolean failOnBlockers = true;
	private boolean scanData;
	@Setter(lombok.AccessLevel.NONE)
	private Report report;

	public record Report(int formatVersion, String sourceFingerprint, String sourceProduct, String targetProduct,
			String targetVersion, Method migrationMethod, boolean dataScanned, boolean relationshipsCollected,
			String status, MigrationAssessment assessment,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
			MigrationDataProfile dataProfile,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
			String mappingFingerprint,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
			com.sqlapp.data.schemas.migration.assessment.ResolvedMigrationTargetMapping targetMapping) {
		public Report(final int formatVersion, final String sourceFingerprint, final String sourceProduct, final String targetProduct,
				final String targetVersion, final Method migrationMethod, final boolean dataScanned, final boolean relationshipsCollected,
				final String status, final MigrationAssessment assessment, final MigrationDataProfile dataProfile) {
			this(formatVersion, sourceFingerprint, sourceProduct, targetProduct, targetVersion, migrationMethod,
					dataScanned, relationshipsCollected, status, assessment, dataProfile, null, null);
		}
		public Report(final int formatVersion, final String sourceFingerprint, final String sourceProduct, final String targetProduct,
				final String targetVersion, final Method migrationMethod, final boolean dataScanned, final boolean relationshipsCollected,
				final String status, final MigrationAssessment assessment) {
			this(formatVersion, sourceFingerprint, sourceProduct, targetProduct, targetVersion, migrationMethod,
					dataScanned, relationshipsCollected, status, assessment, null, null, null);
		}
	}

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
			final var htmlOutput = htmlOutputFile == null ? null : htmlOutputFile.toPath().toAbsolutePath().normalize();
			final var mappingPath = mappingFile == null ? null : mappingFile.toPath().toAbsolutePath().normalize();
			final var templatePath = mappingTemplateFile == null ? null : mappingTemplateFile.toPath().toAbsolutePath().normalize();
			if (mappingFile != null && !mappingFile.isFile()) { throw new CommandException("mappingFile must be an existing YAML file"); }
			if (input.equals(output) || Files.exists(output) && Files.isSameFile(input, output)) {
				throw new CommandException("outputFile must not overwrite inputFile");
			}
			if (htmlOutput != null && (input.equals(htmlOutput) || output.equals(htmlOutput)
					|| Files.exists(htmlOutput) && (Files.isSameFile(input, htmlOutput)
							|| Files.exists(output) && Files.isSameFile(output, htmlOutput)))) {
				throw new CommandException("htmlOutputFile must be distinct from inputFile and outputFile");
			}
			if (mappingPath != null && (input.equals(mappingPath) || output.equals(mappingPath)
					|| htmlOutput != null && htmlOutput.equals(mappingPath)
					|| Files.exists(output) && Files.isSameFile(output, mappingPath)
					|| htmlOutput != null && Files.exists(htmlOutput) && Files.isSameFile(htmlOutput, mappingPath))) {
				throw new CommandException("mappingFile must be distinct from inputFile and output files");
			}
			if (templatePath != null && (input.equals(templatePath) || output.equals(templatePath)
					|| htmlOutput != null && htmlOutput.equals(templatePath) || mappingPath != null && mappingPath.equals(templatePath)
					|| Files.exists(templatePath) && (Files.exists(output) && Files.isSameFile(output, templatePath)
							|| htmlOutput != null && Files.exists(htmlOutput) && Files.isSameFile(htmlOutput, templatePath)
							|| mappingPath != null && Files.isSameFile(mappingPath, templatePath)))) {
				throw new CommandException("mappingTemplateFile must be distinct from input and other output/configuration files");
			}
			final String fingerprint = AssessMigrationCommand.fingerprint(inputFile);
			final var source = MigrationAssessmentSourceProvider.resolve(input).load(input, scanData);
			if (scanData && (!source.dataScanned() || source.dataProfile() == null)) {
				throw new CommandException("Source provider did not supply the requested data profile");
			}
			final var target = DatabaseMigrationAssessmentProvider.resolve(source.sourceProduct(), targetDatabase, targetVersion);
			final String version = target.normalizeTargetVersion(targetVersion);
			final var targetAssessment = target.assess(source, version);
			final String mappingFingerprint = mappingFile == null ? null : AssessMigrationCommand.fingerprint(mappingFile);
			final var targetMapping = mappingFile == null ? null : new MigrationTargetMappingResolver().resolve(mappingFile,
					mappingFingerprint, fingerprint, targetDatabase, version, source);
			final var mappingAssessment = targetMapping == null ? new MigrationAssessment(java.util.List.of(), java.util.List.of())
					: target.assessMapping(source, version, targetMapping);
			final var mappingCoverage = targetMapping == null ? new MigrationAssessment(java.util.List.of(), java.util.List.of())
					: new MigrationTargetMappingCoverage().assess(source, targetMapping);
			final var findings = new ArrayList<>(targetAssessment.findings());
			final var inventory = new ArrayList<>(targetAssessment.inventory());
			findings.addAll(mappingAssessment.findings());
			inventory.addAll(mappingAssessment.inventory());
			findings.addAll(mappingCoverage.findings());
			inventory.addAll(mappingCoverage.inventory());
			findings.addAll(source.assessment().findings());
			inventory.addAll(source.assessment().inventory());
			if (!fingerprint.equals(AssessMigrationCommand.fingerprint(inputFile))) {
				throw new CommandException("inputFile changed during assessment; retry with a stable copy");
			}
			if (mappingFile != null && !mappingFingerprint.equals(AssessMigrationCommand.fingerprint(mappingFile))) {
				throw new CommandException("mappingFile changed during assessment; retry with a stable copy");
			}
			final var assessment = new MigrationAssessment(findings, inventory);
			final var result = new Report(targetMapping != null ? 3 : scanData ? 2 : 1, fingerprint, source.sourceProduct(), target.targetProduct(), version,
					Method.LOGICAL_MIGRATION, source.dataScanned(), source.relationshipsCollected(),
					assessment.hasBlockers() ? "BLOCKED" : "REVIEW_REQUIRED", assessment, source.dataProfile(),
					mappingFingerprint, targetMapping);
			writeReport(result, source, target, fingerprint, version);
		} catch (final Exception e) {
			throw e instanceof CommandException commandException ? commandException
					: new CommandException("Database migration assessment failed: " + e.getMessage(), e);
		}
	}

	/** Publish evidence before applying the failure policy. */
	void writeReport(final Report result) throws IOException {
		writeReport(result, null, null, null, null);
	}

	private void writeReport(final Report result,
			final com.sqlapp.data.schemas.migration.assessment.MigrationAssessmentSource source,
			final DatabaseMigrationAssessmentProvider target, final String sourceFingerprint,
			final String normalizedTargetVersion) throws IOException {
		final var converter = new JsonConverter();
		converter.setIndentOutput(true);
		AtomicMigrationFile.write(outputFile.toPath(), temporary -> converter.writeJsonValue(temporary.toFile(), result));
		report = result;
		if (htmlOutputFile != null) {
			final String html = DatabaseMigrationAssessmentHtml.render(result);
			AtomicMigrationFile.write(htmlOutputFile.toPath(), temporary -> Files.writeString(temporary, html, StandardCharsets.UTF_8));
		}
		if (mappingTemplateFile != null) {
			new MigrationTargetMappingTemplateWriter().write(mappingTemplateFile, sourceFingerprint, targetDatabase,
					normalizedTargetVersion, source, target);
		}
		info("Database migration assessment: ", report.status());
		if (failOnBlockers && report.assessment().hasBlockers()) {
			throw new CommandException("Migration blockers found; review report: " + outputFile);
		}
	}
}

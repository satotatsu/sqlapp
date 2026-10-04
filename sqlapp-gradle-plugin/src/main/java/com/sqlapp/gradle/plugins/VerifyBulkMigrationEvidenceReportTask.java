/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.bulk.VerifyBulkMigrationEvidenceReportCommand;

/** Revalidates a saved bulk migration audit artifact without database access. */
public abstract class VerifyBulkMigrationEvidenceReportTask
		extends AbstractTask<VerifyBulkMigrationEvidenceReportCommand> {
	public void call(final Action<VerifyBulkMigrationEvidenceReportTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getEvidenceReportFile();

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getOperationalReportFile();

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getVerificationReportFile();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getAssessmentReportFile();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getDdlVerificationReportFile();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getTargetValidationReportFile();

	@Input
	@Optional
	public abstract Property<String> getExpectedTargetEnvironmentId();

	@Input
	@Optional
	public abstract Property<String> getExpectedEvidenceReportFingerprint();

	@Input
	@Optional
	public abstract Property<String> getExpectedPlanFingerprint();

	@Input
	@Optional
	public abstract Property<String> getExpectedConfigurationFingerprint();

	@Input
	@Optional
	public abstract Property<Long> getMaxEvidenceAgeSeconds();

	@Override
	protected void beforeRun(final VerifyBulkMigrationEvidenceReportCommand command) {
		command.setEvidenceReportFile(getEvidenceReportFile().get().getAsFile());
		command.setOperationalReportFile(getOperationalReportFile().get().getAsFile());
		command.setVerificationReportFile(getVerificationReportFile().get().getAsFile());
		if (getConfigurationFile().isPresent()) {
			command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		}
		if (getAssessmentReportFile().isPresent()) {
			command.setAssessmentReportFile(getAssessmentReportFile().get().getAsFile());
		}
		if (getDdlVerificationReportFile().isPresent()) {
			command.setDdlVerificationReportFile(getDdlVerificationReportFile().get().getAsFile());
		}
		if (getTargetValidationReportFile().isPresent()) {
			command.setTargetValidationReportFile(getTargetValidationReportFile().get().getAsFile());
		}
		if (getExpectedTargetEnvironmentId().isPresent()) {
			command.setExpectedTargetEnvironmentId(getExpectedTargetEnvironmentId().get());
		}
		if (getExpectedEvidenceReportFingerprint().isPresent()) {
			command.setExpectedEvidenceReportFingerprint(getExpectedEvidenceReportFingerprint().get());
		}
		if (getExpectedPlanFingerprint().isPresent()) {
			command.setExpectedPlanFingerprint(getExpectedPlanFingerprint().get());
		}
		if (getExpectedConfigurationFingerprint().isPresent()) {
			command.setExpectedConfigurationFingerprint(getExpectedConfigurationFingerprint().get());
		}
		if (getMaxEvidenceAgeSeconds().isPresent()) {
			command.setMaxEvidenceAgeSeconds(getMaxEvidenceAgeSeconds().get());
		}
	}

	@Override
	protected VerifyBulkMigrationEvidenceReportCommand createCommand() {
		return new VerifyBulkMigrationEvidenceReportCommand();
	}
}

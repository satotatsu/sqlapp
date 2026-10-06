/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.bulk.VerifyBulkMigrationJobRepairEvidenceCommand;

/** Verifies repair evidence files without database access. */
public abstract class VerifyBulkMigrationJobRepairEvidenceTask
		extends AbstractTask<VerifyBulkMigrationJobRepairEvidenceCommand> {
	public void call(final Action<VerifyBulkMigrationJobRepairEvidenceTask> action) {
		action.execute(this);
	}

	@InputDirectory @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getRepairReportDirectory();
	@InputFile @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getRepairExecutionReportFile();
	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getApprovedRepairPlanFile();
	@Input @Optional
	public abstract Property<String> getExpectedApprovedRepairPlanFileFingerprint();
	@Input @Optional
	public abstract Property<Long> getMaxApprovedRepairPlanAgeSeconds();
	@Input @Optional
	public abstract Property<Long> getMaxApprovedRepairPlanFileSizeBytes();
	@Input @Optional
	public abstract Property<Long> getMaxEvidenceFileSizeBytes();
	@InputFile @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getPostRepairVerificationReportFile();
	@Input @Optional
	public abstract Property<String> getExpectedRepairExecutionReportFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedPostRepairVerificationReportFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedMigrationPlanFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedRepairPlanFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedConfigurationFingerprint();
	@Input @Optional
	public abstract Property<Long> getMaxEvidenceAgeSeconds();

	@Override
	protected void beforeRun(final VerifyBulkMigrationJobRepairEvidenceCommand command) {
		if (getRepairReportDirectory().isPresent()) {
			command.setRepairReportDirectory(getRepairReportDirectory().get().getAsFile());
		}
		if (getRepairExecutionReportFile().isPresent()) {
			command.setRepairExecutionReportFile(getRepairExecutionReportFile().get().getAsFile());
		}
		command.setApprovedRepairPlanFile(getApprovedRepairPlanFile().get().getAsFile());
		if (getExpectedApprovedRepairPlanFileFingerprint().isPresent()) {
			command.setExpectedApprovedRepairPlanFileFingerprint(
					getExpectedApprovedRepairPlanFileFingerprint().get());
		}
		if (getMaxApprovedRepairPlanAgeSeconds().isPresent()) {
			command.setMaxApprovedRepairPlanAgeSeconds(getMaxApprovedRepairPlanAgeSeconds().get());
		}
		if (getMaxApprovedRepairPlanFileSizeBytes().isPresent()) {
			command.setMaxApprovedRepairPlanFileSizeBytes(getMaxApprovedRepairPlanFileSizeBytes().get());
		}
		if (getMaxEvidenceFileSizeBytes().isPresent()) {
			command.setMaxEvidenceFileSizeBytes(getMaxEvidenceFileSizeBytes().get());
		}
		if (getPostRepairVerificationReportFile().isPresent()) {
			command.setPostRepairVerificationReportFile(getPostRepairVerificationReportFile().get().getAsFile());
		}
		if (getExpectedRepairExecutionReportFingerprint().isPresent()) {
			command.setExpectedRepairExecutionReportFingerprint(getExpectedRepairExecutionReportFingerprint().get());
		}
		if (getExpectedPostRepairVerificationReportFingerprint().isPresent()) {
			command.setExpectedPostRepairVerificationReportFingerprint(
					getExpectedPostRepairVerificationReportFingerprint().get());
		}
		if (getExpectedMigrationPlanFingerprint().isPresent()) {
			command.setExpectedMigrationPlanFingerprint(getExpectedMigrationPlanFingerprint().get());
		}
		if (getExpectedRepairPlanFingerprint().isPresent()) {
			command.setExpectedRepairPlanFingerprint(getExpectedRepairPlanFingerprint().get());
		}
		if (getExpectedConfigurationFingerprint().isPresent()) {
			command.setExpectedConfigurationFingerprint(getExpectedConfigurationFingerprint().get());
		}
		if (getMaxEvidenceAgeSeconds().isPresent()) {
			command.setMaxEvidenceAgeSeconds(getMaxEvidenceAgeSeconds().get());
		}
	}

	@Override
	protected VerifyBulkMigrationJobRepairEvidenceCommand createCommand() {
		return new VerifyBulkMigrationJobRepairEvidenceCommand();
	}
}

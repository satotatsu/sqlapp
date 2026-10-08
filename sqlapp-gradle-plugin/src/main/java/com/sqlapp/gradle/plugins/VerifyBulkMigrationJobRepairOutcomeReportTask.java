/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.work.DisableCachingByDefault;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.bulk.VerifyBulkMigrationJobRepairOutcomeReportCommand;

/** Revalidates a saved repair outcome report and all selected source evidence. */
@DisableCachingByDefault(because = "Command execution has not been validated for build caching")
public abstract class VerifyBulkMigrationJobRepairOutcomeReportTask
		extends AbstractTask<VerifyBulkMigrationJobRepairOutcomeReportCommand> {
	public void call(final Action<VerifyBulkMigrationJobRepairOutcomeReportTask> action) { action.execute(this); }

	@InputFiles @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getOutcomeReportFile();
	@InputDirectory @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getRepairReportDirectory();
	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getApprovedRepairPlanFile();
	@Input @Optional public abstract Property<String> getExpectedApprovedRepairPlanFileFingerprint();
	@Input @Optional public abstract Property<Long> getMaxApprovedRepairPlanAgeSeconds();
	@Input @Optional public abstract Property<Long> getMaxApprovedRepairPlanFileSizeBytes();
	@Input @Optional public abstract Property<Long> getMaxEvidenceFileSizeBytes();
	@InputFiles @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getRepairExecutionReportFile();
	@InputFiles @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getRepairFailureReportFile();
	@InputFiles @Optional @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getPostRepairVerificationReportFile();
	@Input @Optional public abstract Property<String> getExpectedOutcomeReportFingerprint();
	@Input @Optional public abstract Property<String> getExpectedStatus();
	@Input @Optional public abstract Property<String> getExpectedMigrationPlanFingerprint();
	@Input @Optional public abstract Property<String> getExpectedRepairPlanFingerprint();
	@Input @Optional public abstract Property<String> getExpectedConfigurationFingerprint();
	@Input @Optional public abstract Property<Long> getMaxEvidenceAgeSeconds();

	@Override
	protected void beforeRun(final VerifyBulkMigrationJobRepairOutcomeReportCommand command) {
		if (getOutcomeReportFile().isPresent()) {
			command.setOutcomeReportFile(getOutcomeReportFile().get().getAsFile());
		}
		if (getRepairReportDirectory().isPresent()) {
			command.setRepairReportDirectory(getRepairReportDirectory().get().getAsFile());
		}
		command.setApprovedRepairPlanFile(getApprovedRepairPlanFile().get().getAsFile());
		set(getExpectedApprovedRepairPlanFileFingerprint(), command::setExpectedApprovedRepairPlanFileFingerprint);
		set(getMaxApprovedRepairPlanAgeSeconds(), command::setMaxApprovedRepairPlanAgeSeconds);
		set(getMaxApprovedRepairPlanFileSizeBytes(), command::setMaxApprovedRepairPlanFileSizeBytes);
		set(getMaxEvidenceFileSizeBytes(), command::setMaxEvidenceFileSizeBytes);
		setFile(getRepairExecutionReportFile(), command::setRepairExecutionReportFile);
		setFile(getRepairFailureReportFile(), command::setRepairFailureReportFile);
		setFile(getPostRepairVerificationReportFile(), command::setPostRepairVerificationReportFile);
		set(getExpectedOutcomeReportFingerprint(), command::setExpectedOutcomeReportFingerprint);
		set(getExpectedStatus(), command::setExpectedStatus);
		set(getExpectedMigrationPlanFingerprint(), command::setExpectedMigrationPlanFingerprint);
		set(getExpectedRepairPlanFingerprint(), command::setExpectedRepairPlanFingerprint);
		set(getExpectedConfigurationFingerprint(), command::setExpectedConfigurationFingerprint);
		set(getMaxEvidenceAgeSeconds(), command::setMaxEvidenceAgeSeconds);
	}

	private static void setFile(final RegularFileProperty property,
			final java.util.function.Consumer<java.io.File> setter) {
		if (property.isPresent()) { setter.accept(property.get().getAsFile()); }
	}

	private static <T> void set(final Property<T> property, final java.util.function.Consumer<T> setter) {
		if (property.isPresent()) { setter.accept(property.get()); }
	}

	@Override
	protected VerifyBulkMigrationJobRepairOutcomeReportCommand createCommand() {
		return new VerifyBulkMigrationJobRepairOutcomeReportCommand();
	}
}

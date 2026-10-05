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

import com.sqlapp.data.db.command.migration.bulk.VerifyBulkMigrationJobRepairFailureEvidenceCommand;

/** Verifies failed repair evidence files without database access. */
public abstract class VerifyBulkMigrationJobRepairFailureEvidenceTask
		extends AbstractTask<VerifyBulkMigrationJobRepairFailureEvidenceCommand> {
	public void call(final Action<VerifyBulkMigrationJobRepairFailureEvidenceTask> action) {
		action.execute(this);
	}

	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getRepairFailureReportFile();
	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getApprovedRepairPlanFile();
	@Input @Optional
	public abstract Property<String> getExpectedRepairFailureReportFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedMigrationPlanFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedRepairPlanFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedConfigurationFingerprint();
	@Input @Optional
	public abstract Property<Long> getMaxEvidenceAgeSeconds();

	@Override
	protected void beforeRun(final VerifyBulkMigrationJobRepairFailureEvidenceCommand command) {
		command.setRepairFailureReportFile(getRepairFailureReportFile().get().getAsFile());
		command.setApprovedRepairPlanFile(getApprovedRepairPlanFile().get().getAsFile());
		if (getExpectedRepairFailureReportFingerprint().isPresent()) {
			command.setExpectedRepairFailureReportFingerprint(getExpectedRepairFailureReportFingerprint().get());
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
	protected VerifyBulkMigrationJobRepairFailureEvidenceCommand createCommand() {
		return new VerifyBulkMigrationJobRepairFailureEvidenceCommand();
	}
}

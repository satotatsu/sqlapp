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

import com.sqlapp.data.db.command.migration.bulk.VerifyBulkMigrationEvidenceCommand;

/** Verifies bulk migration execution and data evidence without database access. */
public abstract class VerifyBulkMigrationEvidenceTask extends AbstractTask<VerifyBulkMigrationEvidenceCommand> {
	public VerifyBulkMigrationEvidenceTask() {
		getRequireSuccessfulExecution().convention(true);
		getRequireMatchingData().convention(true);
		getRequireProvenance().convention(true);
	}

	public void call(final Action<VerifyBulkMigrationEvidenceTask> action) {
		action.execute(this);
	}

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

	@Input
	public abstract Property<Boolean> getRequireSuccessfulExecution();

	@Input
	public abstract Property<Boolean> getRequireMatchingData();

	@Input
	public abstract Property<Boolean> getRequireProvenance();

	@Override
	protected void beforeRun(final VerifyBulkMigrationEvidenceCommand command) {
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
		command.setRequireSuccessfulExecution(getRequireSuccessfulExecution().get());
		command.setRequireMatchingData(getRequireMatchingData().get());
		command.setRequireProvenance(getRequireProvenance().get());
	}

	@Override
	protected VerifyBulkMigrationEvidenceCommand createCommand() {
		return new VerifyBulkMigrationEvidenceCommand();
	}
}

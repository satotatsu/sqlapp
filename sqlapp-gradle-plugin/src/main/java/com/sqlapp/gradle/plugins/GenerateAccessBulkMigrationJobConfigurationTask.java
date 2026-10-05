/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.assessment.GenerateAccessBulkMigrationJobConfigurationCommand;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;

/** Generates an executeBulkMigrationJob YAML file from an Access assessment. */
public abstract class GenerateAccessBulkMigrationJobConfigurationTask
		extends AbstractTask<GenerateAccessBulkMigrationJobConfigurationCommand> {
	public GenerateAccessBulkMigrationJobConfigurationTask() {
		getChunkSize().convention(10_000);
		getResume().convention(true);
		getVerification().convention(true);
		getOperationalReport().convention(true);
		getCheckpointMode().convention(BulkMigrationCheckpointMode.DATABASE);
		getLeaseDurationSeconds().convention(300L);
	}
	@InputFile @PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getAssessmentReportFile();
	@InputFile @PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getSchemaFile();
	@OutputFile
	public abstract RegularFileProperty getOutputFile();
	@Input @Optional
	public abstract Property<String> getJobId();
	@Input
	public abstract Property<Integer> getChunkSize();
	@Input
	public abstract Property<Boolean> getResume();
	@Input
	public abstract Property<Boolean> getVerification();
	@Input @Optional
	public abstract Property<String> getVerificationReportFile();
	@Input @Optional
	public abstract Property<String> getRepairPlanOnMismatchFile();
	@Input
	public abstract Property<Boolean> getOperationalReport();
	@Input @Optional
	public abstract Property<String> getOperationalReportFile();
	@Input
	public abstract Property<BulkMigrationCheckpointMode> getCheckpointMode();
	@Input @Optional
	public abstract Property<String> getCheckpointDirectory();
	@Input @Optional
	public abstract Property<String> getLeaseOwnerId();
	@Input
	public abstract Property<Long> getLeaseDurationSeconds();
	@Input @Optional
	public abstract Property<String> getExpectedAssessmentReportFingerprint();
	@InputFile @Optional @PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getDdlVerificationReportFile();
	@Input @Optional
	public abstract Property<String> getExpectedDdlVerificationReportFingerprint();
	@Override protected GenerateAccessBulkMigrationJobConfigurationCommand createCommand() {
		return new GenerateAccessBulkMigrationJobConfigurationCommand();
	}
	@Override protected void beforeRun(final GenerateAccessBulkMigrationJobConfigurationCommand command) {
		command.setAssessmentReportFile(getAssessmentReportFile().get().getAsFile());
		command.setSchemaFile(getSchemaFile().get().getAsFile());
		command.setOutputFile(getOutputFile().get().getAsFile());
		if (getJobId().isPresent()) { command.setJobId(getJobId().get()); }
		command.setChunkSize(getChunkSize().get());
		command.setResume(getResume().get());
		command.setVerification(getVerification().get());
		if (getVerificationReportFile().isPresent()) {
			command.setVerificationReportFile(getVerificationReportFile().get());
		}
		if (getRepairPlanOnMismatchFile().isPresent()) {
			command.setRepairPlanOnMismatchFile(getRepairPlanOnMismatchFile().get());
		}
		command.setOperationalReport(getOperationalReport().get());
		if (getOperationalReportFile().isPresent()) {
			command.setOperationalReportFile(getOperationalReportFile().get());
		}
		command.setCheckpointMode(getCheckpointMode().get());
		if (getCheckpointDirectory().isPresent()) {
			command.setCheckpointDirectory(getCheckpointDirectory().get());
		}
		if (getLeaseOwnerId().isPresent()) {
			command.setLeaseOwnerId(getLeaseOwnerId().get());
		}
		command.setLeaseDurationSeconds(getLeaseDurationSeconds().get());
		if (getExpectedAssessmentReportFingerprint().isPresent()) {
			command.setExpectedAssessmentReportFingerprint(getExpectedAssessmentReportFingerprint().get());
		}
		if (getDdlVerificationReportFile().isPresent()) {
			command.setDdlVerificationReportFile(getDdlVerificationReportFile().get().getAsFile());
		}
		if (getExpectedDdlVerificationReportFingerprint().isPresent()) {
			command.setExpectedDdlVerificationReportFingerprint(getExpectedDdlVerificationReportFingerprint().get());
		}
	}
}

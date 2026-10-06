/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.bulk.ValidateBulkMigrationTargetCommand;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;

/** Validates a declarative bulk migration target without writing data. */
@DisableCachingByDefault(because = "Reads live database metadata and data")
public abstract class ValidateBulkMigrationTargetTask extends AbstractDbTask<ValidateBulkMigrationTargetCommand> {

	public ValidateBulkMigrationTargetTask() {
		setSourceDataSource(getProject().getObjects().newInstance(DataSourceExtension.class));
	}

	public void call(final Action<ValidateBulkMigrationTargetTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getConfigurationFile();

	@Input
	@Optional
	public abstract Property<String> getExpectedConfigurationFingerprint();

	@Input
	@Optional
	public abstract Property<Long> getMaxConfigurationFileSizeBytes();

	@Input
	@Optional
	public abstract Property<Long> getMaxSchemaFileSizeBytes();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getAssessmentReportFile();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getDdlVerificationReportFile();

	@Input
	@Optional
	public abstract Property<Long> getMaxApprovalArtifactFileSizeBytes();

	@OutputFile
	@Optional
	public abstract RegularFileProperty getReportFile();

	@Input
	@Optional
	public abstract Property<String> getTargetEnvironmentId();

	@Nested
	public abstract DataSourceExtension getSourceDataSource();

	public abstract void setSourceDataSource(DataSourceExtension value);

	public void sourceDataSource(final Action<DataSourceExtension> action) {
		action.execute(getSourceDataSource());
	}

	@Override
	protected void beforeRun(final ValidateBulkMigrationTargetCommand command) {
		command.setConfigurationFile(getConfigurationFile().get().getAsFile());
		command.setSourceDataSource(getSourceDataSource().createDataSource());
		if (getExpectedConfigurationFingerprint().isPresent()) {
			command.setExpectedConfigurationFingerprint(getExpectedConfigurationFingerprint().get());
		}
		if (getMaxConfigurationFileSizeBytes().isPresent()) {
			command.setMaxConfigurationFileSizeBytes(getMaxConfigurationFileSizeBytes().get());
		}
		if (getMaxSchemaFileSizeBytes().isPresent()) {
			command.setMaxSchemaFileSizeBytes(getMaxSchemaFileSizeBytes().get());
		}
		if (getAssessmentReportFile().isPresent()) {
			command.setAssessmentReportFile(getAssessmentReportFile().get().getAsFile());
		}
		if (getDdlVerificationReportFile().isPresent()) {
			command.setDdlVerificationReportFile(getDdlVerificationReportFile().get().getAsFile());
		}
		if (getMaxApprovalArtifactFileSizeBytes().isPresent()) {
			command.setMaxApprovalArtifactFileSizeBytes(getMaxApprovalArtifactFileSizeBytes().get());
		}
		if (getReportFile().isPresent()) {
			command.setReportFile(getReportFile().get().getAsFile());
		}
		if (getTargetEnvironmentId().isPresent()) {
			command.setTargetEnvironmentId(getTargetEnvironmentId().get());
		}
	}

	@Override
	protected ValidateBulkMigrationTargetCommand createCommand() {
		return new ValidateBulkMigrationTargetCommand();
	}
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.migration.assessment.VerifyDatabaseMigrationDdlPhasesCommand;

/** Verifies phase-separated database migration DDL without opening a database connection. */
public abstract class VerifyDatabaseMigrationDdlPhasesTask
		extends AbstractTask<VerifyDatabaseMigrationDdlPhasesCommand> {
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getDirectory();
	@org.gradle.api.tasks.InputFile @Optional
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getAssessmentReportFile();
	@OutputFile @Optional
	public abstract RegularFileProperty getVerificationReportFile();
	@Input @Optional
	public abstract Property<String> getExpectedAssessmentReportFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedSourceFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedMappingFingerprint();
	@Input @Optional
	public abstract Property<String> getExpectedTargetDatabase();
	@Input @Optional
	public abstract Property<String> getExpectedTargetVersion();

	@Override
	protected VerifyDatabaseMigrationDdlPhasesCommand createCommand() {
		return new VerifyDatabaseMigrationDdlPhasesCommand();
	}

	@Override
	protected void beforeRun(final VerifyDatabaseMigrationDdlPhasesCommand command) {
		command.setDirectory(getDirectory().get().getAsFile());
		if (getAssessmentReportFile().isPresent()) { command.setAssessmentReportFile(getAssessmentReportFile().get().getAsFile()); }
		if (getVerificationReportFile().isPresent()) { command.setVerificationReportFile(getVerificationReportFile().get().getAsFile()); }
		if (getExpectedAssessmentReportFingerprint().isPresent()) {
			command.setExpectedAssessmentReportFingerprint(getExpectedAssessmentReportFingerprint().get());
		}
		if (getExpectedSourceFingerprint().isPresent()) { command.setExpectedSourceFingerprint(getExpectedSourceFingerprint().get()); }
		if (getExpectedMappingFingerprint().isPresent()) { command.setExpectedMappingFingerprint(getExpectedMappingFingerprint().get()); }
		if (getExpectedTargetDatabase().isPresent()) { command.setExpectedTargetDatabase(getExpectedTargetDatabase().get()); }
		if (getExpectedTargetVersion().isPresent()) { command.setExpectedTargetVersion(getExpectedTargetVersion().get()); }
	}
}

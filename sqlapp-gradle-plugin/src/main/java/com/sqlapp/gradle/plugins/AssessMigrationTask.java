/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.assessment.AssessMigrationCommand;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.gradle.plugins.properties.OptionalDataSourceTaskProperty;

/** Offline or optional read-only online migration preflight. */
@DisableCachingByDefault(because = "Assessment is review evidence and must rerun its failure policy")
public abstract class AssessMigrationTask extends AbstractTask<AssessMigrationCommand>
		implements OptionalDataSourceTaskProperty {
	public AssessMigrationTask() {
		getFailOnBlockers().convention(true);
		getScanCharacterData().convention(false);
		getOutputs().upToDateWhen(task -> false);
	}
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getSchemaFile();
	@OutputFile
	public abstract RegularFileProperty getOutputFile();
	@Input
	public abstract Property<String> getTargetVersion();
	@Input
	@Optional
	public abstract Property<String> getTargetCharacterSet();
	@Input
	public abstract Property<MigrationAssessment.Method> getMigrationMethod();
	@Input
	public abstract Property<Boolean> getFailOnBlockers();
	@Input
	public abstract Property<Boolean> getScanCharacterData();

	@Override
	protected AssessMigrationCommand createCommand() {
		return new AssessMigrationCommand();
	}
	@Override
	protected void beforeRun(final AssessMigrationCommand command) {
		command.setSchemaFile(getSchemaFile().get().getAsFile());
		command.setOutputFile(getOutputFile().get().getAsFile());
		command.setTargetVersion(getTargetVersion().get());
		command.setTargetCharacterSet(getTargetCharacterSet().getOrNull());
		command.setMigrationMethod(getMigrationMethod().get());
		command.setFailOnBlockers(getFailOnBlockers().get());
		command.setScanCharacterData(getScanCharacterData().get());
	}
}

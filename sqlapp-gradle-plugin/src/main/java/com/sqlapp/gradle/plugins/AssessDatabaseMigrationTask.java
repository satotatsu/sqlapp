/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.assessment.AssessDatabaseMigrationCommand;

/** Offline migration preflight using source and target service providers. */
@DisableCachingByDefault(because = "Assessment must rerun its failure policy")
public abstract class AssessDatabaseMigrationTask extends AbstractTask<AssessDatabaseMigrationCommand> {
	public AssessDatabaseMigrationTask() {
		getFailOnBlockers().convention(true);
		getScanData().convention(false);
		getOutputs().upToDateWhen(task -> false);
	}
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getInputFile();
	@OutputFile
	public abstract RegularFileProperty getOutputFile();
	@Optional
	@OutputFile
	public abstract RegularFileProperty getHtmlOutputFile();
	@Optional
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getMappingFile();
	@Optional
	@OutputFile
	public abstract RegularFileProperty getMappingTemplateFile();
	@Optional
	@OutputFile
	public abstract RegularFileProperty getDdlOutputFile();
	@Optional
	@org.gradle.api.tasks.OutputDirectory
	public abstract DirectoryProperty getDdlPhaseOutputDirectory();
	@Input
	public abstract Property<String> getTargetVersion();
	@Input
	public abstract Property<String> getTargetDatabase();
	@Input
	public abstract Property<Boolean> getFailOnBlockers();
	@Input
	public abstract Property<Boolean> getScanData();

	@Override
	protected AssessDatabaseMigrationCommand createCommand() {
		return new AssessDatabaseMigrationCommand();
	}
	@Override
	protected void beforeRun(final AssessDatabaseMigrationCommand command) {
		command.setInputFile(getInputFile().get().getAsFile());
		command.setOutputFile(getOutputFile().get().getAsFile());
		command.setHtmlOutputFile(getHtmlOutputFile().isPresent() ? getHtmlOutputFile().get().getAsFile() : null);
		command.setMappingFile(getMappingFile().isPresent() ? getMappingFile().get().getAsFile() : null);
		command.setMappingTemplateFile(getMappingTemplateFile().isPresent() ? getMappingTemplateFile().get().getAsFile() : null);
		command.setDdlOutputFile(getDdlOutputFile().isPresent() ? getDdlOutputFile().get().getAsFile() : null);
		command.setDdlPhaseOutputDirectory(getDdlPhaseOutputDirectory().isPresent()
				? getDdlPhaseOutputDirectory().get().getAsFile() : null);
		command.setTargetVersion(getTargetVersion().get());
		command.setTargetDatabase(getTargetDatabase().get());
		command.setFailOnBlockers(getFailOnBlockers().get());
		command.setScanData(getScanData().get());
	}
}

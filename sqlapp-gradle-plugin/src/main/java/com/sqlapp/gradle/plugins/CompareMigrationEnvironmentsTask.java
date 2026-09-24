/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins;

import java.util.ArrayList;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.CompareMigrationEnvironmentsCommand;

/** Compares migration snapshots without connecting to their databases. */
@DisableCachingByDefault(because = "Comparison reports may be used as CI gate evidence")
public abstract class CompareMigrationEnvironmentsTask extends AbstractTask<CompareMigrationEnvironmentsCommand> {
	public CompareMigrationEnvironmentsTask() {
		getFailOnDifferences().convention(false);
	}

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSnapshotFiles();

	@Input
	@Optional
	public abstract Property<String> getBaselineEnvironmentId();

	@OutputFile
	@Optional
	public abstract RegularFileProperty getOutputFile();

	@Input
	public abstract Property<Boolean> getFailOnDifferences();

	@Override
	protected CompareMigrationEnvironmentsCommand createCommand() {
		return new CompareMigrationEnvironmentsCommand();
	}

	@Override
	protected void beforeRun(final CompareMigrationEnvironmentsCommand command) {
		command.setSnapshotFiles(new ArrayList<>(getSnapshotFiles().getFiles()));
		if (getBaselineEnvironmentId().isPresent()) {
			command.setBaselineEnvironmentId(getBaselineEnvironmentId().get());
		}
		if (getOutputFile().isPresent()) {
			command.setOutputFile(getOutputFile().get().getAsFile());
		}
		command.setFailOnDifferences(getFailOnDifferences().get());
	}
}

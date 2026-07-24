/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.GenerateLegacyRdbLoaderCommand;

/**
 * Gradle task for generating restartable legacy RDB loader artifacts.
 */
@DisableCachingByDefault
public abstract class GenerateLegacyRdbLoaderTask extends AbstractTask<GenerateLegacyRdbLoaderCommand> {

	public GenerateLegacyRdbLoaderTask() {
		getTableOperationMode().convention("INSERT_IGNORE");
		getRootBatchSize().convention(500);
		getCommitEveryRootBatches().convention(500L);
		getDeleteCommittedRoots().convention(true);
		getStagingTablePrefix().convention("TMP_");
		getQuoteIdentifiers().convention(false);
		getRunnerClassName().convention("LegacyMigrationLoader");
	}

	public void call(Action<GenerateLegacyRdbLoaderTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getContractFile();

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getSchemaFile();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	public abstract Property<String> getTableOperationMode();

	@Input
	public abstract Property<Integer> getRootBatchSize();

	@Input
	public abstract Property<Long> getCommitEveryRootBatches();

	@Input
	public abstract Property<Boolean> getDeleteCommittedRoots();

	@Input
	public abstract Property<String> getStagingTablePrefix();

	@Input
	public abstract Property<Boolean> getQuoteIdentifiers();

	@Input
	public abstract Property<String> getRunnerClassName();

	@Override
	protected void beforeRun(GenerateLegacyRdbLoaderCommand command) {
		command.setContractFile(getContractFile().get().getAsFile());
		command.setSchemaFile(getSchemaFile().get().getAsFile());
		command.setOutputDirectory(getOutputDirectory().get().getAsFile());
		command.setTableOperationMode(getTableOperationMode().get());
		command.setRootBatchSize(getRootBatchSize().get());
		command.setCommitEveryRootBatches(getCommitEveryRootBatches().get());
		command.setDeleteCommittedRoots(getDeleteCommittedRoots().get());
		command.setStagingTablePrefix(getStagingTablePrefix().get());
		command.setQuoteIdentifiers(getQuoteIdentifiers().get());
		command.setRunnerClassName(getRunnerClassName().get());
	}

	@Override
	protected GenerateLegacyRdbLoaderCommand createCommand() {
		return new GenerateLegacyRdbLoaderCommand();
	}
}

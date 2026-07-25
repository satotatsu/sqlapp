/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.migration.LoadLegacyHierarchyCommand;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;

/**
 * Gradle task for loading a staged legacy hierarchy.
 */
@DisableCachingByDefault
public abstract class LoadLegacyHierarchyTask extends AbstractTask<LoadLegacyHierarchyCommand>
		implements DataSourceTaskProperty {

	public void call(Action<LoadLegacyHierarchyTask> action) {
		action.execute(this);
	}

	@InputFile
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getLoadPlanFile();

	@InputFile
	@Optional
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract RegularFileProperty getSchemaFile();

	@Override
	protected void beforeRun(LoadLegacyHierarchyCommand command) {
		command.setLoadPlanFile(getLoadPlanFile().get().getAsFile());
		if (getSchemaFile().isPresent()) {
			command.setSchemaFile(getSchemaFile().get().getAsFile());
		}
	}

	@Override
	protected LoadLegacyHierarchyCommand createCommand() {
		return new LoadLegacyHierarchyCommand();
	}
}

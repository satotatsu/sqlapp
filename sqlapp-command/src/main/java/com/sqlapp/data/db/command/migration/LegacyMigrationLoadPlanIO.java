/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/**
 * Reads and writes legacy RDB load plans.
 */
public class LegacyMigrationLoadPlanIO {

	private final YamlConverter converter = new YamlConverter();

	public LegacyMigrationLoadPlan read(File file) {
		return converter.fromJsonString(file, LegacyMigrationLoadPlan.class);
	}

	public void write(File file, LegacyMigrationLoadPlan plan) {
		File directory = file.getAbsoluteFile().getParentFile();
		if (directory != null && !directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create RDB load plan directory: " + directory);
		}
		File temporary = new File(directory, file.getName() + ".tmp");
		converter.writeJsonValue(temporary, plan);
		try {
			Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new CommandException("Failed to replace RDB load plan: " + file, e);
		} finally {
			if (temporary.exists()) {
				temporary.delete();
			}
		}
	}
}

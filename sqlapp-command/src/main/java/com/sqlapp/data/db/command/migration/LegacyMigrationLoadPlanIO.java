/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;

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
		try {
			AtomicMigrationFile.write(file.toPath(),
					temporary -> converter.writeJsonValue(temporary.toFile(), plan));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to replace RDB load plan: " + file, e);
		}
	}
}

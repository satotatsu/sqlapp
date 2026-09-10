/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/**
 * Reads and writes {@link LegacyMigrationMapping} YAML artifacts.
 */
public class LegacyMigrationMappingIO {

	private final YamlConverter converter = new YamlConverter();

	public LegacyMigrationMapping read(File file) {
		return converter.fromJsonString(file, LegacyMigrationMapping.class);
	}

	public void write(File file, LegacyMigrationMapping mapping) {
		try {
			AtomicMigrationFile.write(file.toPath(),
					temporary -> converter.writeJsonValue(temporary.toFile(), mapping));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to replace migration mapping: " + file, e);
		}
	}
}

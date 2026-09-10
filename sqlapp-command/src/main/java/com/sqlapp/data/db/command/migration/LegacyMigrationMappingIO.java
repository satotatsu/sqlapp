/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/**
 * Reads and writes {@link LegacyMigrationMapping} YAML artifacts.
 */
public class LegacyMigrationMappingIO {

	private final YamlConverter converter = new YamlConverter();

	public LegacyMigrationMapping read(File file) {
		Objects.requireNonNull(file, "file");
		if (!file.isFile()) {
			throw new CommandException("Legacy migration mapping does not exist: " + file);
		}
		try {
			LegacyMigrationMapping mapping = converter.fromJsonString(file,
					LegacyMigrationMapping.class);
			new LegacyMigrationMappingValidator().validate(mapping);
			return mapping;
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read legacy migration mapping: " + file, e);
		}
	}

	public void write(File file, LegacyMigrationMapping mapping) {
		Objects.requireNonNull(file, "file");
		new LegacyMigrationMappingValidator().validate(mapping);
		try {
			AtomicMigrationFile.write(file.toPath(),
					temporary -> converter.writeJsonValue(temporary.toFile(), mapping));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to replace migration mapping: " + file, e);
		}
	}
}

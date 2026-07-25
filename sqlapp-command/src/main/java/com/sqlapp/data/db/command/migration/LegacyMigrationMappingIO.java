/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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
		File directory = file.getAbsoluteFile().getParentFile();
		if (directory != null && !directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create migration mapping directory: " + directory);
		}
		File temporary = new File(directory, file.getName() + ".tmp");
		converter.writeJsonValue(temporary, mapping);
		try {
			try {
				Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new CommandException("Failed to replace migration mapping: " + file, e);
		} finally {
			if (temporary.exists()) {
				temporary.delete();
			}
		}
	}
}

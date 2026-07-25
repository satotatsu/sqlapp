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

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/**
 * Reads and atomically writes legacy migration contracts.
 */
public class LegacyMigrationContractIO {

	private final YamlConverter converter = new YamlConverter();

	public LegacyMigrationContract read(File file) {
		return converter.fromJsonString(file, LegacyMigrationContract.class);
	}

	public void write(File file, LegacyMigrationContract contract) {
		File directory = file.getAbsoluteFile().getParentFile();
		if (directory != null && !directory.exists() && !directory.mkdirs()) {
			throw new CommandException("Failed to create migration contract directory: " + directory);
		}
		File temporary = new File(directory, file.getName() + ".tmp");
		converter.writeJsonValue(temporary, contract);
		try {
			try {
				Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new CommandException("Failed to replace migration contract: " + file, e);
		} finally {
			if (temporary.exists()) {
				temporary.delete();
			}
		}
	}
}

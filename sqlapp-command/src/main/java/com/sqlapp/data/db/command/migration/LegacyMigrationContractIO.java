/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/**
 * Reads and atomically writes legacy migration contracts.
 */
public class LegacyMigrationContractIO {

	private final YamlConverter converter = new YamlConverter();

	public LegacyMigrationContract read(File file) {
		Objects.requireNonNull(file, "file");
		if (!file.isFile()) {
			throw new CommandException("Legacy migration contract does not exist: " + file);
		}
		try {
			LegacyMigrationContract contract = converter.fromJsonString(file,
					LegacyMigrationContract.class);
			new LegacyMigrationContractValidator().validate(contract);
			return contract;
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read legacy migration contract: " + file, e);
		}
	}

	public void write(File file, LegacyMigrationContract contract) {
		Objects.requireNonNull(file, "file");
		new LegacyMigrationContractValidator().validate(contract);
		try {
			AtomicMigrationFile.write(file.toPath(),
					temporary -> converter.writeJsonValue(temporary.toFile(), contract));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to replace migration contract: " + file, e);
		}
	}
}

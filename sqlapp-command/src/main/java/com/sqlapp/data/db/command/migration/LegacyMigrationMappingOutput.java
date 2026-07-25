/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.io.File;

import com.sqlapp.data.schemas.migration.LegacyMigrationMapping;
import com.sqlapp.exceptions.CommandException;

/**
 * Loads an optional previous mapping, composes the new step, and saves it
 * atomically.
 */
public class LegacyMigrationMappingOutput {

	public void write(File previousMappingFile, File outputFile, LegacyMigrationMapping step) {
		LegacyMigrationMappingIO io = new LegacyMigrationMappingIO();
		LegacyMigrationMapping result = step;
		if (previousMappingFile != null) {
			if (!previousMappingFile.isFile()) {
				throw new CommandException("migrationMappingFile does not exist or is not a file: "
						+ previousMappingFile);
			}
			result = new LegacyMigrationMappingMerger().merge(io.read(previousMappingFile), step);
		}
		io.write(outputFile, result);
	}

	public void validateInput(File previousMappingFile, File inputSchemaFile) {
		if (previousMappingFile == null) {
			return;
		}
		if (!previousMappingFile.isFile()) {
			throw new CommandException("migrationMappingFile does not exist or is not a file: "
					+ previousMappingFile);
		}
		LegacyMigrationMapping mapping = new LegacyMigrationMappingIO().read(previousMappingFile);
		new LegacyMigrationMappingValidator().validateTargetFingerprint(mapping, inputSchemaFile);
	}
}

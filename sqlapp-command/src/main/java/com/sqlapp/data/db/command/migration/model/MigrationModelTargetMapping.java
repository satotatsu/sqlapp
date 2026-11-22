/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;
import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelColumn;
import com.sqlapp.data.schemas.migration.MigrationModelPlan;
import com.sqlapp.exceptions.CommandException;

/**
 * Transfer name mapping that gives the declared column types of model tables to
 * a transfer reading them from the engine. Each table is marked as a contract,
 * so that an existing target table is checked against it before loading.
 */
public final class MigrationModelTargetMapping {

	private MigrationModelTargetMapping() {
	}

	/** Mapping for every table model with declared columns, keyed by its table. */
	public static TransferNameMappingIO.Configuration of(final MigrationModelPlan plan) {
		final Map<String, TransferNameMappingIO.TableMapping> tables = new LinkedHashMap<>();
		for (final MigrationModel model : plan.models()) {
			if (!model.hasDeclaredColumns()) {
				continue;
			}
			final Map<String, TransferNameMappingIO.ColumnMapping> columns = new LinkedHashMap<>();
			for (final MigrationModelColumn column : model.columns()) {
				columns.put(column.name(), new TransferNameMappingIO.ColumnMapping(null, column.typeText(), null));
			}
			tables.put(model.schemaName() == null ? model.name() : model.schemaName() + "." + model.name(),
					new TransferNameMappingIO.TableMapping(null, columns, true));
		}
		return new TransferNameMappingIO.Configuration(1, tables);
	}

	/** Writes the mapping atomically, replacing a previous one. */
	static void write(final Path file, final MigrationModelPlan plan) {
		final TransferNameMappingIO io = new TransferNameMappingIO();
		final String text = io.format(of(plan));
		try {
			String previous = null;
			if (Files.exists(file)) {
				final var snapshot = io.read(file);
				if (snapshot.text().equals(text)) {
					return;
				}
				previous = snapshot.fingerprint();
			}
			if (file.toAbsolutePath().getParent() != null) {
				Files.createDirectories(file.toAbsolutePath().getParent());
			}
			io.write(file, text, previous);
		} catch (IOException | RuntimeException e) {
			throw new CommandException(
					"Failed to write migration model target mapping: " + file.toAbsolutePath() + ": " + e.getMessage(),
					e);
		}
	}
}

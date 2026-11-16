/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sqlapp.data.schemas.migration.MigrationModel;
import com.sqlapp.data.schemas.migration.MigrationModelPlan;
import com.sqlapp.data.schemas.migration.MigrationModelSource;
import com.sqlapp.data.schemas.migration.MigrationModelUnitTest;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.YamlConverter;

/** Reads model YAML and its SQL files into a validated plan. */
public final class MigrationModelConfigurationResolver {

	public record Resolution(MigrationModelPlan plan, List<MigrationModelUnitTest> unitTests) {
		public Resolution {
			unitTests = List.copyOf(unitTests);
		}
	}

	public Resolution resolve(final File configurationFile) {
		if (configurationFile == null || !configurationFile.isFile()) {
			throw new CommandException("Migration model configuration file is required"
					+ (configurationFile == null ? "." : ": " + configurationFile.getAbsolutePath()));
		}
		final MigrationModelConfiguration value;
		try {
			final YamlConverter converter = new YamlConverter();
			converter.setFailOnUnknownProperties(true);
			value = converter.fromJsonString(configurationFile, MigrationModelConfiguration.class);
		} catch (RuntimeException e) {
			throw new CommandException("Failed to read migration model configuration: "
					+ configurationFile.getAbsolutePath() + ": " + e.getMessage(), e);
		}
		if (value == null || value.getModels() == null || value.getModels().isEmpty()) {
			throw new CommandException("models is required in migration model configuration.");
		}
		try {
			final List<MigrationModelSource> sources = new ArrayList<>();
			for (final var source : nullToEmpty(value.getSources())) {
				sources.add(source(configurationFile, source));
			}
			final List<MigrationModel> models = new ArrayList<>();
			for (final var model : value.getModels()) {
				models.add(model(configurationFile, model));
			}
			final MigrationModelPlan plan = MigrationModelPlan.of(models, sources);
			final List<MigrationModelUnitTest> unitTests = new ArrayList<>();
			final Map<String, Boolean> ids = new LinkedHashMap<>();
			for (final var test : nullToEmpty(value.getUnitTests())) {
				final MigrationModelUnitTest unitTest = unitTest(test);
				plan.model(unitTest.model());
				if (ids.put(unitTest.id(), Boolean.TRUE) != null) {
					throw new IllegalArgumentException("Duplicate unit test name: " + unitTest.id());
				}
				unitTests.add(unitTest);
			}
			return new Resolution(plan, unitTests);
		} catch (IllegalArgumentException e) {
			throw new CommandException("Invalid migration model configuration: " + e.getMessage(), e);
		}
	}

	private static MigrationModel model(final File configurationFile, final MigrationModelConfiguration.Model value) {
		final boolean file = value.getSql() != null && !value.getSql().isBlank();
		final boolean inline = value.getQuery() != null && !value.getQuery().isBlank();
		if (file == inline) {
			throw new IllegalArgumentException(
					"Specify either sql (file) or query (inline SQL) for model: " + value.getName());
		}
		final String sql;
		if (file) {
			final File sqlFile = resolve(configurationFile, value.getSql());
			if (!sqlFile.isFile()) {
				throw new IllegalArgumentException(
						"SQL file of model " + value.getName() + " does not exist: " + sqlFile.getAbsolutePath());
			}
			try {
				sql = Files.readString(sqlFile.toPath(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new CommandException(
						"Failed to read SQL file of model " + value.getName() + ": " + sqlFile.getAbsolutePath(), e);
			}
		} else {
			sql = value.getQuery();
		}
		return new MigrationModel(value.getName(), value.getSchema(), stripStatementEnd(sql), materialization(value),
				value.getParameters());
	}

	/**
	 * A model is embedded in CREATE ... AS, so a trailing terminator is removed.
	 */
	private static String stripStatementEnd(final String sql) {
		String value = sql.strip();
		while (value.endsWith(";")) {
			value = value.substring(0, value.length() - 1).stripTrailing();
		}
		return value;
	}

	private static MigrationModel.Materialization materialization(final MigrationModelConfiguration.Model value) {
		if (value.getMaterialized() == null || value.getMaterialized().isBlank()) {
			return MigrationModel.Materialization.VIEW;
		}
		try {
			return MigrationModel.Materialization.valueOf(value.getMaterialized().trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unsupported materialized value for model " + value.getName() + ": "
					+ value.getMaterialized() + ". Use view or table.");
		}
	}

	private static MigrationModelSource source(final File configurationFile,
			final MigrationModelConfiguration.Source value) {
		if (value.getFile() != null && !value.getFile().isBlank()) {
			if (value.getTable() != null && !value.getTable().isBlank()) {
				throw new IllegalArgumentException("Specify either table or file for source: " + value.getName());
			}
			final File file = resolve(configurationFile, value.getFile());
			final boolean glob = value.getFile().contains("*") || value.getFile().contains("?");
			if (!glob && !file.isFile()) {
				throw new IllegalArgumentException(
						"File of source " + value.getName() + " does not exist: " + file.getAbsolutePath());
			}
			return new MigrationModelSource(value.getName(), value.getSchema(), null,
					file.getAbsolutePath().replace('\\', '/'), value.getFormat());
		}
		String schema = value.getSchema();
		String table = value.getTable();
		if (table != null && (schema == null || schema.isBlank())) {
			final String[] parts = table.split("\\.", -1);
			if (parts.length == 2) {
				schema = parts[0];
				table = parts[1];
			} else if (parts.length > 2) {
				throw new IllegalArgumentException("Source table must be table or schema.table: " + value.getTable()
						+ ". Specify schema separately when a name contains a dot.");
			}
		}
		return new MigrationModelSource(value.getName(), schema, table, null, value.getFormat());
	}

	private static MigrationModelUnitTest unitTest(final MigrationModelConfiguration.UnitTest value) {
		final Map<String, MigrationModelUnitTest.Fixture> given = new LinkedHashMap<>();
		for (final var entry : value.getGiven() == null ? Map.<String, MigrationModelConfiguration.Given>of().entrySet()
				: value.getGiven().entrySet()) {
			final var fixture = entry.getValue();
			if (fixture == null) {
				throw new IllegalArgumentException(
						"Fixture is empty for " + entry.getKey() + " in unit test: " + value.getName());
			}
			try {
				given.put(entry.getKey(),
						new MigrationModelUnitTest.Fixture(fixture.getColumns(), fixture.getRows(), fixture.getSql()));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Unit test " + value.getName() + ", given " + entry.getKey() + ": " + e.getMessage());
			}
		}
		if (value.getExpect() == null || value.getExpect().getRows() == null) {
			throw new IllegalArgumentException("expect.rows is required in unit test: " + value.getName());
		}
		final List<List<String>> expected = new ArrayList<>();
		for (final List<Object> row : value.getExpect().getRows()) {
			final List<String> canonical = new ArrayList<>();
			for (final Object cell : row) {
				canonical.add(cell == null ? null : String.valueOf(cell));
			}
			expected.add(canonical);
		}
		return new MigrationModelUnitTest(value.getName(), value.getModel(), given, expected, value.isOrdered());
	}

	private static File resolve(final File configurationFile, final String path) {
		final File file = new File(path);
		return file.isAbsolute() ? file : new File(configurationFile.getAbsoluteFile().getParentFile(), path);
	}

	private static <T> List<T> nullToEmpty(final List<T> values) {
		return values == null ? List.of() : values;
	}
}

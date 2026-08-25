/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.loader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/** Resolves a schema-file provider without a dependency on its dialect. */
public final class SchemaFileLoaderResolver {

	private SchemaFileLoaderResolver() {
	}

	public static SchemaFileLoader resolve(final Path file) {
		final Path normalized = normalize(file);
		final List<SchemaFileLoader> matches = ServiceLoader
				.load(SchemaFileLoader.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(loader -> loader.supports(normalized)).toList();
		if (matches.isEmpty()) {
			throw new IllegalArgumentException(
					"No schema file loader supports: " + normalized);
		}
		if (matches.size() > 1) {
			throw new IllegalStateException(
					"Multiple schema file loaders support: " + normalized
							+ " (" + matches.stream()
									.map(loader -> loader.getClass().getName())
									.toList() + ")");
		}
		return matches.get(0);
	}

	/** Loads a supported database or schema file as a Schema model. */
	public static Schema loadSchema(final Path file) throws IOException {
		final Path normalized = normalize(file);
		return resolve(normalized).loadSchema(normalized);
	}

	/** Loads a supported database or schema file as a Schema model. */
	public static Schema loadSchema(final File file) throws IOException {
		return loadSchema(Objects.requireNonNull(file, "file").toPath());
	}

	/** Loads one table from a supported database or schema file. */
	public static Table loadTable(final Path file, final String tableName)
			throws IOException {
		final Path normalized = normalize(file);
		final String normalizedTableName = Objects.requireNonNull(tableName,
				"tableName");
		return resolve(normalized).loadTable(normalized, normalizedTableName);
	}

	/** Loads one table from a supported database or schema file. */
	public static Table loadTable(final File file, final String tableName)
			throws IOException {
		return loadTable(Objects.requireNonNull(file, "file").toPath(),
				tableName);
	}

	private static Path normalize(final Path file) {
		return Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
	}
}

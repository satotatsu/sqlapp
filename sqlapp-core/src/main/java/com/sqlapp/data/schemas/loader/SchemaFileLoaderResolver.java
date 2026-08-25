/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.loader;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/** Resolves a schema-file provider without a dependency on its dialect. */
public final class SchemaFileLoaderResolver {

	private SchemaFileLoaderResolver() {
	}

	public static SchemaFileLoader resolve(final Path file) {
		final Path normalized = file.toAbsolutePath().normalize();
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
}

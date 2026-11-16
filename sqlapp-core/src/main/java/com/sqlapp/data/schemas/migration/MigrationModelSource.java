/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.migration;

/**
 * An input relation that models read but do not build: either an existing
 * table of the execution engine or a data file read by that engine.
 */
public record MigrationModelSource(String name, String schemaName, String tableName, String file, String format) {

	public MigrationModelSource {
		MigrationModel.requireName("Source name", name);
		schemaName = blankToNull(schemaName);
		tableName = blankToNull(tableName);
		file = blankToNull(file);
		format = blankToNull(format);
		if ((tableName == null) == (file == null)) {
			throw new IllegalArgumentException("Specify either table or file for source: " + name);
		}
		if (file != null && schemaName != null) {
			throw new IllegalArgumentException("A file source cannot have a schema: " + name);
		}
		if (tableName != null && format != null) {
			throw new IllegalArgumentException("format is only valid for a file source: " + name);
		}
	}

	public static MigrationModelSource table(final String name, final String schemaName, final String tableName) {
		return new MigrationModelSource(name, schemaName, tableName, null, null);
	}

	public static MigrationModelSource file(final String name, final String file, final String format) {
		return new MigrationModelSource(name, null, null, file, format);
	}

	public boolean isFile() {
		return file != null;
	}

	private static String blankToNull(final String value) {
		return value == null || value.isBlank() ? null : value;
	}
}

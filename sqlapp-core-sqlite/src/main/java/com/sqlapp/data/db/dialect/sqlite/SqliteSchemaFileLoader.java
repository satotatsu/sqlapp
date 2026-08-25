/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.loader.SchemaFileLoader;

/** Service provider for SQLite database files. */
public class SqliteSchemaFileLoader implements SchemaFileLoader {

	@Override
	public boolean supports(final Path file) {
		final String name = file.getFileName().toString()
				.toLowerCase(Locale.ROOT);
		return name.endsWith(".db") || name.endsWith(".sqlite")
				|| name.endsWith(".sqlite3");
	}

	@Override
	public Schema loadSchema(final Path file) throws IOException {
		return SqliteFileLoader.loadSchema(file);
	}

	@Override
	public Table loadTable(final Path file, final String tableName)
			throws IOException {
		return SqliteFileLoader.loadTable(file, tableName);
	}
}

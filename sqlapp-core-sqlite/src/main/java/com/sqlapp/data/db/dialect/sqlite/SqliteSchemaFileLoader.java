/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.loader.SchemaFileLoader;

/** Service provider for SQLite database files. */
public class SqliteSchemaFileLoader implements SchemaFileLoader {
	private static final byte[] SQLITE_HEADER =
			"SQLite format 3\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

	@Override
	public boolean supports(final Path file) {
		final String name = file.getFileName().toString()
				.toLowerCase(Locale.ROOT);
		if (name.endsWith(".db") || name.endsWith(".sqlite")
				|| name.endsWith(".sqlite3")) {
			return true;
		}
		if (!Files.isRegularFile(file)) {
			return false;
		}
		try (InputStream input = Files.newInputStream(file)) {
			return Arrays.equals(SQLITE_HEADER,
					input.readNBytes(SQLITE_HEADER.length));
		} catch (IOException e) {
			return false;
		}
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

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.loader.SchemaFileLoader;

/** Service provider for Access MDB and ACCDB files. */
public class MdbSchemaFileLoader implements SchemaFileLoader {

	@Override
	public boolean supports(final Path file) {
		final String name = file.getFileName().toString()
				.toLowerCase(Locale.ROOT);
		return name.endsWith(".mdb") || name.endsWith(".accdb");
	}

	@Override
	public Schema loadSchema(final Path file) throws IOException {
		return MdbFileLoader.loadSchema(file);
	}

	@Override
	public Table loadTable(final Path file, final String tableName)
			throws IOException {
		return MdbFileLoader.loadTable(file, tableName);
	}
}

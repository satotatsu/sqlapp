/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 */
package com.sqlapp.data.schemas.loader;

import java.io.IOException;
import java.nio.file.Path;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

/**
 * Provider for database or schema files handled outside JDBC.
 * <p>
 * Implementations may attach lazy row iterators to returned tables. Callers
 * which stop iteration before exhaustion must close an iterator implementing
 * {@link AutoCloseable}.
 * </p>
 */
public interface SchemaFileLoader {

	/** Returns whether this provider supports the specified file. */
	boolean supports(Path file);

	/** Loads the database file as a Schema model. */
	Schema loadSchema(Path file) throws IOException;

	/** Loads one table, including relationships needed by its metadata. */
	Table loadTable(Path file, String tableName) throws IOException;
}

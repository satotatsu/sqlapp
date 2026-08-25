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

/** Provider for database or schema files handled outside JDBC. */
public interface SchemaFileLoader {

	boolean supports(Path file);

	Schema loadSchema(Path file) throws IOException;

	Table loadTable(Path file, String tableName) throws IOException;
}

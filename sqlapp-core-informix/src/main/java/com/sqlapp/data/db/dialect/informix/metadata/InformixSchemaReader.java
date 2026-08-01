/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcSchemaReader;
import com.sqlapp.data.db.metadata.TableReader;

/** Informix JDBC schema reader. */
public class InformixSchemaReader extends JdbcSchemaReader {
	public InformixSchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new InformixTableReader(getDialect());
	}
}

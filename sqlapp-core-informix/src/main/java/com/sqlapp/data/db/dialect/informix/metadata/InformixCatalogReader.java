/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcCatalogReader;
import com.sqlapp.data.db.metadata.SchemaReader;

/** Informix JDBC catalog reader. */
public class InformixCatalogReader extends JdbcCatalogReader {
	public InformixCatalogReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new InformixSchemaReader(getDialect());
	}
}

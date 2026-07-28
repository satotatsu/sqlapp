/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;

/**
 * MariaDB 11.5 catalog reader.
 */
public class MariadbCatalog11_50Reader extends MariadbCatalog10_27Reader {

	public MariadbCatalog11_50Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new MariadbSchema11_50Reader(getDialect());
	}
}

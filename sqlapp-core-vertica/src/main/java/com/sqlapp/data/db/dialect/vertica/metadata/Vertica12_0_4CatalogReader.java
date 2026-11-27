/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;

/** Catalog reader for Vertica 12.0.4 and later. */
public class Vertica12_0_4CatalogReader extends Vertica11_1_1CatalogReader {

	public Vertica12_0_4CatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new Vertica12_0_4SchemaReader(this.getDialect());
	}
}

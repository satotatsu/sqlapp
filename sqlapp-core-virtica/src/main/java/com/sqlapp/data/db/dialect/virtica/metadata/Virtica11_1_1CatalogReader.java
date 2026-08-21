/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;

/** Catalog reader for Vertica 11.1.1 and later. */
public class Virtica11_1_1CatalogReader extends VirticaCatalogReader {

	public Virtica11_1_1CatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new Virtica11_1_1SchemaReader(this.getDialect());
	}
}

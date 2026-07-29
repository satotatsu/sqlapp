/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * Vertica CREATE TABLE.
 */
public class VirticaCreateTableFactory
		extends AbstractCreateTableFactory<VirticaSqlBuilder> {

	@Override
	protected void addCreateObject(final Table table,
			final VirticaSqlBuilder builder) {
		builder.create().table()
				.ifNotExists(getOptions().isCreateIfNotExists()).space()
				.name(table, getOptions().isDecorateSchemaName());
	}
}

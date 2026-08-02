/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTruncateTableFactory;
import com.sqlapp.data.schemas.Table;

/** Implements Access table truncation as DELETE because Access has no TRUNCATE. */
public class MdbTruncateTableFactory
		extends AbstractTruncateTableFactory<MdbSqlBuilder> {

	@Override
	protected void addTruncateTable(final Table table,
			final MdbSqlBuilder builder) {
		builder.delete().from();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		this.addTableComment(table, builder);
	}
}

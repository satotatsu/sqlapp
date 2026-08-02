/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/** Generates Access CREATE INDEX syntax, which has no IF NOT EXISTS clause. */
public class MdbCreateIndexFactory
		extends AbstractCreateIndexFactory<MdbSqlBuilder> {

	@Override
	protected void addUnique(final Index index, final Table table,
			final MdbSqlBuilder builder) {
		builder.unique(index.isUnique()).index().space();
	}
}

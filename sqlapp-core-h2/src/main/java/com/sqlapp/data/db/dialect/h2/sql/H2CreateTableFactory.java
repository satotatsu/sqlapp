/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.sql.CreateTableFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * H2 CREATE TABLE.
 */
public class H2CreateTableFactory extends CreateTableFactory {

	@Override
	protected void addCreateObject(final Table table,
			final AbstractSqlBuilder<?> builder) {
		builder.create().table()
				.ifNotExists(getOptions().isCreateIfNotExists()).space()
				.name(table, getOptions().isDecorateSchemaName());
	}
}

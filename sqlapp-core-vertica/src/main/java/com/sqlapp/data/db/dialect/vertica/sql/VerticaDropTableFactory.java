/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.sql.DropTableFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Vertica DROP TABLE.
 */
public class VerticaDropTableFactory extends DropTableFactory {

	@Override
	protected void addDropObject(final Table table, final AbstractSqlBuilder<?> builder) {
		builder.drop().table().ifExists(getOptions().isDropIfExists()).space().name(table,
				getOptions().isDecorateSchemaName());
	}
}

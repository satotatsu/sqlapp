/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * Oracle Database 23ai DROP TABLE factory.
 */
public class Oracle23aiDropTableFactory
		extends AbstractDropTableFactory<OracleSqlBuilder> {

	@Override
	protected void addDropObject(final Table table,
			final OracleSqlBuilder builder) {
		builder.drop().table()
				.ifExists(this.getOptions().isDropIfExists()).space();
		builder.name(table, this.getOptions().isDecorateSchemaName());
		if (this.getDialect().supportsDropCascade()) {
			builder.cascade().constraints();
		}
	}
}

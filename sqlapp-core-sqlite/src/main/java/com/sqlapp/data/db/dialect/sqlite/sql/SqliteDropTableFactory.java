/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * Retains shared dependency ordering while targeting the model's attached
 * database.
 */
public class SqliteDropTableFactory extends AbstractDropTableFactory<SqliteSqlBuilder> {
	@Override
	protected void addDropObject(Table table, SqliteSqlBuilder builder) {
		builder.drop().table().name(table, table.getSchemaName() != null);
	}
}

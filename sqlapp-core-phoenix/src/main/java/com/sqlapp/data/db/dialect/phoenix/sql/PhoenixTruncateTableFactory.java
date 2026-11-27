/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTruncateTableFactory;
import com.sqlapp.data.schemas.Table;

/** Deletes all rows using Phoenix DML rather than unsupported TRUNCATE DDL. */
public class PhoenixTruncateTableFactory extends AbstractTruncateTableFactory<PhoenixSqlBuilder> {
	@Override
	protected void addTruncateTable(final Table table, final PhoenixSqlBuilder builder) {
		builder.delete().from().name(table, getOptions().isDecorateSchemaName());
		addTableComment(table, builder);
	}
}

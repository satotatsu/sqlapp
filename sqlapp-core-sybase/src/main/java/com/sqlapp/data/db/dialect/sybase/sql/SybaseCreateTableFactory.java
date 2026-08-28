/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.sql;

import com.sqlapp.data.db.sql.CreateTableFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;

/** Emits explicit NULL because ASE may default unspecified columns to NOT NULL. */
public class SybaseCreateTableFactory extends CreateTableFactory {
	@Override
	protected void addColumnDefinition(final Column column,
			final AbstractSqlBuilder<?> builder) {
		super.addColumnDefinition(column, builder);
		if (!column.isNotNull()) {
			builder.space().null_();
		}
	}
}

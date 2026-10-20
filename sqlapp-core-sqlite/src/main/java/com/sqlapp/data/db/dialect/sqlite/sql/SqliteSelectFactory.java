/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.sql.SelectFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Key lookup using the canonical table columns for typed bind parameters. */
public class SqliteSelectFactory extends SelectFactory {
	@Override
	protected void addSelectConditionColumns(Table table, SqlSignature signature, AbstractSqlBuilder<?> builder) {
		SqliteKeyColumns.addWhere(table, signature, builder, this::getValueDefinitionSimple,
				this::addWhereColumnComment);
	}
}

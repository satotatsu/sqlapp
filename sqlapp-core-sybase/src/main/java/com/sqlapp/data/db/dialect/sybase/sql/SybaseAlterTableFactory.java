/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sybase.sql;

import java.util.List;

import com.sqlapp.data.db.sql.AlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Generates ASE's {@code ALTER TABLE ... ADD column definition} syntax. */
public class SybaseAlterTableFactory extends AlterTableFactory {
	@Override
	protected void addAddColumn(final Table originalTable, final Table table,
			final DbObjectDifference difference, final List<SqlOperation> result) {
		final Column column = difference.getTarget(Column.class);
		final AbstractSqlBuilder<?> builder = createSqlBuilder();
		builder.alter().table().name(table, this.getOptions().isDecorateSchemaName());
		builder.add();
		builder.name(column).space()
				.definition(column, this.getTableOptions().getWithColumnRemarks().test(column));
		if (!column.isNotNull()) {
			builder.space().null_();
		}
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
	}
}

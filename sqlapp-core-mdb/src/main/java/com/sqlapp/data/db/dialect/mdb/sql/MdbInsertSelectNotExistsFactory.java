/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractInsertSelectNotExistsTableFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/** Generates UCanAccess-compatible conditional INSERT SQL. */
public class MdbInsertSelectNotExistsFactory
		extends AbstractInsertSelectNotExistsTableFactory<MdbSqlBuilder> {
	@Override
	protected String getColumnParameterExpression(final Column column,
			final String defaultValue) {
		return MdbParameterExpression.of(column, defaultValue);
	}

	@Override
	protected void addInsertSelectTable(final Table table,
			final SqlSignature signature, final MdbSqlBuilder builder) {
		builder.insert().into();
		builder.name(table, getOptions().isDecorateSchemaName());
		addTableComment(table, builder);
		final int[] count = new int[1];
		builder.lineBreak().brackets(() -> {
			builder.indent(() -> {
				for (final Column column : table.getColumns()) {
					if (!isInsertable(column) || isAutoIncrementColumn(column)
							|| isFormulaColumn(column)) {
						continue;
					}
					builder.lineBreak().comma(count[0] > 0)
							.space(2, count[0] == 0).name(column);
					count[0]++;
				}
			});
			builder.lineBreak();
		});
		builder.lineBreak().select();
		count[0] = 0;
		builder.indent(() -> {
			for (final Column column : table.getColumns()) {
				if (!isInsertable(column) || isAutoIncrementColumn(column)
						|| isFormulaColumn(column)) {
					continue;
				}
				builder.lineBreak().comma(count[0] > 0).space(2, count[0] == 0)
						._add(getValueDefinitionSimple(column));
				count[0]++;
			}
		});
		builder.lineBreak().from().space()._add("(VALUES(0))");
		builder.lineBreak().where().not().exists().lineBreak();
		builder.brackets(true, () -> {
			builder.select().space()._add("1");
			builder.lineBreak().from().name(table,
					getOptions().isDecorateSchemaName());
			addKeyColumnsCondition(table, signature, builder);
		});
	}
}

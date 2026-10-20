/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.sql.SelectForAppFactory;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Native SQLite application-query pagination, preserving shared filters and
 * ordering.
 */
public class SqliteSelectForAppFactory extends SelectForAppFactory {
	@Override
	protected void addOffsetRowsOnly(Table table, AbstractSqlBuilder<?> builder) {
		String rows = toIsNotEmptyExpression(ParameterDefinition.ROW_KEY_PARANETER_NAME);
		String offset = toIsNotEmptyExpression(ParameterDefinition.OFFSET_KEY_PARANETER_NAME);
		String dataQuery = "!" + ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME;
		builder.lineBreak()._add(toIfExpression(dataQuery + " && " + rows));
		builder.lineBreak()._add("LIMIT /*" + ParameterDefinition.ROW_KEY_PARANETER_NAME + "*/1");
		builder.lineBreak()._add(getEndIfExpression());
		// SQLite requires LIMIT before OFFSET, including when only an offset is
		// supplied.
		builder.lineBreak()._add(toIfExpression(dataQuery + " && !(" + rows + ") && " + offset));
		builder.lineBreak()._add("LIMIT -1");
		builder.lineBreak()._add(getEndIfExpression());
		builder.lineBreak()._add(toIfExpression(dataQuery + " && " + offset));
		builder.lineBreak()._add("OFFSET /*" + ParameterDefinition.OFFSET_KEY_PARANETER_NAME + "*/0");
		builder.lineBreak()._add(getEndIfExpression());
	}
}

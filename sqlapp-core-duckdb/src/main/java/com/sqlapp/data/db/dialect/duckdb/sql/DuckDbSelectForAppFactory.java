/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSelectForAppFactory;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.schemas.Table;

/**
 * Native paging avoids scrollable result sets, which DuckDB JDBC does not
 * support.
 */
public class DuckDbSelectForAppFactory extends AbstractSelectForAppFactory<DuckDbSqlBuilder> {

	@Override
	protected void addOffsetRowsOnly(final Table table, final DuckDbSqlBuilder builder) {
		final String count = ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME;
		final String row = ParameterDefinition.ROW_KEY_PARANETER_NAME;
		final String offset = ParameterDefinition.OFFSET_KEY_PARANETER_NAME;
		builder.lineBreak()._add(toIfExpression("!" + count));
		builder.lineBreak()._add(toIfIsNotEmptyExpression(row));
		builder.lineBreak()._add("LIMIT /*" + row + "*/1");
		builder.lineBreak()._add(getEndIfExpression());
		builder.lineBreak()._add(toIfIsNotEmptyExpression(offset));
		builder.lineBreak()._add("OFFSET /*" + offset + "*/0");
		builder.lineBreak()._add(getEndIfExpression());
		builder.lineBreak()._add(getEndIfExpression());
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropFunctionFactory;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;

/**
 * {@code DROP MACRO [TABLE]} for DuckDB.
 */
public class DuckDbDropFunctionFactory extends AbstractDropFunctionFactory<DuckDbSqlBuilder> {

	@Override
	protected void addDropObject(final Function obj, final DuckDbSqlBuilder builder) {
		builder.drop().space()._add("MACRO");
		if (obj.getFunctionType() == FunctionType.Table) {
			builder.space()._add("TABLE");
		}
		builder.space().name(obj, getOptions().isDecorateSchemaName());
	}
}

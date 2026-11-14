/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbMacros;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.util.CommonUtils;

/**
 * {@code CREATE MACRO} for DuckDB. A stored definition is emitted as is;
 * otherwise a single-overload macro is built from the arguments and the
 * statement (the macro body). Only scalar and table macros exist in DuckDB.
 */
public class DuckDbCreateFunctionFactory extends AbstractCreateFunctionFactory<DuckDbSqlBuilder> {

	@Override
	protected void addCreateObject(final Function obj, final DuckDbSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getDefinition())) {
			builder._add(String.join(System.lineSeparator(), obj.getDefinition()));
			return;
		}
		final FunctionType type = obj.getFunctionType();
		if (type != null && type != FunctionType.Scalar && type != FunctionType.Table) {
			throw new IllegalArgumentException("DuckDB supports only scalar and table macros: " + obj.getName());
		}
		if (CommonUtils.isEmpty(obj.getStatement())) {
			throw new IllegalArgumentException("DuckDB macro requires a definition or a statement: " + obj.getName());
		}
		final List<String> names = new ArrayList<>();
		final List<String> types = new ArrayList<>();
		for (final NamedArgument argument : obj.getArguments()) {
			names.add(argument.getName());
			types.add(argument.getDataTypeName());
		}
		final String body = String.join(System.lineSeparator(), obj.getStatement());
		builder._add(DuckDbMacros.createMacro(getDialect(),
				getOptions().isDecorateSchemaName() ? obj.getSchemaName() : null, obj.getName(),
				type == FunctionType.Table, List.of(new DuckDbMacros.Overload(names, types, body))));
	}

	@Override
	protected void addOptions(final Function obj, final List<SqlOperation> sqlList) {
		if (obj.getRemarks() != null) {
			DuckDbSqlFactories.addComment(this, obj, sqlList);
		}
	}
}

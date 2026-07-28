/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;

/**
 * CREATE FUNCTION factory for Oracle Database 21c SQL macros.
 */
public class Oracle21cCreateFunctionFactory extends Oracle19cCreateFunctionFactory {

	@Override
	protected void addSqlMacroClause(final Function function,
			final OracleSqlBuilder builder) {
		builder.space()._add("SQL_MACRO")._add("(");
		builder._add(function.getFunctionType() == FunctionType.Table
				? "TABLE" : "SCALAR");
		builder._add(")");
	}

	@Override
	protected boolean supportsScalarSqlMacro() {
		return true;
	}
}

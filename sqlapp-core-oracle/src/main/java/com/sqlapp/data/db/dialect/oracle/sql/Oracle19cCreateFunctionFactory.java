/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.dialect.oracle.metadata.OracleFunctionReader;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE FUNCTION factory for Oracle Database 19c table SQL macros.
 */
public class Oracle19cCreateFunctionFactory extends OracleCreateFunctionFactory {

	@Override
	protected void addCreateObject(final Function function,
			final OracleSqlBuilder builder) {
		if (!isSqlMacro(function) || !isEmpty(function.getDefinition())) {
			super.addCreateObject(function, builder);
			return;
		}
		if (!supportsScalarSqlMacro()
				&& function.getFunctionType() != FunctionType.Table) {
			throw new IllegalArgumentException(
					"Oracle 19c supports only TABLE SQL macros: "
							+ function.getName());
		}
		builder.create().or().replace().function();
		builder.name(function, this.getOptions().isDecorateSchemaName());
		if (!CommonUtils.isEmpty(function.getArguments())) {
			builder.arguments(function.getArguments());
		}
		builder.lineBreak().return_().space()._add(function.getReturning());
		addSqlMacroClause(function, builder);
		builder.lineBreak()._add(function.getStatement());
	}

	protected void addSqlMacroClause(final Function function,
			final OracleSqlBuilder builder) {
		builder.space()._add("SQL_MACRO");
	}

	protected boolean supportsScalarSqlMacro() {
		return false;
	}

	private boolean isSqlMacro(final Function function) {
		return Boolean.parseBoolean(
				function.getSpecifics().get(OracleFunctionReader.SQL_MACRO));
	}
}

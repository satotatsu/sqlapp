/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.FunctionType;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.CommonUtils;

public class Db2CreateFunctionFactory extends AbstractCreateFunctionFactory<Db2SqlBuilder> {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createSql(Function function) {
		return Db2RoutineSql.create(function, getDialect(), () -> super.createSql(function),
				(sql, type) -> createOperation(sql, type, function), getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addCreateObject(Function function, Db2SqlBuilder builder) {
		if (!CommonUtils.isEmpty(function.getDefinition())) {
			builder._add(function.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(function.getStatement())) {
			throw new IllegalArgumentException(
					"Db2 function requires a definition or statement: " + function.getName());
		}
		if (function.getLanguage() != null && !"SQL".equalsIgnoreCase(function.getLanguage())) {
			throw new IllegalArgumentException(
					"External Db2 function requires complete definition: " + function.getName());
		}
		builder.create().function().space();
		Db2RoutineSql.name(builder, function, false, getOptions().isDecorateSchemaName());
		builder.space()._add("(");
		int count = 0;
		for (NamedArgument argument : function.getArguments()) {
			if (function.getFunctionType() == FunctionType.Row && argument.getDefaultValue() != null)
				throw new IllegalArgumentException("Db2 RETURNS ROW function cannot have parameter defaults");
			if (argument.getDirection() != null && argument.getDirection() != ParameterDirection.Input) {
				throw new IllegalArgumentException("Db2 SQL function output parameters require complete definition");
			}
			builder.comma(count++ > 0);
			Db2RoutineArgumentSql.append(argument.clone().setDirection((ParameterDirection) null), builder);
		}
		builder._add(")").lineBreak().returns().space();
		if (function.getFunctionType() == FunctionType.Table || function.getFunctionType() == FunctionType.Row) {
			var table = function.getReturning().getTable();
			if (table == null || table.getColumns().isEmpty()) {
				throw new IllegalArgumentException("Db2 table/row function requires return columns");
			}
			builder._add(function.getFunctionType() == FunctionType.Table ? "TABLE (" : "ROW (");
			count = 0;
			for (Column column : table.getColumns()) {
				builder.comma(count++ > 0);
				Db2RoutineSql.identifier(builder, column.getName());
				builder.space();
				if (column.getSpecifics().containsKey("TYPENAME"))
					builder._add(Db2RoutineArgumentSql.renderType(column.getSpecifics(), column.getName(), builder));
				else
					builder.typeDefinition(column.getDataType(), column.getDataTypeName(), column.getLength(),
							column.getScale());
			}
			builder._add(")");
		} else if (function.getFunctionType() == null || function.getFunctionType() == FunctionType.Scalar) {
			if (function.getReturning().getDataType() == null && function.getReturning().getDataTypeName() == null) {
				throw new IllegalArgumentException("Db2 scalar function requires a return type");
			}
			if (function.getReturning().getSpecifics().containsKey("TYPENAME"))
				builder._add(Db2RoutineArgumentSql.renderType(function.getReturning().getSpecifics(),
						function.getName(), builder));
			else
				builder._add(function.getReturning());
		} else {
			throw new IllegalArgumentException("Db2 aggregate function requires complete definition");
		}
		if (function.getSpecificName() != null) {
			builder.lineBreak().specific().space();
			Db2RoutineSql.identifier(builder, function.getSpecificName());
		}
		builder.lineBreak().language().space()._add("SQL");
		if (function.getDeterministic() != null) {
			builder.lineBreak()._add(function.getDeterministic() ? "DETERMINISTIC" : "NOT DETERMINISTIC");
		}
		if (function.getSqlDataAccess() != null)
			builder.lineBreak()._add(function.getSqlDataAccess());
		if (function.getOnNullCall() != null)
			builder.lineBreak()._add(function.getOnNullCall());
		builder.lineBreak()._add(function.getStatement());
	}
}

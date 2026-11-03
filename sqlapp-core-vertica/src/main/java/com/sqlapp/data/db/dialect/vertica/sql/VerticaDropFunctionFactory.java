/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropFunctionFactory;
import com.sqlapp.data.schemas.Function;

/** Drops the exact function overload using modeled argument types. */
public class VerticaDropFunctionFactory extends AbstractDropFunctionFactory<VerticaSqlBuilder> {

	@Override
	protected void addDropObject(Function function, VerticaSqlBuilder builder) {
		var kind = com.sqlapp.data.db.dialect.vertica.util.VerticaFunctionKind.of(function);
		if (kind.isLoad() && getOptions().isDropIfExists())
			throw new IllegalArgumentException(
					"Vertica load-function DROP IF EXISTS is not qualified; set dropIfExists=false for " + kind);
		if (kind.isLoad() && !function.getArguments().isEmpty())
			throw new IllegalArgumentException(
					"Vertica load-function DROP requires the empty catalog signature: " + function.getName());
		builder.drop().space()._add(kind.dropKeyword()).ifExists(getOptions().isDropIfExists()).space();
		builder.name(function, getOptions().isDecorateSchemaName())._add('(');
		if (kind == com.sqlapp.data.db.dialect.vertica.util.VerticaFunctionKind.TRANSFORM
				&& "Any".equalsIgnoreCase(function.getSpecifics().get("FUNCTION_ARGUMENT_TYPE", String.class))) {
			if (function.getArguments().size() > 1 || !function.getArguments().isEmpty()
					&& !"Any".equalsIgnoreCase(function.getArguments().get(0).getDataTypeName()))
				throw new IllegalArgumentException("Polymorphic transform DROP requires a consistent Any signature");
			builder._add(')');
			return;
		}
		boolean first = true;
		for (var argument : function.getArguments()) {
			if (argument.getDataType() == null
					&& (argument.getDataTypeName() == null || argument.getDataTypeName().isBlank()))
				throw new IllegalArgumentException(
						"Vertica function DROP requires argument types: " + function.getName());
			if (argument.getDataType() == com.sqlapp.data.db.datatype.DataType.OTHER
					|| argument.getDataType() == com.sqlapp.data.db.datatype.DataType.DOMAIN)
				throw new IllegalArgumentException(
						"Vertica function DROP requires resolved native argument types: " + function.getName());
			if (argument.getDirection() != com.sqlapp.jdbc.sql.ParameterDirection.Input)
				throw new IllegalArgumentException(
						"Vertica function DROP requires input arguments: " + function.getName());
			if (!first)
				builder.comma().space();
			var type = getDialect().createSqlBuilder();
			type.typeDefinition(argument);
			builder._add(type.toString().replaceAll("\\([^()]*\\)", ""));
			first = false;
		}
		builder._add(')');
	}
}

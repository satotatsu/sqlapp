/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropFunctionFactory;
import com.sqlapp.data.schemas.Function;

/** Drops the exact function overload using modeled argument types. */
public class VirticaDropFunctionFactory extends AbstractDropFunctionFactory<VirticaSqlBuilder> {

	@Override
	protected void addDropObject(Function function, VirticaSqlBuilder builder) {
		builder.drop().function().ifExists(getOptions().isDropIfExists());
		builder.name(function, getOptions().isDecorateSchemaName())._add('(');
		boolean first = true;
		for (var argument : function.getArguments()) {
			if (argument.getDataType() == null
					&& (argument.getDataTypeName() == null || argument.getDataTypeName().isBlank()))
				throw new IllegalArgumentException(
						"Vertica function DROP requires argument types: " + function.getName());
			if (argument.getDirection() != com.sqlapp.jdbc.sql.ParameterDirection.Input)
				throw new IllegalArgumentException(
						"Vertica function DROP requires input arguments: " + function.getName());
			if (!first)
				builder.comma().space();
			builder.typeDefinition(argument);
			first = false;
		}
		builder._add(')');
	}
}

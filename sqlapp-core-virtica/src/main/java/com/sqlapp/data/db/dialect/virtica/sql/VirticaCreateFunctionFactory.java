/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.util.CommonUtils;

/**
 * Vertica SQL function generation; UDx recreation requires a complete
 * definition.
 */
public class VirticaCreateFunctionFactory extends AbstractCreateFunctionFactory<VirticaSqlBuilder> {
	@Override
	protected void addCreateObject(Function function, VirticaSqlBuilder builder) {
		if (!CommonUtils.isEmpty(function.getDefinition())) {
			if (function.getDefinition().stream().allMatch(s -> s == null || s.isBlank()))
				throw new IllegalArgumentException("Vertica function CREATE definition must not be blank");
			builder._add(function.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(function.getStatement()))
			throw new IllegalArgumentException(
					"Vertica function requires RETURN body or complete CREATE definition: " + function.getName());
		String body = String.join("\n", function.getStatement()).trim();
		if (!body.matches("(?is)^RETURN\\b.+"))
			throw new IllegalArgumentException(
					"Vertica SQL function requires RETURN body; supply complete CREATE for UDx: " + function.getName());
		if (function.getReturning().getDataType() == null && (function.getReturning().getDataTypeName() == null
				|| function.getReturning().getDataTypeName().isBlank()))
			throw new IllegalArgumentException("Vertica SQL function requires return type: " + function.getName());
		for (var argument : function.getArguments()) {
			if (argument.getDataType() == null
					&& (argument.getDataTypeName() == null || argument.getDataTypeName().isBlank()))
				throw new IllegalArgumentException(
						"Vertica SQL function argument requires a type: " + argument.getName());
			if (argument.getName() == null || argument.getName().isBlank()
					|| argument.getDirection() != com.sqlapp.jdbc.sql.ParameterDirection.Input)
				throw new IllegalArgumentException(
						"Vertica SQL function requires named input arguments: " + function.getName());
		}
		builder.create().function().name(function, getOptions().isDecorateSchemaName())
				.arguments(function.getArguments());
		builder.lineBreak()._add("RETURN ")._add(function.getReturning());
		builder.lineBreak()._add("AS BEGIN").lineBreak()._add(body);
		if (!body.endsWith(";"))
			builder._add(";");
		builder.lineBreak()._add("END;");
	}
}

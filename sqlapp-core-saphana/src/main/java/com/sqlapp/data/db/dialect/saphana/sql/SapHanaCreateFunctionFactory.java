/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.CommonUtils;

/**
 * SQLScript scalar and table-valued functions using the shared routine model.
 */
public class SapHanaCreateFunctionFactory extends AbstractCreateFunctionFactory<SapHanaSqlBuilder> {
	@Override
	protected void addCreateObject(final Function function, final SapHanaSqlBuilder builder) {
		if (!CommonUtils.isEmpty(function.getDefinition())) {
			super.addCreateObject(function, builder);
			return;
		}
		builder.create().function().name(function, getOptions().isDecorateSchemaName())._add("(");
		int position = 0;
		for (NamedArgument argument : function.getArguments()) {
			if (argument.getDirection() == ParameterDirection.Output) {
				continue;
			}
			if (argument.getDirection() == ParameterDirection.Inout) {
				throw new IllegalArgumentException("SAP HANA function arguments must be IN: " + argument.getName());
			}
			builder.comma(position++ > 0).argument(argument);
		}
		builder._add(")").lineBreak().returns().space();
		final var outputs = function.getArguments().stream().filter(a -> a.getDirection() == ParameterDirection.Output)
				.toList();
		if (function.getReturning() != null && function.getReturning().getTable() != null || outputs.isEmpty()) {
			builder._add(function.getReturning());
		} else {
			position = 0;
			for (NamedArgument output : outputs) {
				builder.comma(position++ > 0).name(output.getName()).space()
						.addTypeDefinition(new com.sqlapp.data.schemas.Column().setDataType(output.getDataType())
								.setDataTypeName(output.getDataTypeName()).setLength(output.getLength())
								.setScale(output.getScale()));
			}
		}
		SapHanaRoutineBody.append(toString(function.getStatement()), builder);
	}
}

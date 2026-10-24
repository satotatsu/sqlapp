/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateFunctionFactory;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.util.CommonUtils;

/**
 * Legacy external functions, including return-parameter and FREE_IT metadata.
 */
public class FirebirdCreateFunctionFactory extends AbstractCreateFunctionFactory<FirebirdSqlBuilder> {
	@Override
	protected void addOtherDefinitions(Function obj, java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		if (obj.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(obj, com.sqlapp.data.db.sql.SqlType.SET_COMMENT));
	}

	@Override
	protected void addCreateObject(Function function, FirebirdSqlBuilder builder) {
		if (!CommonUtils.isEmpty(function.getDefinition())) {
			builder._add(function.getDefinition());
			return;
		}
		String module = function.getSpecifics().get("firebird.udf.module");
		String entry = function.getSpecifics().get("firebird.udf.entrypoint");
		if (module == null || module.isBlank() || entry == null || entry.isBlank())
			throw new IllegalArgumentException(
					"Legacy Firebird function requires module and entrypoint or complete definition");
		builder._add("DECLARE EXTERNAL FUNCTION ").name(function.getName()).lineBreak();
		for (int i = 0; i < function.getArguments().size(); i++) {
			var argument = function.getArguments().get(i);
			builder.comma(i > 0);
			if ("CSTRING".equals(argument.getDataTypeName()))
				builder._add("CSTRING(")._add(argument.getLength())._add(")");
			else if (argument.getDataType() == com.sqlapp.data.db.datatype.DataType.BLOB
					|| argument.getDataType() == com.sqlapp.data.db.datatype.DataType.CLOB)
				builder._add("BLOB");
			else
				builder.typeDefinition(argument.getDataType(), argument.getDataTypeName(), argument.getLength(),
						argument.getScale());
			if (argument.getCharacterSet() != null)
				builder._add(" CHARACTER SET ").name(argument.getCharacterSet());
			mechanism(Integer.parseInt(argument.getSpecifics().getOrDefault("firebird.udf.mechanism", "1")), false,
					builder);
		}
		builder.lineBreak().returns().space();
		int returnPosition = Integer.parseInt(function.getSpecifics().getOrDefault("firebird.udf.returnPosition", "0"));
		if (returnPosition > 0) {
			if (returnPosition > function.getArguments().size())
				throw new IllegalArgumentException("Invalid UDF return parameter position");
			if (Math.abs(Integer.parseInt(function.getArguments().get(returnPosition - 1).getSpecifics()
					.getOrDefault("firebird.udf.mechanism", "1"))) == 4)
				throw new IllegalArgumentException("SCALAR_ARRAY cannot be used as a UDF return parameter");
			builder._add("PARAMETER ")._add(returnPosition);
		} else {
			if ("CSTRING".equals(function.getReturning().getDataTypeName()))
				builder._add("CSTRING(")._add(function.getReturning().getLength())._add(")");
			else
				builder._add(function.getReturning());
			mechanism(Integer.parseInt(function.getSpecifics().getOrDefault("firebird.udf.returnMechanism", "0")), true,
					builder);
		}
		builder.lineBreak()._add("ENTRY_POINT ").sqlChar(entry)._add(" MODULE_NAME ").sqlChar(module);
	}

	private void mechanism(int mechanism, boolean returning, FirebirdSqlBuilder builder) {
		int code = Math.abs(mechanism);
		switch (code) {
		case 0 -> builder._add(" BY VALUE");
		case 1, 3 -> {
		}
		case 2 -> builder._add(" BY DESCRIPTOR");
		case 4 -> {
			if (returning)
				throw new IllegalArgumentException("SCALAR_ARRAY is only valid for a UDF input argument");
			builder._add(" BY SCALAR_ARRAY");
		}
		case 5 -> builder._add(" NULL");
		default -> throw new IllegalArgumentException("Unsupported Firebird UDF argument mechanism: " + mechanism);
		}
		if (mechanism < 0) {
			if (!returning)
				throw new IllegalArgumentException("FREE_IT is only valid for a UDF return value");
			builder._add(" FREE_IT");
		}
	}
}

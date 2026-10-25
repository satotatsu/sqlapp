/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.NamedArgument;

/**
 * Catalog argument types must retain their resolved identity and string units.
 */
final class Db2RoutineArgumentSql {
	private Db2RoutineArgumentSql() {
	}

	static void append(NamedArgument argument, Db2SqlBuilder builder) {
		var fields = argument.getSpecifics();
		if (!fields.containsKey("TYPENAME")) {
			builder.argument(argument);
			appendDefault(argument, builder);
			return;
		}
		String type = renderType(fields, argument.getName(), builder);
		if (argument.getDirection() != null)
			builder._add(argument.getDirection()).space();
		if (argument.getName() != null) {
			Db2RoutineSql.identifier(builder, argument.getName());
			builder.space();
		}
		builder._add(type);
		appendDefault(argument, builder);
	}

	static String renderType(java.util.Map<String, String> fields, String objectName, Db2SqlBuilder builder) {
		String locator = fields.get("LOCATOR");
		if (locator != null && !"N".equals(locator))
			throw new IllegalArgumentException("Db2 locator type requires complete routine definition: " + objectName);
		String schema = fields.get("TYPESCHEMA"), module = fields.get("TYPEMODULENAME");
		String type;
		if (schema != null && (!"SYSIBM".equals(schema) || module != null)) {
			type = Db2CreationContext.identifier(schema) + "."
					+ (module == null ? "" : Db2CreationContext.identifier(module) + ".")
					+ Db2CreationContext.identifier(fields.get("TYPENAME"));
		} else {
			if (module != null)
				throw new IllegalArgumentException("Db2 module argument type requires its schema");
			type = Db2NativeTypeSql.render(builder.getDialect(), fields, objectName);
		}
		return type;
	}

	private static void appendDefault(NamedArgument argument, Db2SqlBuilder builder) {
		if (argument.getDefaultValue() == null)
			return;
		if (!(builder.getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110)
				|| (argument.getDirection() != null
						&& argument.getDirection() != com.sqlapp.jdbc.sql.ParameterDirection.Input)
				|| argument.getDefaultValue().isBlank() || argument.getDefaultValue().indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"Db2 argument default requires an input parameter on LUW 11.1 or later: " + argument.getName());
		builder.space()._add("DEFAULT").space()._add(argument.getDefaultValue());
	}
}

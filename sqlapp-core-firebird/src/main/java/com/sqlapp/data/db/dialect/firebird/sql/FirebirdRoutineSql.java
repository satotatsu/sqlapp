/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;

final class FirebirdRoutineSql {
	static FirebirdSqlBuilder argumentComment(com.sqlapp.data.schemas.ArgumentRoutine<?> routine,
			com.sqlapp.data.schemas.NamedArgument argument, FirebirdSqlBuilder builder) {
		boolean function = routine instanceof com.sqlapp.data.schemas.Function;
		if (builder.getDialect().compareTo(function ? com.sqlapp.data.db.dialect.firebird.DialectHolder.defaultDialect30
				: com.sqlapp.data.db.dialect.firebird.DialectHolder.defaultDialect20) < 0)
			throw new IllegalArgumentException(
					"Parameter comments require Firebird " + (function ? "3.0" : "2.0") + " or later");
		builder._add(function ? "COMMENT ON FUNCTION PARAMETER " : "COMMENT ON PARAMETER ").name(routine.getName())
				._add(".").name(argument.getName())._add(" IS ");
		if (argument.getRemarks() == null)
			builder._add("NULL");
		else
			builder.sqlChar(argument.getRemarks());
		return builder;
	}

	static void addSecurity(java.util.Map<String, String> specifics, FirebirdSqlBuilder builder) {
		String security = specifics.get("firebird.sqlSecurity");
		if (security == null)
			return;
		addSecurity(switch (security) {
		case "DEFINER" -> com.sqlapp.data.schemas.SqlSecurity.Definer;
		case "INVOKER" -> com.sqlapp.data.schemas.SqlSecurity.Invoker;
		default -> throw new IllegalArgumentException("firebird.sqlSecurity must be DEFINER or INVOKER");
		}, builder);
	}

	static boolean addExternal(java.util.Map<String, String> specifics, FirebirdSqlBuilder builder) {
		String engine = specifics.get("firebird.external.engine");
		String entry = specifics.get("firebird.external.entrypoint");
		if (engine == null && entry == null)
			return false;
		if (engine == null || engine.isBlank() || entry == null || entry.isBlank())
			throw new IllegalArgumentException("Firebird external routine requires both engine and entrypoint");
		if (builder.getDialect().compareTo(com.sqlapp.data.db.dialect.firebird.DialectHolder.defaultDialect30) < 0)
			throw new IllegalArgumentException("External routine engines require Firebird 3.0 or later");
		builder.lineBreak()._add("EXTERNAL NAME ").sqlChar(entry).space()._add("ENGINE ").name(engine);
		return true;
	}

	static void addSecurity(com.sqlapp.data.schemas.SqlSecurity security, FirebirdSqlBuilder builder) {
		if (security == null)
			return;
		if (builder.getDialect().compareTo(com.sqlapp.data.db.dialect.firebird.DialectHolder.defaultDialect40) < 0)
			throw new IllegalArgumentException("SQL SECURITY requires Firebird 4.0 or later");
		builder.lineBreak()._add("SQL SECURITY ")
				._add(security == com.sqlapp.data.schemas.SqlSecurity.Definer ? "DEFINER" : "INVOKER");
	}

	private FirebirdRoutineSql() {
	}

	static void addBody(java.util.List<String> statement, FirebirdSqlBuilder builder) {
		String body = statement == null ? "" : String.join("\n", statement).trim();
		if (body.isEmpty()) {
			throw new IllegalArgumentException("Firebird PSQL routine requires a statement or complete definition");
		}
		builder.lineBreak();
		if (!body.matches("(?is)^AS\\b.*")) {
			builder.as().lineBreak();
		}
		builder._add(body);
	}
}

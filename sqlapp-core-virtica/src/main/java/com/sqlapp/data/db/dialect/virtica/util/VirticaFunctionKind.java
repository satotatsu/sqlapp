package com.sqlapp.data.db.dialect.virtica.util;

import java.util.Locale;
import com.sqlapp.data.schemas.Function;

/** Interprets the stable USER_FUNCTIONS.PROCEDURE_TYPE catalog field. */
public enum VirticaFunctionKind {
	SCALAR("FUNCTION", false), TRANSFORM("TRANSFORM FUNCTION", false), AGGREGATE("AGGREGATE FUNCTION", false),
	ANALYTIC("ANALYTIC FUNCTION", false), PARSER("PARSER", true), FILTER("FILTER", true), SOURCE("SOURCE", true);

	private final String sql;
	private final boolean load;

	VirticaFunctionKind(String sql, boolean load) {
		this.sql = sql;
		this.load = load;
	}

	public String dropKeyword() {
		return sql;
	}

	public boolean isLoad() {
		return load;
	}

	public static VirticaFunctionKind of(Function function) {
		String type = function.getSpecifics().get("PROCEDURE_TYPE", String.class);
		if (type == null || type.isBlank())
			return SCALAR;
		return switch (type.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT)) {
		case "USER DEFINED FUNCTION" -> SCALAR;
		case "USER DEFINED TRANSFORM", "USER DEFINED TRANSFORM FUNCTION" -> TRANSFORM;
		case "USER DEFINED AGGREGATE", "USER DEFINED AGGREGATE FUNCTION" -> AGGREGATE;
		case "USER DEFINED ANALYTIC", "USER DEFINED ANALYTIC FUNCTION" -> ANALYTIC;
		case "USER DEFINED PARSER" -> PARSER;
		case "USER DEFINED FILTER" -> FILTER;
		case "USER DEFINED SOURCE" -> SOURCE;
		default -> throw new IllegalArgumentException("Unsupported Vertica PROCEDURE_TYPE: " + type);
		};
	}
}

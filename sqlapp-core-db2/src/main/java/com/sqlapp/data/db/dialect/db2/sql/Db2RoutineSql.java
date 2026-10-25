/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Shared replay for native SQL routines; full definitions remain authoritative.
 */
final class Db2RoutineSql {
	private Db2RoutineSql() {
	}

	static List<SqlOperation> create(Routine<?> routine, Dialect dialect, Supplier<List<SqlOperation>> modeled,
			BiFunction<String, SqlType, SqlOperation> operation, boolean decorate) {
		if (!(dialect instanceof Db2_1110))
			return modeled.get();
		List<SqlOperation> result = new ArrayList<>();
		for (String sql : Db2CreationContext.sql(routine.getSpecifics().get("QUALIFIER"),
				routine.getSpecifics().get("FUNC_PATH")))
			result.add(operation.apply(sql, SqlType.SET_SEARCH_PATH_TO_SCHEMA));
		if (!routine.getDefinition().isEmpty()) {
			for (String sql : Db2DefinitionSql.statements(routine.getDefinition(), dialect)) {
				validateText(sql, routine);
				result.add(operation.apply(sql, SqlType.CREATE));
			}
		} else {
			if (routine.getStatement().isEmpty())
				throw unsupported(routine);
			if ("PL/SQL".equalsIgnoreCase(routine.getSpecifics().get("DIALECT")))
				throw unsupported(routine);
			Db2DefinitionSql.text(routine.getStatement());
			result.addAll(modeled.get());
		}
		if (routine.getRemarks() != null && !routine.getRemarks().isEmpty()) {
			Db2SqlBuilder builder = new Db2SqlBuilder(dialect);
			builder._add("COMMENT ON SPECIFIC " + (routine instanceof Function ? "FUNCTION " : "PROCEDURE "));
			name(builder, routine, true, decorate);
			builder._add(" IS ").sqlChar(routine.getRemarks());
			result.add(operation.apply(builder.toString(), SqlType.SET_COMMENT));
		}
		return result;
	}

	static void identifier(Db2SqlBuilder builder, String name) {
		if (builder.getDialect() instanceof Db2_1110) {
			Db2CreationContext.identifier(name);
			if (name.indexOf('"') >= 0) {
				builder._add(Db2CreationContext.identifier(name));
				return;
			}
		}
		builder.name(name);
	}

	static void name(Db2SqlBuilder builder, Routine<?> routine, boolean specific, boolean decorate) {
		if (!(builder.getDialect() instanceof Db2_1110) && !specific) {
			builder.name(routine, decorate);
			return;
		}
		if (decorate && routine.getSchemaName() != null) {
			identifier(builder, routine.getSchemaName());
			builder._add(".");
		}
		identifier(builder, specific ? routine.getSpecificName() : routine.getName());
	}

	static void procedureOptions(Procedure procedure, Db2SqlBuilder builder) {
		if (!(builder.getDialect() instanceof Db2_1110))
			return;
		String autonomous = procedure.getSpecifics().get("AUTONOMOUS");
		if (autonomous != null && !"N".equals(autonomous))
			throw unsupported(procedure);
		String resultSets = procedure.getSpecifics().get("RESULT_SETS");
		if (resultSets != null) {
			final int count;
			try {
				count = Integer.parseInt(resultSets);
			} catch (NumberFormatException e) {
				throw unsupported(procedure);
			}
			if (count < 0 || count > Short.MAX_VALUE)
				throw unsupported(procedure);
			builder.lineBreak()._add("DYNAMIC RESULT SETS " + count);
		}
		String access = procedure.getSpecifics().get("SQL_DATA_ACCESS");
		if (access != null) {
			String sql = switch (access) {
			case "C" -> "CONTAINS SQL";
			case "R" -> "READS SQL DATA";
			case "M" -> "MODIFIES SQL DATA";
			default -> throw unsupported(procedure);
			};
			builder.lineBreak()._add(sql);
		}
		String deterministic = procedure.getSpecifics().get("DETERMINISTIC");
		if (deterministic != null)
			builder.lineBreak()._add(flag(deterministic, procedure) ? "DETERMINISTIC" : "NOT DETERMINISTIC");
		String commit = procedure.getSpecifics().get("COMMIT_ON_RETURN");
		if (commit != null)
			builder.lineBreak()._add("COMMIT ON RETURN " + (flag(commit, procedure) ? "YES" : "NO"));
	}

	private static boolean flag(String value, Routine<?> routine) {
		if (!Set.of("Y", "N").contains(value))
			throw unsupported(routine);
		return "Y".equals(value);
	}

	private static void validateText(String text, Routine<?> routine) {
		if (text == null || text.isBlank() || text.indexOf('\0') >= 0)
			throw unsupported(routine);
	}

	private static IllegalArgumentException unsupported(Routine<?> routine) {
		return new IllegalArgumentException(
				"Db2 routine requires valid SQL body/options or complete ordered CREATE definition: "
						+ routine.getName());
	}
}

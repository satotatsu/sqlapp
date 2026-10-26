/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.Firebird30;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Legacy UDF declaration changes; no library is loaded by SQL generation. */
final class FirebirdExternalFunctionSql {
	static final String MODULE = "firebird.udf.module", ENTRY = "firebird.udf.entrypoint";

	static boolean legacy(Function function, Dialect dialect) {
		return !(dialect instanceof Firebird30) || function.getSpecifics().get(MODULE) != null || String
				.join("\n", function.getDefinition()).matches("(?is)^\\s*DECLARE\\s+EXTERNAL\\s+FUNCTION\\b.*");
	}

	static List<SqlOperation> alter(DbObjectDifference difference, SqlFactoryRegistry registry,
			FirebirdSqlBuilder builder) {
		if (!(difference.getOriginal() instanceof Function original)
				|| !(difference.getTarget() instanceof Function target))
			throw new IllegalArgumentException("External function ALTER requires original and target functions");
		if (!legacy(original, builder.getDialect()) || !legacy(target, builder.getDialect()))
			throw new UnsupportedOperationException(
					"Converting legacy UDF and stored/UDR functions requires an explicit migration");
		var before = original.clone();
		var after = target.clone();
		before.setDefinition(List.of());
		after.setDefinition(List.of());
		if (!original.getDefinition().equals(target.getDefinition())) {
			canonical(original, before, registry);
			canonical(target, after, registry);
		}
		var changed = before.diff(after).getChangedProperties();
		var unsupported = changed.keySet().stream().filter(key -> !Set.of("remarks", "specifics").contains(key))
				.toList();
		if (!unsupported.isEmpty())
			throw new UnsupportedOperationException(
					"External function ALTER cannot change its name/signature or body: " + unsupported);
		var oldSpecifics = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		var newSpecifics = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		oldSpecifics.putAll(original.getSpecifics());
		newSpecifics.putAll(target.getSpecifics());
		for (String key : List.of(MODULE, ENTRY)) {
			oldSpecifics.remove(key);
			newSpecifics.remove(key);
		}
		if (!oldSpecifics.equals(newSpecifics))
			throw new UnsupportedOperationException("External function ALTER contains unsupported specifics changes");
		FirebirdPrivilege.identifier(target.getName(), "external function name");
		boolean entry = !Objects.equals(original.getSpecifics().get(ENTRY), target.getSpecifics().get(ENTRY));
		boolean module = !Objects.equals(original.getSpecifics().get(MODULE), target.getSpecifics().get(MODULE));
		var result = new ArrayList<SqlOperation>();
		if (entry || module) {
			builder._add("ALTER EXTERNAL FUNCTION ").name(target.getName());
			if (entry)
				builder._add(" ENTRY_POINT ").sqlChar(value(target, ENTRY));
			if (module)
				builder._add(" MODULE_NAME ").sqlChar(value(target, MODULE));
			result.add(new SqlOperation(builder.toString(), SqlType.ALTER, target));
		}
		if (!Objects.equals(original.getRemarks(), target.getRemarks()))
			result.addAll(registry.createSql(target, SqlType.SET_COMMENT));
		return result;
	}

	private static String value(Function function, String key) {
		String value = function.getSpecifics().get(key);
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("External function ALTER requires a nonempty " + key + " without NUL");
		return value;
	}

	private static void canonical(Function source, Function modeled, SqlFactoryRegistry registry) {
		if (source.getDefinition().isEmpty())
			return;
		String actual = String.join("\n", source.getDefinition()).replace("\r\n", "\n").strip();
		String expected = registry.createSql(modeled, SqlType.CREATE).getFirst().getSqlText().replace("\r\n", "\n")
				.strip();
		if (!actual.equals(expected))
			throw new IllegalArgumentException(
					"Changed UDF definition must agree with its modeled declaration; use canonical reader metadata or an explicit migration");
	}

	private FirebirdExternalFunctionSql() {
	}
}

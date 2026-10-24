/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ArgumentRoutine;
import com.sqlapp.data.schemas.DbObjectDifference;

/** Alters a modeled routine in place, preserving grants and dependencies. */
public abstract class FirebirdAlterRoutineFactory<T extends ArgumentRoutine<T>>
		extends SimpleSqlFactory<T, FirebirdSqlBuilder> {
	protected abstract String kind();

	@Override
	public List<SqlOperation> createSql(T routine) {
		List<SqlOperation> result = new ArrayList<>();
		var create = getSqlFactoryRegistry().createSql(routine, SqlType.CREATE);
		if (create.isEmpty())
			throw new IllegalArgumentException("No Firebird routine CREATE factory");
		String prefix = "(?is)^\\s*CREATE\\s+" + kind() + "\\b";
		String ddl = create.get(0).getSqlText();
		if (!java.util.regex.Pattern.compile(prefix).matcher(ddl).find())
			throw new IllegalArgumentException(
					"Routine ALTER requires a CREATE " + kind() + " definition or modeled statement");
		addSql(result, createSqlBuilder()._add(ddl.replaceFirst(prefix, "ALTER " + kind())), SqlType.ALTER, routine);
		result.addAll(create.subList(1, create.size()));
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getOriginal() == null || difference.getTarget() == null
				|| !difference.getOriginal().getClass().equals(difference.getTarget().getClass()))
			throw new IllegalArgumentException("Routine ALTER requires original and target of the same kind");
		var changed = difference.getChangedProperties();
		boolean structural = changed.keySet().stream().anyMatch(key -> !Set.of("remarks", "arguments").contains(key));
		List<com.sqlapp.data.schemas.NamedArgument> commentedArguments = new ArrayList<>();
		Set<String> supported = Set.of("statement", "sqlSecurity", "deterministic", "remarks", "arguments", "returning",
				"specifics");
		var unsupported = changed.keySet().stream().filter(key -> !supported.contains(key)).toList();
		if (!unsupported.isEmpty())
			throw new UnsupportedOperationException(
					"Firebird routine ALTER does not support changes to " + unsupported);
		if (changed.get("arguments") instanceof com.sqlapp.data.schemas.DbObjectDifferenceCollection arguments) {
			for (DbObjectDifference argument : arguments.getList()) {
				if (argument.getTarget() == null) {
					structural = true;
					continue;
				}
				var argumentTarget = (com.sqlapp.data.schemas.NamedArgument) argument.getTarget();
				DbObjectDifference actual = argument.getOriginal() == null
						? new com.sqlapp.data.schemas.NamedArgument(argumentTarget.getName()).diff(argumentTarget)
						: argument;
				validateProperties(actual, Set.of("name", "dataType", "dataTypeName", "length", "scale", "notNull",
						"defaultValue", "characterSet", "collation", "direction", "remarks"), "argument");
				if (argument.getOriginal() == null
						|| actual.getChangedProperties().keySet().stream().anyMatch(key -> !"remarks".equals(key)))
					structural = true;
				if (actual.getChangedProperties().containsKey("remarks"))
					commentedArguments.add(argumentTarget);
			}
		}
		if (changed.get("returning") instanceof DbObjectDifference returning) {
			validateProperties(returning,
					Set.of("dataType", "dataTypeName", "length", "scale", "characterSet", "collation", "specifics"),
					"return type");
			var before = (com.sqlapp.data.schemas.FunctionReturning) returning.getOriginal();
			var after = (com.sqlapp.data.schemas.FunctionReturning) returning.getTarget();
			validateSpecifics(before.getSpecifics(), after.getSpecifics(), Set.of("firebird.notNull"));
		}
		var original = (T) difference.getOriginal();
		T target = (T) difference.getTarget();
		validateSpecifics(original.getSpecifics(), target.getSpecifics(),
				Set.of("firebird.external.engine", "firebird.external.entrypoint"));
		if (!target.getDefinition().isEmpty() && structural)
			throw new IllegalArgumentException(
					"A complete routine definition shadows modeled changes; use a modeled statement for diff ALTER");
		List<SqlOperation> result = new ArrayList<>();
		if (structural)
			result.addAll(createSql(target));
		for (var argument : commentedArguments) {
			if (!structural || argument.getRemarks() == null)
				addSql(result, FirebirdRoutineSql.argumentComment(target, argument, createSqlBuilder()),
						SqlType.SET_COMMENT, argument);
		}
		if (changed.containsKey("remarks")) {
			var builder = createSqlBuilder();
			builder._add("COMMENT ON " + kind() + " ").name(target.getName())._add(" IS ");
			if (target.getRemarks() == null)
				builder._add("NULL");
			else
				builder.sqlChar(target.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, target);
		}
		return result;
	}

	private void validateProperties(DbObjectDifference difference, Set<String> allowed, String label) {
		var unsupported = difference.getChangedProperties().keySet().stream().filter(key -> !allowed.contains(key))
				.toList();
		if (!unsupported.isEmpty())
			throw new UnsupportedOperationException(
					"Firebird " + label + " ALTER does not support changes to " + unsupported);
	}

	private void validateSpecifics(java.util.Map<String, String> original, java.util.Map<String, String> target,
			Set<String> allowed) {
		var before = new java.util.HashMap<>(original);
		var after = new java.util.HashMap<>(target);
		allowed.forEach(key -> {
			before.remove(key);
			after.remove(key);
		});
		if (!before.equals(after))
			throw new UnsupportedOperationException("Firebird routine ALTER contains unsupported specifics changes");
	}
}

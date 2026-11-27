/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.metadata;

import java.util.*;

import com.sqlapp.data.db.dialect.vertica.DialectHolder;
import com.sqlapp.data.schemas.*;

/**
 * Maps exported procedure overloads atomically; bodies remain opaque executable
 * DDL.
 */
final class VerticaProcedureDdlMapper {
	private static String key(String schema, String name, Collection<NamedArgument> arguments) {
		var parts = new ArrayList<String>();
		parts.add(Objects.requireNonNull(schema, "procedure schema").toLowerCase(Locale.ROOT));
		parts.add(Objects.requireNonNull(name, "procedure name").toLowerCase(Locale.ROOT));
		for (var argument : arguments) {
			var type = argument.getDataType();
			if (type == null || type == com.sqlapp.data.db.datatype.DataType.OTHER
					|| type == com.sqlapp.data.db.datatype.DataType.DOMAIN)
				throw new IllegalArgumentException("Procedure export requires resolved native argument types: " + name);
			parts.add(type == com.sqlapp.data.db.datatype.DataType.DECIMAL ? "NUMERIC" : type.name());
		}
		// Length-prefix each part so punctuation inside identifiers cannot alias
		// another key.
		return parts.stream().map(p -> p.length() + ":" + p).collect(java.util.stream.Collectors.joining());
	}

	static Map<String, Procedure> targets(Collection<Procedure> procedures) {
		Map<String, Procedure> result = new LinkedHashMap<>();
		for (var p : procedures) {
			if (p.getSchemaName() == null || p.getSchemaName().isBlank() || p.getName() == null
					|| p.getName().isBlank())
				throw new IllegalArgumentException("Procedure definition mapping requires schema and name");
			var previous = result.putIfAbsent(key(p.getSchemaName(), p.getName(), p.getArguments()), p);
			if (previous != null && previous != p)
				throw new IllegalArgumentException("Duplicate procedure overload: " + p.getName());
		}
		return result;
	}

	static void apply(String script, Collection<Procedure> procedures) {
		var targets = targets(procedures);
		Map<Procedure, String> definitions = new IdentityHashMap<>();
		var dialect = DialectHolder.defaultDialect11_1_1;
		var reader = new Vertica11_1_1ProcedureReader(dialect);
		var splitter = new VerticaFunctionReader(dialect);
		for (var statement : dialect.createSqlSplitter().parse(script)) {
			if (statement.getTextType().isComment())
				continue;
			String sql = statement.getText().strip();
			var cursor = new VerticaProjectionDdlMapper.Cursor(sql);
			if (cursor.keyword("SET") || cursor.keyword("RESET") || cursor.keyword("USE") || cursor.keyword("ALTER"))
				throw new IllegalArgumentException(
						"Procedure definition extraction cannot discard context or ALTER statements");
			cursor = new VerticaProjectionDdlMapper.Cursor(sql);
			if (!cursor.keyword("CREATE"))
				continue;
			if (cursor.keyword("OR"))
				cursor.expect("REPLACE");
			if (!cursor.keyword("PROCEDURE"))
				continue;
			var names = new ArrayList<String>();
			names.add(cursor.identifier());
			while (cursor.dot())
				names.add(cursor.identifier());
			if (names.size() < 2 || names.size() > 3)
				throw new IllegalArgumentException("Procedure CREATE requires qualified identity");
			var arguments = new ArrayList<NamedArgument>();
			for (String argument : splitter.splitArguments(cursor.argumentList()))
				arguments.add(reader.createArgument(argument));
			cursor.expect("LANGUAGE");
			if (cursor.atEnd())
				throw new IllegalArgumentException("Procedure CREATE body is missing");
			var target = targets.get(key(names.get(names.size() - 2), names.get(names.size() - 1), arguments));
			if (target == null)
				continue;
			if (names.size() == 3
					&& (target.getCatalogName() == null || !names.get(0).equalsIgnoreCase(target.getCatalogName())))
				throw new IllegalArgumentException("Procedure CREATE catalog does not match target");
			if (definitions.put(target, sql + "\n;") != null)
				throw new IllegalArgumentException("Duplicate procedure CREATE: " + target.getName());
		}
		for (var target : targets.values())
			if (!definitions.containsKey(target))
				throw new IllegalArgumentException("CREATE PROCEDURE definition not found: " + target.getName());
		definitions.forEach(Procedure::setDefinition);
	}
}

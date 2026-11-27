/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2FunctionMappings;
import com.sqlapp.data.db.dialect.db2.Db2FunctionMappings.*;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.schemas.Catalog;

final class Db2FunctionMappingSql {
	private Db2FunctionMappingSql() {
	}

	static List<String> create(Catalog catalog, Dialect dialect) {
		var snapshot = validate(catalog, dialect);
		if (!snapshot.orderKnown() && snapshot.mappings().size() > 1)
			throw new IllegalArgumentException(
					"Db2 function mappings with tied or missing creation times require an explicit intended order via Db2FunctionMappings.set(catalog, orderedMappings)");
		return create(snapshot.mappings());
	}

	private static List<String> create(List<Mapping> mappings) {
		List<String> result = new ArrayList<>();
		for (var mapping : mappings) {
			result.add(create(mapping));
			if (mapping.remarks() != null)
				result.add("COMMENT ON FUNCTION MAPPING " + id(mapping.name()) + " IS " + literal(mapping.remarks()));
		}
		return result;
	}

	private static Snapshot validate(Catalog catalog, Dialect dialect) {
		var snapshot = Db2FunctionMappings.get(catalog);
		if (!snapshot.mappings().isEmpty() && !(dialect instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 function mapping generation requires LUW 11.1 or later");
		return snapshot;
	}

	private static String create(Mapping mapping) {
		if (mapping.definition() != null)
			return mapping.definition();
		if (mapping.infix() == null)
			throw new IllegalArgumentException(
					"Db2 function mapping requires explicit infix notation via withInfix or a complete CREATE definition: "
							+ mapping.name());
		if (mapping.functionSchema() == null || mapping.specificName() == null
				|| "SYSIBM".equals(mapping.functionSchema()) || !mapping.parameterOptions().isEmpty())
			throw new IllegalArgumentException(
					"Db2 function mapping requires a complete CREATE definition for builtin signatures or parameter options: "
							+ mapping.name());
		String target;
		if (mapping.serverName() != null) {
			if (mapping.serverType() != null || mapping.serverVersion() != null || mapping.wrapperName() != null)
				throw new IllegalArgumentException(
						"Db2 function mapping server and category scopes cannot be combined: " + mapping.name());
			target = " SERVER " + id(mapping.serverName());
		} else {
			if (mapping.serverType() == null)
				throw new IllegalArgumentException(
						"Db2 function mapping requires a server or server type: " + mapping.name());
			target = " SERVER TYPE " + id(mapping.serverType());
			if (mapping.serverVersion() != null)
				target += " VERSION " + literal(mapping.serverVersion());
			if (mapping.wrapperName() != null)
				target += " WRAPPER " + id(mapping.wrapperName());
		}
		String sql = "CREATE FUNCTION MAPPING " + id(mapping.name()) + " FOR SPECIFIC "
				+ Db2CreationContext.qualified(mapping.functionSchema(), mapping.specificName()) + target;
		if (!mapping.options().isEmpty())
			sql += " OPTIONS ("
					+ mapping.options().entrySet().stream().map(e -> e.getKey() + " " + literal(e.getValue()))
							.collect(java.util.stream.Collectors.joining(", "))
					+ ")";
		return sql + (mapping.infix() ? " WITH INFIX" : "");
	}

	/** Only append preserves the precedence of all existing mappings. */
	static List<String> diff(Catalog original, Catalog target, Dialect dialect) {
		var oldSnapshot = validate(original, dialect);
		var newSnapshot = validate(target, dialect);
		if (oldSnapshot.equals(newSnapshot))
			return List.of();
		if (!oldSnapshot.orderKnown() || !newSnapshot.orderKnown())
			throw new IllegalArgumentException("Db2 function mapping changes require resolved mapping precedence");
		var before = oldSnapshot.mappings();
		var after = newSnapshot.mappings();
		if (after.size() < before.size() || !after.subList(0, before.size()).equals(before))
			throw new IllegalArgumentException(
					"Db2 function mapping removal, modification or reordering requires an explicit native plan to preserve mapping precedence");
		return create(after.subList(before.size(), after.size()));
	}

	private static String id(String value) {
		return Db2CreationContext.identifier(value);
	}

	private static String literal(String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}

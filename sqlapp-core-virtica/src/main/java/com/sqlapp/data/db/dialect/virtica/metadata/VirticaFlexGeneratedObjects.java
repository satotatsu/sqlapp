package com.sqlapp.data.db.dialect.virtica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.data.schemas.Schema;

/**
 * Verifies generated objects as one set; never drops/replaces physical designs.
 */
final class VirticaFlexGeneratedObjects {
	private record Identity(String kind, String schema, String name) {
	}

	static Set<String> verify(Connection connection, Schema schema, VirticaSchemaRestorePlan.Plan plan)
			throws SQLException {
		try {
			Schema current = new Schema(schema.getName());
			String script = new VirticaDdlExporter().exportSchemaObjects(connection, current);
			VirticaFlexRawColumns.verify(connection, schema, plan.flexTables());
			return matchingStatements(schema, plan, script);
		} catch (IllegalArgumentException failure) {
			throw new SQLException("Invalid generated-object export during Flex verification", failure);
		}
	}

	static Set<String> matchingStatements(Schema schema, VirticaSchemaRestorePlan.Plan plan, String currentExport)
			throws SQLException {
		var before = definitions(schema, plan.statements().subList(0, plan.generatedCheckpoint()));
		var expected = definitions(schema,
				plan.statements().subList(plan.generatedCheckpoint(), plan.statements().size()));
		var actual = definitions(schema, DialectHolder.defaultDialect12_0_4.createSqlSplitter().parse(currentExport)
				.stream().filter(s -> !s.getTextType().isComment()).map(s -> s.getText().strip()).toList());
		actual.keySet().removeAll(before.keySet());
		var skipped = new LinkedHashSet<String>();
		for (var entry : actual.entrySet()) {
			String source = expected.get(entry.getKey());
			if (source == null)
				throw new SQLException(
						"Generated Flex object is absent from the exported restore definition: " + entry.getKey());
			if (!canonical(source).equals(canonical(entry.getValue())))
				throw new SQLException(
						"Generated Flex object differs from the saved vendor DDL; no replacement is attempted: "
								+ entry.getKey());
			skipped.add(source);
		}
		return Collections.unmodifiableSet(skipped);
	}

	private static String canonical(String sql) {
		return sql.replace("\r\n", "\n").strip();
	}

	private static Map<Identity, String> definitions(Schema schema, List<String> statements) throws SQLException {
		Map<Identity, String> result = new LinkedHashMap<>();
		for (String sql : statements) {
			var cursor = new VirticaProjectionDdlMapper.Cursor(sql);
			if (!cursor.keyword("CREATE"))
				continue;
			if (cursor.keyword("OR"))
				cursor.expect("REPLACE");
			String kind = null;
			if (cursor.keyword("FLEX") || cursor.keyword("FLEXIBLE") || cursor.keyword("EXTERNAL")) {
				cursor.expect("TABLE");
				kind = "TABLE";
			} else
				for (String candidate : List.of("TABLE", "VIEW", "PROJECTION", "SEQUENCE"))
					if (cursor.keyword(candidate)) {
						kind = candidate;
						break;
					}
			if (kind == null)
				continue;
			var names = new ArrayList<String>();
			names.add(cursor.identifier());
			while (cursor.dot()) {
				if (names.size() == 3)
					throw new SQLException("Too many generated-object qualifiers");
				names.add(cursor.identifier());
			}
			if (names.size() < 2 || !names.get(names.size() - 2).equalsIgnoreCase(schema.getName()))
				throw new SQLException("Generated object export escaped the new schema");
			if (names.size() == 3
					&& (schema.getCatalogName() == null || !names.get(0).equalsIgnoreCase(schema.getCatalogName())))
				throw new SQLException("Generated object export catalog does not match the Schema model");
			var key = new Identity(kind, names.get(names.size() - 2).toLowerCase(Locale.ROOT),
					names.get(names.size() - 1).toLowerCase(Locale.ROOT));
			if (result.putIfAbsent(key, sql) != null)
				throw new SQLException("Duplicate generated-object CREATE identity: " + key);
		}
		return result;
	}
}

package com.sqlapp.data.db.dialect.virtica.metadata;

import java.util.*;
import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

/**
 * Atomic matching of standalone text-index CREATE statements; no dependency
 * execution.
 */
final class VirticaTextIndexDdlMapper {
	private record Name(String schema, String name) {
	}

	static String schema(Index index) {
		String own = index.getSpecifics().get("INDEX_SCHEMA_NAME", String.class);
		return own == null ? index.getSchemaName() : own;
	}

	private static Name name(String schema, String name) {
		if (schema == null || schema.isBlank() || name == null || name.isBlank())
			throw new IllegalArgumentException("Text index mapping requires schema and name");
		return new Name(schema.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
	}

	static Map<Name, Index> targets(Collection<Index> indexes) {
		Map<Name, Index> result = new LinkedHashMap<>();
		for (var index : indexes) {
			if (index.getIndexType() != IndexType.FullText)
				throw new IllegalArgumentException("Expected text index");
			var previous = result.putIfAbsent(name(schema(index), index.getName()), index);
			if (previous != null && previous != index)
				throw new IllegalArgumentException("Duplicate text index identity");
		}
		return result;
	}

	static void apply(String script, Collection<Index> indexes) {
		var targets = targets(indexes);
		Map<Index, String> definitions = new IdentityHashMap<>();
		for (var statement : DialectHolder.defaultDialect11_1_1.createSqlSplitter().parse(script)) {
			if (statement.getTextType().isComment())
				continue;
			String sql = statement.getText().strip();
			var cursor = new VirticaProjectionDdlMapper.Cursor(sql);
			if (cursor.keyword("ALTER") || cursor.keyword("SET") || cursor.keyword("RESET") || cursor.keyword("USE"))
				throw new IllegalArgumentException("Text index extraction cannot discard context or ALTER statements");
			cursor = new VirticaProjectionDdlMapper.Cursor(sql);
			if (!cursor.keyword("CREATE") || !cursor.keyword("TEXT") || !cursor.keyword("INDEX"))
				continue;
			var names = qualified(cursor);
			if (names.size() < 2)
				throw new IllegalArgumentException("Text index CREATE must be schema qualified");
			cursor.expect("ON");
			var source = qualified(cursor);
			String columns = cursor.argumentList();
			if (new VirticaFunctionReader(DialectHolder.defaultDialect11_1_1).splitArguments(columns).size() < 2)
				throw new IllegalArgumentException("Text index CREATE requires ID and text columns");
			var target = targets.get(name(names.get(names.size() - 2), names.get(names.size() - 1)));
			if (target == null)
				continue;
			if (names.size() == 3
					&& (target.getCatalogName() == null || !names.get(0).equalsIgnoreCase(target.getCatalogName())))
				throw new IllegalArgumentException("Text index CREATE catalog mismatch");
			if (target.getTableName() != null && !source.get(source.size() - 1).equalsIgnoreCase(target.getTableName()))
				throw new IllegalArgumentException("Text index source table mismatch");
			if (source.size() < 2 || target.getSchemaName() != null
					&& !source.get(source.size() - 2).equalsIgnoreCase(target.getSchemaName()))
				throw new IllegalArgumentException("Text index source schema missing or mismatched");
			if (definitions.put(target, sql + "\n;") != null)
				throw new IllegalArgumentException("Duplicate text index CREATE");
		}
		for (var target : targets.values())
			if (!definitions.containsKey(target))
				throw new IllegalArgumentException("CREATE TEXT INDEX definition not found: " + target.getName());
		definitions.forEach(Index::setDefinition);
	}

	private static List<String> qualified(VirticaProjectionDdlMapper.Cursor cursor) {
		var names = new ArrayList<String>();
		names.add(cursor.identifier());
		while (cursor.dot()) {
			if (names.size() == 3)
				throw new IllegalArgumentException("Too many text index qualifiers");
			names.add(cursor.identifier());
		}
		return names;
	}
}

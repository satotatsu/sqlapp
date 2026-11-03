/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.metadata;

import java.util.*;
import com.sqlapp.data.db.dialect.virtica.DialectHolder;
import com.sqlapp.data.schemas.Projection;

/** Extracts named CREATE statements without distributing dependency scripts. */
final class VirticaProjectionDdlMapper {
	private record Name(String schema, String name) {
	}

	private record Create(String catalog, Name name, String ddl) {
	}

	private static boolean same(String left, String right) {
		return left == null ? right == null : right != null && left.equalsIgnoreCase(right);
	}

	private static Map<Name, List<Projection>> targets(Collection<Projection> projections) {
		Objects.requireNonNull(projections, "projections");
		Map<Name, List<Projection>> targets = new TreeMap<>(
				Comparator.comparing(Name::schema, String.CASE_INSENSITIVE_ORDER).thenComparing(Name::name,
						String.CASE_INSENSITIVE_ORDER));
		Set<Projection> identities = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Projection projection : projections) {
			Objects.requireNonNull(projection, "projection");
			if (!identities.add(projection))
				continue;
			if (projection.getSchemaName() == null || projection.getSchemaName().isBlank()
					|| projection.getName() == null || projection.getName().isBlank())
				throw new IllegalArgumentException("Projection definition mapping requires schema and object names");
			var group = targets.computeIfAbsent(new Name(projection.getSchemaName(), projection.getName()),
					k -> new ArrayList<>());
			if (group.stream().anyMatch(p -> same(p.getCatalogName(), projection.getCatalogName())))
				throw new IllegalArgumentException("Duplicate projection target identity: " + projection.getSchemaName()
						+ "." + projection.getName());
			group.add(projection);
		}
		return targets;
	}

	static void validateTargets(Collection<Projection> projections) {
		targets(projections);
	}

	static void apply(String script, Collection<Projection> projections) {
		Objects.requireNonNull(script, "script");
		var targets = targets(projections);
		if (targets.isEmpty())
			return;
		Map<Projection, String> definitions = new IdentityHashMap<>();
		for (var statement : DialectHolder.defaultDialect11_1_1.createSqlSplitter().parse(script)) {
			if (statement.getTextType().isComment())
				continue;
			String sql = statement.getText().strip();
			Cursor cursor = new Cursor(sql);
			if (cursor.keyword("SET") || cursor.keyword("RESET") || cursor.keyword("USE"))
				throw new IllegalArgumentException(
						"Projection definition extraction cannot discard session-context statements");
			cursor = new Cursor(sql);
			if (cursor.keyword("ALTER") && (cursor.keyword("SESSION") || cursor.keyword("PROJECTION")))
				throw new IllegalArgumentException(
						"Projection definition extraction cannot discard session/projection ALTER statements");
			Create create = parse(sql);
			if (create == null)
				continue;
			var candidates = targets.get(create.name());
			if (candidates == null)
				continue;
			var matches = candidates.stream()
					.filter(p -> create.catalog() == null || same(create.catalog(), p.getCatalogName())).toList();
			if (matches.size() != 1)
				throw new IllegalArgumentException(
						"Projection CREATE catalog is missing or ambiguous for " + create.name());
			Projection target = matches.get(0);
			if (definitions.put(target, create.ddl()) != null)
				throw new IllegalArgumentException("Duplicate CREATE PROJECTION definition: " + create.name());
		}
		for (var group : targets.values())
			for (Projection target : group) {
				if (!definitions.containsKey(target))
					throw new IllegalArgumentException("CREATE PROJECTION definition not found: "
							+ target.getSchemaName() + "." + target.getName());
			}
		// Only commit after every target and every candidate definition was validated.
		definitions.forEach(Projection::setDefinition);
	}

	private static Create parse(String sql) {
		Cursor cursor = new Cursor(sql);
		if (!cursor.keyword("CREATE"))
			return null;
		if (cursor.keyword("OR")) {
			cursor.expect("REPLACE");
			if (cursor.keyword("PROJECTION"))
				throw new IllegalArgumentException("Vertica CREATE PROJECTION does not support OR REPLACE");
			return null;
		}
		if (!cursor.keyword("PROJECTION"))
			return null;
		if (cursor.keyword("IF")) {
			cursor.expect("NOT");
			cursor.expect("EXISTS");
		}
		List<String> names = new ArrayList<>();
		names.add(cursor.identifier());
		while (cursor.dot()) {
			if (names.size() == 3)
				throw new IllegalArgumentException("Projection CREATE has too many name qualifiers");
			names.add(cursor.identifier());
		}
		if (names.size() < 2)
			throw new IllegalArgumentException(
					"Projection CREATE must be schema-qualified; search_path is not inferred");
		if (cursor.openParenthesis())
			cursor.finishColumnList();
		cursor.expect("AS");
		cursor.expect("SELECT");
		if (cursor.atEnd())
			throw new IllegalArgumentException("Projection CREATE body is empty: " + names);
		int size = names.size();
		// Keep the terminator outside a possible trailing line comment.
		return new Create(size == 3 ? names.get(0) : null, new Name(names.get(size - 2), names.get(size - 1)),
				sql + "\n;");
	}

	/**
	 * Header-only lexer; statement splitting and literal handling use the dialect
	 * splitter.
	 */
	static final class Cursor {
		private final String sql;
		private int position;

		Cursor(String sql) {
			this.sql = sql;
		}

		int offset() {
			skip();
			return position;
		}

		private void skip() {
			while (position < sql.length()) {
				if (Character.isWhitespace(sql.charAt(position))) {
					position++;
					continue;
				}
				if (sql.startsWith("--", position)) {
					int end = sql.indexOf('\n', position + 2);
					position = end < 0 ? sql.length() : end + 1;
				} else if (sql.startsWith("/*", position)) {
					int end = sql.indexOf("*/", position + 2);
					if (end < 0)
						throw new IllegalArgumentException("Unterminated projection DDL header comment");
					position = end + 2;
				} else
					return;
			}
		}

		boolean keyword(String keyword) {
			skip();
			int end = position + keyword.length();
			if (end <= sql.length() && sql.regionMatches(true, position, keyword, 0, keyword.length())
					&& (end == sql.length() || !part(sql.charAt(end)))) {
				position = end;
				return true;
			}
			return false;
		}

		void expect(String keyword) {
			if (!keyword(keyword))
				throw new IllegalArgumentException("Expected " + keyword + " in projection DDL header");
		}

		boolean dot() {
			skip();
			if (position < sql.length() && sql.charAt(position) == '.') {
				position++;
				return true;
			}
			return false;
		}

		boolean openParenthesis() {
			skip();
			if (position < sql.length() && sql.charAt(position) == '(') {
				position++;
				return true;
			}
			return false;
		}

		void finishColumnList() {
			int depth = 1;
			while (position < sql.length()) {
				skip();
				if (position == sql.length())
					break;
				char c = sql.charAt(position++);
				if (c == '(')
					depth++;
				else if (c == ')' && --depth == 0)
					return;
				else if (c == '\'' || c == '"') {
					boolean closed = false;
					while (position < sql.length()) {
						if (sql.charAt(position++) == c) {
							if (position < sql.length() && sql.charAt(position) == c)
								position++;
							else {
								closed = true;
								break;
							}
						}
					}
					if (!closed)
						throw new IllegalArgumentException("Projection column-list quote is not closed");
				}
			}
			throw new IllegalArgumentException("Projection column list is not closed");
		}

		String argumentList() {
			if (!openParenthesis())
				throw new IllegalArgumentException("Procedure signature is missing");
			int start = position;
			finishColumnList();
			return sql.substring(start, position - 1);
		}

		boolean atEnd() {
			skip();
			return position == sql.length();
		}

		private boolean part(char c) {
			return Character.isLetterOrDigit(c) || c == '_' || c == '$';
		}

		String identifier() {
			skip();
			if (position == sql.length())
				throw new IllegalArgumentException("Projection DDL identifier is missing");
			if (sql.charAt(position) == '"') {
				position++;
				StringBuilder name = new StringBuilder();
				while (position < sql.length()) {
					char c = sql.charAt(position++);
					if (c != '"')
						name.append(c);
					else if (position < sql.length() && sql.charAt(position) == '"') {
						name.append('"');
						position++;
					} else {
						if (name.isEmpty())
							throw new IllegalArgumentException("Projection DDL identifier is empty");
						return name.toString();
					}
				}
				throw new IllegalArgumentException("Projection DDL identifier quote is not closed");
			}
			int start = position;
			char first = sql.charAt(position);
			if (!Character.isLetter(first) && first != '_')
				throw new IllegalArgumentException("Invalid projection DDL identifier");
			while (position < sql.length() && part(sql.charAt(position)))
				position++;
			return sql.substring(start, position);
		}
	}
}

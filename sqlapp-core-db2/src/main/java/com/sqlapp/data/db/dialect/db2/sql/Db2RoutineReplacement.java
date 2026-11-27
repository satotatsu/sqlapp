/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Routine;

/** Verifies identifying DDL and unchanged headers before native replacement. */
final class Db2RoutineReplacement {
	private Db2RoutineReplacement() {
	}

	static String replace(Routine<?> before, Routine<?> after) {
		validateContext(after);
		Header left = parse(before), right = parse(after);
		if (!left.tokens.equals(right.tokens))
			throw unsupported();
		return prefix(right, after, right.text.length());
	}

	static String replaceStatement(Routine<?> before, Routine<?> after) {
		validateContext(after);
		String ddl = cached(before);
		Header source = parse(before, ddl);
		if (!ddl.substring(source.bodyStart).strip().equals(Db2DefinitionSql.text(before.getStatement()).strip()))
			throw unsupported();
		if (Objects.equals(before.getSpecifics().get("DB2_ROUTINE_DDL_BASE64"),
				after.getSpecifics().get("DB2_ROUTINE_DDL_BASE64"))) {
			if (!before.getDefinition().equals(after.getDefinition()))
				throw unsupported();
		} else {
			String targetDdl = cached(after);
			Header target = parse(after, targetDdl);
			if (!source.tokens.equals(target.tokens) || !targetDdl.substring(target.bodyStart).strip()
					.equals(Db2DefinitionSql.text(after.getStatement()).strip()))
				throw unsupported();
		}
		String body = Db2DefinitionSql.text(after.getStatement());
		String first = new Cursor(body).next();
		if (!Set.of("BEGIN", "RETURN").contains(first))
			throw unsupported();
		return prefix(source, before, source.bodyStart) + body;
	}

	private static String prefix(Header header, Routine<?> routine, int end) {
		if (!header.unqualified)
			return "CREATE OR REPLACE " + header.text.substring(header.kindStart, end);
		return "CREATE OR REPLACE " + header.text.substring(header.kindStart, header.nameStart)
				+ com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(routine.getSchemaName(),
						routine.getName())
				+ header.text.substring(header.nameEnd, end);
	}

	private static String cached(Routine<?> routine) {
		String encoded = routine.getSpecifics().get("DB2_ROUTINE_DDL_BASE64");
		if (encoded == null)
			throw unsupported();
		final String ddl;
		try {
			ddl = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
					.decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(encoded))).toString();
		} catch (IllegalArgumentException | java.nio.charset.CharacterCodingException e) {
			throw unsupported();
		}
		if (!routine.getDefinition().isEmpty()
				&& !Db2DefinitionSql.text(routine.getDefinition()).strip().equals(ddl.strip()))
			throw unsupported();
		return ddl;
	}

	private static void validateContext(Routine<?> routine) {
		for (String key : List.of("QUALIFIER", "FUNC_PATH"))
			if (routine.getSpecifics().get(key) == null || routine.getSpecifics().get(key).isBlank())
				throw unsupported();
	}

	private record Header(List<String> tokens, String text, int kindStart, int bodyStart, int nameStart, int nameEnd,
			boolean unqualified) {
	}

	private static Header parse(Routine<?> routine) {
		if (routine.getDefinition().isEmpty())
			throw unsupported();
		return parse(routine, Db2DefinitionSql.text(routine.getDefinition()));
	}

	private static Header parse(Routine<?> routine, String text) {
		if (!"SQL".equalsIgnoreCase(routine.getLanguage()) || routine.getSchemaName() == null
				|| routine.getSpecificName() == null)
			throw unsupported();
		if (text.isBlank() || text.indexOf('\0') >= 0)
			throw unsupported();
		if (java.util.regex.Pattern.compile("(?im)^\\s*--#SET\\b").matcher(text).find())
			throw unsupported();
		Cursor cursor = new Cursor(text);
		cursor.expect("CREATE");
		if (cursor.peek().equals("OR")) {
			cursor.next();
			cursor.expect("REPLACE");
		}
		String kind = routine instanceof Function ? "FUNCTION" : "PROCEDURE";
		cursor.expect(kind);
		int kindStart = cursor.start;
		List<String> tokens = new ArrayList<>();
		tokens.add(kind);
		String firstName = cursor.identifier();
		int nameStart = cursor.start, nameEnd = cursor.position;
		boolean unqualified = !cursor.peek().equals(".");
		String schema, name;
		if (unqualified) {
			schema = routine.getSchemaName();
			name = firstName;
			if (!schema.equals(routine.getSpecifics().get("QUALIFIER")))
				throw unsupported();
		} else {
			schema = firstName;
			cursor.expect(".");
			name = cursor.identifier();
			nameEnd = cursor.position;
		}
		if (!schema.equals(routine.getSchemaName()) || !name.equals(routine.getName()))
			throw unsupported();
		tokens.add(schema);
		tokens.add(name);
		cursor.expect("(");
		tokens.add("(");
		int depth = 1;
		while (depth > 0) {
			String token = cursor.next();
			if (token.isEmpty())
				throw unsupported();
			tokens.add(token);
			if (token.equals("("))
				depth++;
			if (token.equals(")"))
				depth--;
		}
		boolean specific = false, sql = false;
		String previous = "";
		for (;;) {
			String token = cursor.next();
			if (token.isEmpty() || token.equals(";") || token.equals("WRAPPED"))
				throw unsupported();
			if (depth == 0 && (token.equals("BEGIN") || (token.equals("RETURN") && !previous.equals("ON")))) {
				if (!specific || !sql || text.substring(cursor.position).isBlank())
					throw unsupported();
				return new Header(List.copyOf(tokens), text, kindStart, cursor.start, nameStart, nameEnd, unqualified);
			}
			tokens.add(token);
			if (token.equals("("))
				depth++;
			if (token.equals(")") && --depth < 0)
				throw unsupported();
			if (depth == 0 && token.equals("SPECIFIC")) {
				if (specific)
					throw unsupported();
				String identity = cursor.identifier();
				if (cursor.peek().equals(".")) {
					cursor.next();
					if (!identity.equals(schema))
						throw unsupported();
					identity = cursor.identifier();
				}
				if (!identity.equals(routine.getSpecificName()))
					throw unsupported();
				tokens.add(identity);
				specific = true;
			} else if (depth == 0 && token.equals("LANGUAGE")) {
				if (sql)
					throw unsupported();
				cursor.expect("SQL");
				tokens.add("SQL");
				sql = true;
			}
			previous = token;
		}
	}

	private static final class Cursor {
		private final String text;
		private int position, start;

		Cursor(String text) {
			this.text = text;
		}

		void expect(String value) {
			if (!value.equals(next()))
				throw unsupported();
		}

		String peek() {
			int saved = position;
			String token = next();
			position = saved;
			return token;
		}

		String identifier() {
			String token = next();
			if (token.startsWith("\""))
				return token.substring(1, token.length() - 1).replace("\"\"", "\"");
			if (!token.matches("[A-Z_][A-Z_0-9$#@]*"))
				throw unsupported();
			return token;
		}

		String next() {
			for (;;) {
				while (position < text.length() && Character.isWhitespace(text.charAt(position)))
					position++;
				if (text.startsWith("--", position)) {
					while (position < text.length() && text.charAt(position) != '\n' && text.charAt(position) != '\r')
						position++;
				} else if (text.startsWith("/*", position)) {
					position += 2;
					int depth = 1;
					while (depth > 0 && position < text.length()) {
						if (text.startsWith("/*", position)) {
							depth++;
							position += 2;
						} else if (text.startsWith("*/", position)) {
							depth--;
							position += 2;
						} else
							position++;
					}
					if (depth != 0)
						throw unsupported();
				} else
					break;
			}
			start = position;
			if (position == text.length())
				return "";
			char first = text.charAt(position++);
			if (first == '\"' || first == '\'') {
				while (position < text.length()) {
					if (text.charAt(position++) == first) {
						if (position < text.length() && text.charAt(position) == first)
							position++;
						else
							return text.substring(start, position);
					}
				}
				throw unsupported();
			}
			if (Character.isLetterOrDigit(first) || "_$#@".indexOf(first) >= 0) {
				while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position))
						|| "_$#@".indexOf(text.charAt(position)) >= 0))
					position++;
				return text.substring(start, position).toUpperCase(Locale.ROOT);
			}
			return String.valueOf(first);
		}
	}

	static IllegalArgumentException unsupported() {
		return new IllegalArgumentException(
				"Db2 routine body replacement requires complete SQL CREATE DDL with matching qualified name, SPECIFIC identity, unchanged signature/options and resolved QUALIFIER/FUNC_PATH; re-read metadata with readDefinition=true or provide an explicit native replacement plan");
	}
}

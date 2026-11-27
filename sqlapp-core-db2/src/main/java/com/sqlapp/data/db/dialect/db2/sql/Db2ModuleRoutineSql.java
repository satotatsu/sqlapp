/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.dialect.db2.Db2ModuleMembers;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.schemas.Package;

/**
 * Replays complete native module SQL routine definitions without rewriting
 * bodies.
 */
final class Db2ModuleRoutineSql {
	private Db2ModuleRoutineSql() {
	}

	/**
	 * Catalog signature proof plus source checks for resolved anchored/default
	 * types.
	 */
	static boolean canDeclareFirst(Db2ModuleMembers.Member member) {
		if (!Set.of(Db2ModuleMembers.Kind.FUNCTION, Db2ModuleMembers.Kind.PROCEDURE).contains(member.kind())
				|| !member.userDefined() || !member.published()
				|| !"Y".equals(member.attributes().get("MODULEROUTINEIMPLEMENTED"))
				|| !"Y".equals(member.attributes().get("ROUTINE_DECLARATION_INDEPENDENT")))
			return false;
		String text = member.attributes().get("ROUTINE_TEXT");
		if (text == null)
			return false;
		Header header = new Header(text, member);
		for (;;) {
			String token = header.next();
			if (token.isEmpty() || token.equals("ANCHOR") || token.equals("DEFAULT"))
				return false;
			if (token.equals("LANGUAGE"))
				return header.next().equals("SQL");
		}
	}

	/** The original catalog definition has already been validated by create. */
	static Db2ModuleMembers.Member staged(Db2ModuleMembers.Member member, boolean declaration) {
		String text = member.attributes().get("ROUTINE_TEXT");
		Header header = new Header(text, member);
		header.keyword("ALTER");
		header.keyword("MODULE");
		header.identifier();
		if (header.peek().equals(".")) {
			header.next();
			header.identifier();
		}
		header.keyword("PUBLISH");
		int verbStart = header.tokenStart, verbEnd = header.position;
		header.keyword(member.kind().name());
		header.identifier();
		header.keyword("(");
		int depth = 1;
		while (depth > 0) {
			String token = header.next();
			if (token.isEmpty())
				throw unsupported(member);
			if (token.equals("("))
				depth++;
			else if (token.equals(")"))
				depth--;
		}
		// Scan only the already validated identifying header, never the body.
		String token;
		while (!(token = header.next()).equals("LANGUAGE")) {
			if (token.equals("SPECIFIC"))
				header.identifier();
			if (header.position >= text.length())
				throw unsupported(member);
		}
		header.keyword("SQL");
		int bodyStart = header.position;
		if (declaration) {
			int nesting = 0;
			for (;;) {
				String part = header.next();
				if (part.isEmpty())
					throw unsupported(member);
				if (nesting == 0 && part.equals("COMMIT")) {
					header.keyword("ON");
					header.keyword("RETURN");
					if (!Set.of("YES", "NO").contains(header.next()))
						throw unsupported(member);
					continue;
				}
				if (nesting == 0 && (part.equals("BEGIN") || part.equals("RETURN"))) {
					bodyStart = header.tokenStart;
					break;
				}
				if (part.equals("("))
					nesting++;
				else if (part.equals(")"))
					nesting--;
				if (nesting < 0)
					throw unsupported(member);
			}
		}
		var fields = new HashMap<>(member.attributes());
		fields.put("ROUTINE_TEXT", declaration ? text.substring(0, bodyStart).stripTrailing()
				: text.substring(0, verbStart) + "ADD" + text.substring(verbEnd));
		if (declaration)
			fields.put("MODULEROUTINEIMPLEMENTED", "N");
		return new Db2ModuleMembers.Member(member.kind(), member.name(), member.specificName(), declaration, true,
				fields);
	}

	static List<SqlOperation> create(Package module, Db2ModuleMembers.Member member, Db2CreateModuleFactory owner,
			java.util.function.BiFunction<String, SqlType, SqlOperation> operationBuilder) {
		var fields = member.attributes();
		if (!Set.of("ROUTINE_TEXT", "ROUTINE_LANGUAGE", "MODULEROUTINEIMPLEMENTED", "ROUTINE_QUALIFIER",
				"ROUTINE_FUNC_PATH", "ROUTINE_REMARKS", "MEMBER_CREATE_TIME", "ROUTINE_ORIGIN", "ROUTINE_RETURN_SCHEMA",
				"ROUTINE_RETURN_MODULE", "ROUTINE_RETURN_NAME", "ROUTINE_PARAMETER_COUNT", "ROUTINE_PARAMETER_SCHEMA",
				"ROUTINE_PARAMETER_MODULE", "ROUTINE_PARAMETER_NAME", "ROUTINE_DECLARATION_INDEPENDENT")
				.containsAll(fields.keySet()) || fields.containsKey("ROUTINE_REMARKS")
				|| !"SQL".equals(fields.getOrDefault("ROUTINE_LANGUAGE", "").stripTrailing())
				|| (fields.containsKey("ROUTINE_DECLARATION_INDEPENDENT")
						&& !Set.of("Y", "N").contains(fields.get("ROUTINE_DECLARATION_INDEPENDENT")))
				|| !Set.of("Y", "N").contains(fields.getOrDefault("MODULEROUTINEIMPLEMENTED", "")))
			throw unsupported(member);
		String text = fields.get("ROUTINE_TEXT");
		validate(module, member, text);
		Routine<?> routine = member.kind() == Db2ModuleMembers.Kind.FUNCTION ? new Function(member.name())
				: new Procedure(member.name());
		routine.setSchemaName(module.getSchemaName());
		routine.setSpecificName(member.specificName());
		routine.setDefinition(text);
		if (fields.containsKey("ROUTINE_QUALIFIER"))
			routine.getSpecifics().put("QUALIFIER", fields.get("ROUTINE_QUALIFIER"));
		if (fields.containsKey("ROUTINE_FUNC_PATH"))
			routine.getSpecifics().put("FUNC_PATH", fields.get("ROUTINE_FUNC_PATH"));
		return Db2RoutineSql.create(routine, owner.getDialect(), () -> {
			throw unsupported(member);
		}, operationBuilder, true);
	}

	private static void validate(Package module, Db2ModuleMembers.Member member, String text) {
		if (text == null || text.isBlank())
			throw unsupported(member);
		Header header = new Header(text, member);
		header.keyword("ALTER");
		header.keyword("MODULE");
		String first = header.identifier(), schema, name;
		if (header.peek().equals(".")) {
			header.next();
			schema = first;
			name = header.identifier();
		} else {
			schema = member.attributes().get("ROUTINE_QUALIFIER");
			name = first;
		}
		if (!module.getSchemaName().equals(schema) || !module.getName().equals(name))
			throw unsupported(member);
		header.keyword(member.published() ? "PUBLISH" : "ADD");
		header.keyword(member.kind().name());
		if (!member.name().equals(header.identifier()))
			throw unsupported(member);
		header.keyword("(");
		int depth = 1;
		while (depth > 0) {
			String token = header.next();
			if (token.isEmpty())
				throw unsupported(member);
			if (token.equals("("))
				depth++;
			else if (token.equals(")"))
				depth--;
		}
		boolean specific = false;
		boolean prototype = "N".equals(member.attributes().get("MODULEROUTINEIMPLEMENTED"));
		boolean language = false;
		for (;;) {
			String token = header.next();
			if (token.isEmpty() || token.equals(";")) {
				if (prototype && member.published() && specific && (token.isEmpty() || header.next().isEmpty()))
					return;
				throw unsupported(member);
			}
			if (token.equals("BEGIN") || token.equals("RETURN") || token.equals("EXTERNAL") || token.equals("WRAPPED"))
				throw unsupported(member);
			if (token.equals("SPECIFIC")) {
				if (specific || !member.specificName().equals(header.identifier()) || header.peek().equals("."))
					throw unsupported(member);
				specific = true;
			} else if (token.equals("LANGUAGE")) {
				if (language)
					throw unsupported(member);
				language = true;
				header.keyword("SQL");
				if (!specific)
					throw unsupported(member);
				if (!prototype) {
					if (text.substring(header.position).isBlank())
						throw unsupported(member);
					return;
				}
			} else if (prototype && specific) {
				// Preserve characteristics needed when other declarations reference a routine.
				switch (token) {
				case "DETERMINISTIC":
					break;
				case "NOT":
					header.keyword("DETERMINISTIC");
					break;
				case "NO":
					header.keyword("EXTERNAL");
					header.keyword("ACTION");
					break;
				case "CONTAINS":
					header.keyword("SQL");
					break;
				case "READS", "MODIFIES":
					header.keyword("SQL");
					header.keyword("DATA");
					break;
				case "CALLED":
					header.keyword("ON");
					header.keyword("NULL");
					header.keyword("INPUT");
					break;
				case "DYNAMIC":
					if (member.kind() != Db2ModuleMembers.Kind.PROCEDURE)
						throw unsupported(member);
					header.keyword("RESULT");
					header.keyword("SETS");
					header.resultSetCount();
					break;
				case "COMMIT":
					if (member.kind() != Db2ModuleMembers.Kind.PROCEDURE)
						throw unsupported(member);
					header.keyword("ON");
					header.keyword("RETURN");
					if (!Set.of("YES", "NO").contains(header.next()))
						throw unsupported(member);
					break;
				default:
					throw unsupported(member);
				}
			}
		}
	}

	/**
	 * Only scans the identifying header; quoted literals/comments are never
	 * keywords.
	 */
	private static final class Header {
		private final String text;
		private final Db2ModuleMembers.Member member;
		private int position;
		private int tokenStart;
		private boolean identifier;

		Header(String text, Db2ModuleMembers.Member member) {
			this.text = text;
			this.member = member;
		}

		String peek() {
			int saved = position;
			String value = next();
			position = saved;
			return value;
		}

		void keyword(String expected) {
			if (!expected.equals(next()))
				throw unsupported(member);
		}

		void resultSetCount() {
			StringBuilder number = new StringBuilder();
			String token = next();
			if (!token.matches("[0-9]"))
				throw unsupported(member);
			number.append(token);
			while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9')
				number.append(text.charAt(position++));
			try {
				if (Integer.parseInt(number.toString()) > 32767)
					throw unsupported(member);
			} catch (NumberFormatException e) {
				throw unsupported(member);
			}
		}

		String identifier() {
			String value = next();
			if (!identifier)
				throw unsupported(member);
			return value.startsWith("\u0001") ? value.substring(1) : value;
		}

		String next() {
			identifier = false;
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
						throw unsupported(member);
				} else
					break;
			}
			tokenStart = position;
			if (position == text.length())
				return "";
			char ch = text.charAt(position++);
			if (ch == '\"' || ch == '\'') {
				StringBuilder value = new StringBuilder();
				while (position < text.length()) {
					char current = text.charAt(position++);
					if (current == ch) {
						if (position < text.length() && text.charAt(position) == ch) {
							value.append(ch);
							position++;
						} else {
							identifier = ch == '\"';
							return identifier ? "\u0001" + value : "\u0002";
						}
					} else
						value.append(current);
				}
				throw unsupported(member);
			}
			if (Character.isLetter(ch) || ch == '_' || ch == '@' || ch == '#' || ch == '$') {
				int start = position - 1;
				while (position < text.length()) {
					char current = text.charAt(position);
					if (!(Character.isLetterOrDigit(current) || current == '_' || current == '@' || current == '#'
							|| current == '$'))
						break;
					position++;
				}
				identifier = true;
				return text.substring(start, position).toUpperCase(Locale.ROOT);
			}
			return Character.toString(ch);
		}
	}

	private static IllegalArgumentException unsupported(Db2ModuleMembers.Member member) {
		return new IllegalArgumentException(
				"Db2 module routine needs complete scoped ALTER MODULE SQL with matching visibility, name and explicit SPECIFIC; published SQL prototypes must have no body; unsupported components require parent definition: "
						+ member.name());
	}
}

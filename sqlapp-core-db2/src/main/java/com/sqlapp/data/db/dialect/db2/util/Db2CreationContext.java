/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit creation context for catalog SQL containing unqualified references.
 */
public final class Db2CreationContext {
	private Db2CreationContext() {
	}

	public static String identifier(String name) {
		if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("Db2 identifier must not be blank or contain NUL");
		}
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	public static String qualified(String schema, String name) {
		return identifier(schema) + "." + identifier(name);
	}

	public static List<String> sql(String qualifier, String functionPath) {
		List<String> result = new ArrayList<>();
		if (qualifier != null && !qualifier.isBlank()) {
			identifier(qualifier);
			result.add("SET CURRENT SCHEMA = '" + qualifier.replace("'", "''") + "'");
		}
		if (functionPath != null && !functionPath.isBlank()) {
			List<String> parts = parsePath(functionPath);
			result.add("SET CURRENT PATH = "
					+ String.join(", ", parts.stream().map(Db2CreationContext::identifier).toList()));
		}
		return List.copyOf(result);
	}

	private static List<String> parsePath(String path) {
		List<String> result = new ArrayList<>();
		int offset = 0;
		while (offset < path.length()) {
			while (offset < path.length() && Character.isWhitespace(path.charAt(offset)))
				offset++;
			StringBuilder value = new StringBuilder();
			if (offset < path.length() && path.charAt(offset) == '"') {
				offset++;
				boolean closed = false;
				while (offset < path.length()) {
					char c = path.charAt(offset++);
					if (c == '"') {
						if (offset < path.length() && path.charAt(offset) == '"') {
							value.append('"');
							offset++;
						} else {
							closed = true;
							break;
						}
					} else
						value.append(c);
				}
				if (!closed)
					throw new IllegalArgumentException("Unterminated Db2 function path identifier");
			} else {
				int start = offset;
				while (offset < path.length() && path.charAt(offset) != ',')
					offset++;
				String token = path.substring(start, offset).trim();
				if (!token.matches("[\\p{L}_][\\p{L}\\p{N}_@$#]*")) {
					throw new IllegalArgumentException("Invalid Db2 function path identifier: " + token);
				}
				value.append(token.toUpperCase(java.util.Locale.ROOT));
			}
			identifier(value.toString());
			result.add(value.toString());
			while (offset < path.length() && Character.isWhitespace(path.charAt(offset)))
				offset++;
			if (offset < path.length()) {
				if (path.charAt(offset++) != ',' || path.substring(offset).isBlank()) {
					throw new IllegalArgumentException("Invalid Db2 function path separator");
				}
			}
		}
		return result;
	}
}

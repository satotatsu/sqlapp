/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative literal comparison: never evaluates Oracle expressions or collation. */
final class OracleListPartitionValues {
	private static final Pattern LITERAL = Pattern.compile("(?:[nN]?'(?:[^']|'')*'|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|(?i:NULL))");
	private OracleListPartitionValues() { }
	static boolean isDefault(String value) { return value != null && "DEFAULT".equalsIgnoreCase(value.trim()); }
	private static Set<String> values(String text) {
		if (text == null || text.isBlank()) throw new IllegalArgumentException("LIST partition values are required");
		Set<String> values = new HashSet<>();
		int offset = 0;
		while (offset < text.length()) {
			while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
			var matcher = LITERAL.matcher(text).region(offset, text.length());
			if (!matcher.lookingAt()) throw new IllegalArgumentException("LIST maintenance requires simple string, numeric or NULL literals");
			String literal = matcher.group();
			if ("NULL".equalsIgnoreCase(literal)) literal = "NULL";
			if (!values.add(literal)) throw new IllegalArgumentException("Duplicate LIST partition literal: " + literal);
			offset = matcher.end();
			while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
			if (offset == text.length()) return values;
			if (text.charAt(offset++) != ',' || text.substring(offset).isBlank())
				throw new IllegalArgumentException("Invalid LIST partition literal list");
		}
		throw new IllegalArgumentException("LIST partition values are required");
	}
	static void split(String original, String left, String right) {
		Set<String> first = values(left);
		if (isDefault(original)) {
			if (!isDefault(right)) throw new IllegalArgumentException("Splitting DEFAULT must retain DEFAULT as the second partition");
			return;
		}
		Set<String> combined = new HashSet<>(first), second = values(right);
		for (String value : second) if (!combined.add(value)) throw new IllegalArgumentException("Split LIST partitions must not overlap");
		if (!combined.equals(values(original))) throw new IllegalArgumentException("LIST split/merge must preserve the complete value set");
	}
	static void same(String table, String index) {
		if (isDefault(table) && isDefault(index)) return;
		if (!values(table).equals(values(index))) throw new IllegalArgumentException("Local index LIST values must match their table partition");
	}
}

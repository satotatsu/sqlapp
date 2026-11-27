/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;

/**
 * Proves absence of object references without evaluating a default expression.
 */
final class Db2LiteralDefault extends SqlTokenizer {
	private static final Pattern TOKEN = Pattern
			.compile("(?i)(?:NULL|TRUE|FALSE)\\b|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)|[()+*/\\-]");
	private final String source;

	private Db2LiteralDefault(String source) {
		super(source);
		this.source = source;
	}

	@Override
	protected boolean supportsNestedBlockComments() {
		return true;
	}

	static boolean isIndependent(String expression) {
		return expression != null && new Db2LiteralDefault(expression).independent();
	}

	private boolean independent() {
		boolean value = false;
		for (int position = 0; position < source.length();) {
			if (Character.isWhitespace(source.charAt(position))) {
				position++;
				continue;
			}
			var marker = getTextMarker(position);
			if (marker != null) {
				if (source.charAt(position) == '\'')
					value = true;
				else if (!source.startsWith("--", position) && !source.startsWith("/*", position))
					return false;
				position = marker.getEnd();
				continue;
			}
			var token = TOKEN.matcher(source).region(position, source.length());
			if (!token.lookingAt())
				return false;
			value = true;
			position = token.end();
		}
		return value;
	}
}

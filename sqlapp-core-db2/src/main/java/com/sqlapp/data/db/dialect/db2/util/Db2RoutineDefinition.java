/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.util;

import java.util.Locale;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.util.SqlTokenizer;

/**
 * Resolves catalog-generated specific identity into complete SQL routine DDL.
 */
public final class Db2RoutineDefinition {
	private Db2RoutineDefinition() {
	}

	private static final Pattern CREATE = Pattern
			.compile("(?is)^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:FUNCTION|PROCEDURE)\\b");

	public static String preserveSpecificName(String definition, String specificName) {
		if (definition == null || specificName == null)
			return definition;
		String masked = new Masked(definition).text();
		var create = CREATE.matcher(masked);
		if (!create.find())
			return definition;
		int depth = 0, language = -1;
		for (int position = create.end(); position < masked.length();) {
			char ch = masked.charAt(position);
			if (ch == '(') {
				depth++;
				position++;
				continue;
			}
			if (ch == ')') {
				depth--;
				position++;
				continue;
			}
			if (!Character.isLetter(ch) && ch != '_') {
				position++;
				continue;
			}
			int start = position++;
			while (position < masked.length()
					&& (Character.isLetterOrDigit(masked.charAt(position)) || masked.charAt(position) == '_'))
				position++;
			if (depth != 0)
				continue;
			String word = masked.substring(start, position).toUpperCase(Locale.ROOT);
			if (word.equals("SPECIFIC"))
				return definition;
			if (word.equals("LANGUAGE")) {
				var sql = Pattern.compile("(?i)\\s+SQL\\b").matcher(masked.substring(position));
				if (!sql.lookingAt())
					return definition;
				language = start;
			}
			if (word.equals("BEGIN") || word.equals("RETURN") || word.equals("WRAPPED"))
				break;
		}
		if (language < 0)
			return definition;
		return definition.substring(0, language) + "SPECIFIC " + Db2CreationContext.identifier(specificName) + " "
				+ definition.substring(language);
	}

	private static final class Masked extends SqlTokenizer {
		private final String source;

		Masked(String source) {
			super(source);
			this.source = source;
		}

		@Override
		protected boolean supportsNestedBlockComments() {
			return true;
		}

		String text() {
			StringBuilder value = new StringBuilder(source);
			for (int position = 0; position < source.length();) {
				var marker = getTextMarker(position);
				if (marker == null) {
					position++;
					continue;
				}
				for (int index = marker.getStart(); index < marker.getEnd(); index++)
					value.setCharAt(index, ' ');
				position = marker.getEnd();
			}
			return value.toString();
		}
	}
}

package com.sqlapp.data.db.dialect.postgres.metadata;

import java.util.ArrayList;
import java.util.List;

/** Splits the server-deparsed default expression list, not user SQL. */
final class PostgresRoutineExpressions {

	private PostgresRoutineExpressions() { }

	static List<String> split(String text) {
		List<String> result = new ArrayList<>();
		if (text == null || text.isBlank()) return result;
		int start = 0, depth = 0;
		char quote = 0;
		boolean escapes = false;
		String dollar = null;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (dollar != null) {
				if (text.startsWith(dollar, i)) { i += dollar.length() - 1; dollar = null; }
				continue;
			}
			if (quote != 0) {
				if (escapes && c == '\\') { i++; continue; }
				if (c == quote) {
					if (i + 1 < text.length() && text.charAt(i + 1) == quote) i++;
					else quote = 0;
				}
				continue;
			}
			if (c == '\'' || c == '"') {
				quote = c;
				escapes = c == '\'' && i > 0 && (text.charAt(i - 1) == 'E' || text.charAt(i - 1) == 'e')
						&& (i == 1 || !Character.isJavaIdentifierPart(text.charAt(i - 2)));
			} else if (c == '$') {
				var matcher = java.util.regex.Pattern.compile("\\$(?:[A-Za-z_][A-Za-z_0-9]*)?\\$").matcher(text);
				matcher.region(i, text.length());
				if (matcher.lookingAt()) { dollar = matcher.group(); i += dollar.length() - 1; }
			} else if (c == '(' || c == '[') depth++;
			else if (c == ')' || c == ']') {
				if (--depth < 0) throw new IllegalArgumentException("Unbalanced routine default expressions: " + text);
			} else if (c == ',' && depth == 0) {
				result.add(text.substring(start, i).trim()); start = i + 1;
			}
		}
		if (quote != 0 || dollar != null || depth != 0) throw new IllegalArgumentException("Unterminated routine default expressions: " + text);
		result.add(text.substring(start).trim());
		if (result.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("Empty routine default expression: " + text);
		return result;
	}
}

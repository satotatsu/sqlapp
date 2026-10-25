/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import java.util.regex.Pattern;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter.TextType;

/**
 * Schema text collections contain lines, never implicit statement boundaries.
 */
final class Db2DefinitionSql {
	private Db2DefinitionSql() {
	}

	private static final Pattern SCRIPT = Pattern
			.compile("\\A\\s*--#SET\\s+TERMINATOR\\s+\\S+[^\\S\\r\\n]*(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);

	static String text(List<String> lines) {
		if (lines.stream().anyMatch(line -> line == null))
			throw new IllegalArgumentException("Db2 SQL text cannot contain null lines");
		String text = String.join("\n", lines);
		if (text.isBlank() || text.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Db2 requires non-empty executable SQL text without NUL characters");
		return text;
	}

	static List<String> statements(List<String> lines, Dialect dialect) {
		String text = text(lines);
		// Only an explicit CLP terminator directive opts into script splitting.
		// Native routine bodies can contain semicolons and must remain intact.
		if (!SCRIPT.matcher(text).find())
			return List.of(text);
		List<String> sql = dialect.createSqlSplitter().parse(text).stream()
				.filter(part -> part.getTextType() == TextType.SQL).map(part -> part.getText()).toList();
		if (sql.isEmpty())
			throw new IllegalArgumentException("Db2 definition script contains no executable SQL");
		return sql;
	}
}

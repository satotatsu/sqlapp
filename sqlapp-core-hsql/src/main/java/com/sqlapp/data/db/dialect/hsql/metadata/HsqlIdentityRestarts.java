/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the next value of plain identity columns. HSQLDB exposes it in no
 * INFORMATION_SCHEMA view; the schema-only {@code SCRIPT} output carries it as
 * {@code ALTER TABLE s.t ALTER COLUMN c RESTART WITH n}. {@code SCRIPT} needs
 * the DBA role, so other users get an empty result and keep the start value.
 */
final class HsqlIdentityRestarts {
	private static final String IDENTIFIER = "(\"(?:\"\"|[^\"])*\"|[^\\s.\"]+)";
	private static final Pattern RESTART = Pattern.compile("^ALTER\\s+TABLE\\s+" + IDENTIFIER + "\\." + IDENTIFIER
			+ "\\s+ALTER\\s+COLUMN\\s+" + IDENTIFIER + "\\s+RESTART\\s+WITH\\s+(-?\\d+)\\s*$",
			Pattern.CASE_INSENSITIVE);

	private HsqlIdentityRestarts() {
	}

	static String key(String schema, String table, String column) {
		return schema + "\u0000" + table + "\u0000" + column;
	}

	/**
	 * @return next identity value keyed by {@link #key(String, String, String)}
	 */
	static Map<String, Long> read(Connection connection) {
		try (var sql = connection.createStatement()) {
			try (var rs = sql
					.executeQuery("SELECT ADMIN FROM INFORMATION_SCHEMA.SYSTEM_USERS WHERE USER_NAME=CURRENT_USER")) {
				if (!rs.next() || !rs.getBoolean(1))
					return Collections.emptyMap();
			}
			Map<String, Long> result = new HashMap<>();
			try (var rs = sql.executeQuery("SCRIPT")) {
				while (rs.next())
					parse(rs.getString(1), result);
			}
			return result;
		} catch (SQLException e) {
			return Collections.emptyMap();
		}
	}

	static void parse(String command, Map<String, Long> result) {
		if (command == null)
			return;
		var matcher = RESTART.matcher(command.strip());
		if (matcher.matches())
			result.put(key(unquote(matcher.group(1)), unquote(matcher.group(2)), unquote(matcher.group(3))),
					Long.valueOf(matcher.group(4)));
	}

	private static String unquote(String identifier) {
		if (identifier.length() >= 2 && identifier.startsWith("\"") && identifier.endsWith("\""))
			return identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"");
		return identifier;
	}

	static boolean needed(List<com.sqlapp.data.schemas.Column> columns) {
		for (var column : columns)
			if (column.isIdentity() && column.getSequenceName() == null)
				return true;
		return false;
	}
}

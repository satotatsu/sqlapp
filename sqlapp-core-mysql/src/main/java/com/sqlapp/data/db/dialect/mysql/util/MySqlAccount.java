/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.util;

/**
 * Account identity includes both user and host; SQL uses mode-independent
 * identifier quotes.
 */
public final class MySqlAccount {
	private MySqlAccount() {
	}

	public static String name(String user, String host) {
		if (user == null || host == null)
			throw invalid();
		return literal(user) + "@" + literal(host);
	}

	public static String normalize(String account) {
		String[] parts = parse(account);
		return name(parts[0], parts[1]);
	}

	/** INFORMATION_SCHEMA GRANTEE uses raw components, not escaped SQL literals. */
	public static String fromGrantee(String grantee) {
		if (grantee == null || !grantee.startsWith("'") || !grantee.endsWith("'"))
			throw invalid();
		int separator = grantee.indexOf("'@'");
		if (separator < 1 || separator != grantee.lastIndexOf("'@'"))
			throw new IllegalArgumentException(
					"Ambiguous INFORMATION_SCHEMA MySQL grantee; use separate user and host fields");
		return name(grantee.substring(1, separator), grantee.substring(separator + 3, grantee.length() - 1));
	}

	public static String sql(String account) {
		String[] parts = parse(account);
		return "`" + parts[0].replace("`", "``") + "`@`" + parts[1].replace("`", "``") + "`";
	}

	public static String user(String account) {
		return parse(account)[0];
	}

	public static String host(String account) {
		return parse(account)[1];
	}

	private static String literal(String value) {
		return "'" + value.replace("\\", "\\\\").replace("'", "''").replace("\0", "\\0").replace("\u001a", "\\Z") + "'";
	}

	private static String[] parse(String account) {
		if (account == null)
			throw invalid();
		String text = account.trim();
		String[] parts = new String[2];
		int offset = 0;
		for (int part = 0; part < 2; part++) {
			if (offset >= text.length() || (text.charAt(offset) != '\'' && text.charAt(offset) != '`'))
				throw invalid();
			char quote = text.charAt(offset++);
			StringBuilder value = new StringBuilder();
			boolean closed = false;
			while (offset < text.length()) {
				char ch = text.charAt(offset++);
				if (ch == quote) {
					if (offset < text.length() && text.charAt(offset) == quote) {
						value.append(quote);
						offset++;
					} else {
						closed = true;
						break;
					}
				} else if (ch == '\\' && quote == '\'') {
					if (offset >= text.length())
						throw invalid();
					char escaped = text.charAt(offset++);
					value.append(switch (escaped) {
					case '0' -> '\0';
					case 'b' -> '\b';
					case 'n' -> '\n';
					case 'r' -> '\r';
					case 't' -> '\t';
					case 'Z' -> '\u001a';
					default -> escaped;
					});
				} else
					value.append(ch);
			}
			if (!closed)
				throw invalid();
			parts[part] = value.toString();
			if (part == 0 && (offset >= text.length() || text.charAt(offset++) != '@'))
				throw invalid();
		}
		if (offset != text.length())
			throw invalid();
		return parts;
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException(
				"MySQL account requires explicit quoted user and host, for example 'reader'@'localhost'");
	}
}

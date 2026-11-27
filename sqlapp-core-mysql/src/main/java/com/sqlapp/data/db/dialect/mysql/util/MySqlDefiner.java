/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.util;

import java.util.Map;

/**
 * Preserves catalog identity separately from explicitly quoted account
 * configuration.
 */
public final class MySqlDefiner {
	/** Optional explicit account, for example 'owner'@'localhost'. */
	public static final String DEFINER = "mysql_definer";
	/** Raw INFORMATION_SCHEMA identity. Never interpret this text as SQL. */
	public static final String RAW_DEFINER = "mysql_raw_definer";

	private MySqlDefiner() {
	}

	public static void read(Map<String, String> specifics, String raw) {
		if (raw != null)
			specifics.put(RAW_DEFINER, raw);
	}

	public static void append(MySqlSqlBuilder builder, Map<String, String> specifics) {
		append(builder, specifics, null);
	}

	/** Explicit configuration takes precedence over raw catalog identity. */
	public static void append(MySqlSqlBuilder builder, Map<String, String> specifics, String rawFallback) {
		String explicit = specifics.get(DEFINER);
		String account;
		if (explicit != null) {
			account = MySqlAccount.sql(explicit);
		} else {
			String raw = specifics.getOrDefault(RAW_DEFINER, rawFallback);
			if (raw == null)
				return;
			int separator = raw.indexOf('@');
			if (separator < 0 || separator != raw.lastIndexOf('@'))
				throw new IllegalArgumentException("Ambiguous MySQL catalog DEFINER; set specifics[\"" + DEFINER
						+ "\"] to an explicit quoted user@host account, or supply a complete definition");
			account = MySqlAccount.sql(MySqlAccount.name(raw.substring(0, separator), raw.substring(separator + 1)));
		}
		builder.space()._add("DEFINER = ")._add(account);
	}
}

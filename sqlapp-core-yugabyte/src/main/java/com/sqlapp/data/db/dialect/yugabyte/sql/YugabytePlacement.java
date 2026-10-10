/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.sql;

import java.util.Map;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;

final class YugabytePlacement {
	private YugabytePlacement() { }
	static int hashColumns(Map<String,String> settings) {
		String value = settings.get("YSQL_HASH_COLUMNS");
		if (value == null) return -1;
		int result = Integer.parseInt(value);
		if (result < 0) throw new IllegalArgumentException("YSQL_HASH_COLUMNS must be non-negative");
		return result;
	}
	static void tablespace(String name, PostgresSqlBuilder builder) {
		if (name != null) builder.space()._add("TABLESPACE").space().name(name);
	}
	static void split(Map<String,String> settings, PostgresSqlBuilder builder) {
		String count = settings.get("YSQL_SPLIT_INTO"), range = settings.get("YSQL_SPLIT_AT_BASE64");
		if (count != null && range != null) throw new IllegalArgumentException("Choose YSQL_SPLIT_INTO or YSQL_SPLIT_AT_BASE64");
		if ((count != null || range != null) && "true".equals(settings.get("YSQL_COLOCATION")))
			throw new IllegalArgumentException("Colocated YSQL relations cannot have split clauses");
		if (count != null) {
			int n = Integer.parseInt(count);
			if (n < 1) throw new IllegalArgumentException("YSQL_SPLIT_INTO must be positive");
			builder.space()._add("SPLIT INTO " + n + " TABLETS");
		}
		if (range != null) {
			String clause = new String(Base64.getDecoder().decode(range), StandardCharsets.UTF_8).trim();
			if (!clause.startsWith("SPLIT AT VALUES")) throw new IllegalArgumentException("Invalid YSQL range split clause");
			builder.space()._add(clause);
		}
	}
}

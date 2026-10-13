/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.edb.oracle.sql;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
/** Preserves literal quotes/backslashes independently of standard_conforming_strings. */
final class EdbOracleSqlLiterals {
	private EdbOracleSqlLiterals() { }
	static void add(PostgresSqlBuilder builder,String value) {
		builder._add("E'")._add(value.replace("\\","\\\\").replace("'","''"))._add("'");
	}
}

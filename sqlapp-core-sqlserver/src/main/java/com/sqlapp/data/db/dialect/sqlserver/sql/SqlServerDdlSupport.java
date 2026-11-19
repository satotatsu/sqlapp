/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

/** SQL Server Unicode string literals for unquoted model identifiers and values. */
final class SqlServerDdlSupport {
	private SqlServerDdlSupport() { }
	static String nchar(String value) { return "N'" + value.replace("'", "''") + "'"; }
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

/**
 * SQL Server Unicode string literals for unquoted model identifiers and values.
 */
final class SqlServerDdlSupport {
	private SqlServerDdlSupport() {
	}

	static String nchar(String value) {
		return "N'" + value.replace("'", "''") + "'";
	}

	/**
	 * SQL Serverのメモリ最適化テーブルかを返します。TableType.MemoryはHSQL/H2の通常テーブルにも 使われるため、SQL
	 * Serverのメタデータが設定するis_memory_optimizedだけで判定します。
	 */
	static boolean isMemoryOptimized(com.sqlapp.data.schemas.Table table) {
		return Boolean.TRUE.equals(table.getSpecifics().get("is_memory_optimized", Boolean.class));
	}
}

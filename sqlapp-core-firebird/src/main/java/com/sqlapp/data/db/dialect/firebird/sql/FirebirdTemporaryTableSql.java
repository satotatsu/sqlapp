/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.schemas.Table;

final class FirebirdTemporaryTableSql {
	private FirebirdTemporaryTableSql() { }

	static void requireSupportedDialect(FirebirdSqlBuilder builder) {
		if (builder.getDialect().compareTo(DialectHolder.defaultDialect20) < 0)
			throw new IllegalArgumentException("Global temporary tables require Firebird 2.1 or later");
	}

	static void addOnCommit(Table table, boolean preserveDefault, FirebirdSqlBuilder builder) {
		String onCommit = table.getSpecifics().get("firebird.onCommit");
		if (onCommit == null) onCommit = preserveDefault ? "PRESERVE ROWS" : "DELETE ROWS";
		if (!java.util.Set.of("PRESERVE ROWS", "DELETE ROWS").contains(onCommit))
			throw new IllegalArgumentException("firebird.onCommit must be PRESERVE ROWS or DELETE ROWS");
		builder._add(" ON COMMIT ")._add(onCommit);
	}
}

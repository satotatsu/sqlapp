/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.sql;

import java.util.function.Predicate;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Keep SQL Server MERGE valid when the selected key consumes all mutable
 * columns.
 */
final class SqlServerMergeSupport {
	private SqlServerMergeSupport() {
	}

	static boolean hasUpdates(Table table, SqlSignature signature, Predicate<Column> updateable) {
		var selected = signature.getSelectedColumnsHolder();
		return table.getColumns().stream()
				.anyMatch(column -> updateable.test(column) && !selected.getKeyColumns().contains(column)
						&& (!selected.isUniqueKey() || !signature.getPrimaryKey().getKeyColumns().contains(column)));
	}
}

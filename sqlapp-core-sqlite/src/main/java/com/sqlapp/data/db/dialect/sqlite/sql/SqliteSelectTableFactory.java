/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SelectTableFactory;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Uses shared whole-table conditions with an explicit SQLite database identity.
 */
public class SqliteSelectTableFactory extends SelectTableFactory {
	@Override
	protected AbstractSqlBuilder<?> newSqlBuilder(Dialect dialect) {
		return new SqliteDmlSqlBuilder(dialect);
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

final class DuckDbSqlFactoryTestSupport {

	private DuckDbSqlFactoryTestSupport() {
	}

	static SqlFactoryRegistry registry() {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry();
	}
}

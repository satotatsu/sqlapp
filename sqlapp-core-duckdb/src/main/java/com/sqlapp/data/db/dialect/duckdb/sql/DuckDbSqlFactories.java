/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;

import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.AbstractNamedObject;

/**
 * Helpers shared by the DuckDB SQL factories.
 */
final class DuckDbSqlFactories {

	private DuckDbSqlFactories() {
	}

	/**
	 * Appends the {@code COMMENT ON} statement for the object.
	 */
	static void addComment(final AbstractSqlFactory<?, ?> owner, final AbstractNamedObject<?> object,
			final List<SqlOperation> sqlList) {
		final DuckDbSetCommentFactory factory = new DuckDbSetCommentFactory();
		factory.setDialect(owner.getDialect());
		factory.setOptions(owner.getOptions());
		sqlList.addAll(factory.createSql(object));
	}
}

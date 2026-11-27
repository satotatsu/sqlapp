/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAnalyzeTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * {@code ANALYZE schema.table} for DuckDB. A table request is never widened to
 * the whole database.
 */
public class DuckDbAnalyzeTableFactory extends AbstractAnalyzeTableFactory<DuckDbSqlBuilder> {

	@Override
	protected void addAnalyzeTable(final Table obj, final DuckDbSqlBuilder builder) {
		if (obj.getName() == null || obj.getName().isBlank()) {
			throw new IllegalArgumentException("DuckDB ANALYZE requires a table name");
		}
		builder.analyze().space().name(obj, getOptions().isDecorateSchemaName());
	}
}

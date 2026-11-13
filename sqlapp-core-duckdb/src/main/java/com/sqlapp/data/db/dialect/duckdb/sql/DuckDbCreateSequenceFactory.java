/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * CREATE SEQUENCE for DuckDB. DuckDB sequences are always BIGINT and have no
 * cache or ordering option; those model properties are not emitted.
 */
public class DuckDbCreateSequenceFactory extends AbstractCreateSequenceFactory<DuckDbSqlBuilder> {

	@Override
	protected void addIfNotExists(final Sequence obj, final DuckDbSqlBuilder builder) {
		builder.ifNotExists(getOptions().isCreateIfNotExists());
	}

	@Override
	protected void addDataType(final Sequence obj, final DuckDbSqlBuilder builder) {
	}

	@Override
	protected void addCache(final Sequence obj, final DuckDbSqlBuilder builder) {
	}

	@Override
	protected void addOrder(final Sequence obj, final DuckDbSqlBuilder builder) {
	}
}

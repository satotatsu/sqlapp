/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;

/**
 * {@code CHECKPOINT database} for DuckDB, registered as {@link SqlType#DEFRAG}
 * of a {@link Catalog}. A checkpoint writes the WAL to the database file and
 * reclaims space of deleted rows. {@code FORCE
 * CHECKPOINT} aborts running transactions and is therefore not generated.
 */
public class DuckDbCheckpointFactory extends AbstractSqlFactory<Catalog, DuckDbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Catalog catalog) {
		if (catalog.getName() == null || catalog.getName().isBlank()) {
			throw new IllegalArgumentException("DuckDB CHECKPOINT requires a database (catalog) name");
		}
		final DuckDbSqlBuilder builder = createSqlBuilder(getDialect());
		builder._add("CHECKPOINT").space().name(catalog.getName());
		final List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.DEFRAG, catalog);
		return result;
	}
}

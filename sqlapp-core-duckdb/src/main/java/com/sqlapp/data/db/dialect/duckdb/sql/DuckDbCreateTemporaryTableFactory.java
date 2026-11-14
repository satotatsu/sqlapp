/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTemporaryTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/**
 * {@code CREATE TEMPORARY TABLE} for DuckDB. Temporary tables live in the
 * session's {@code temp} database and cannot be schema qualified, so the name
 * is never decorated. Only self-referencing foreign keys can be kept because
 * DuckDB rejects foreign keys across catalogs.
 */
public class DuckDbCreateTemporaryTableFactory extends AbstractCreateTemporaryTableFactory<DuckDbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Table source) {
		final String prefix = nullToEmpty(getTableOptions().getTemporaryTableNamePrefix().apply(source));
		final String suffix = nullToEmpty(getTableOptions().getTemporaryTableNameSuffix().apply(source));
		final Table temporary = source.clone();
		temporary.setName(prefix + source.getName() + suffix);
		temporary.setSchemaName(null);
		temporary.setCatalogName(null);
		for (final Index index : temporary.getIndexes()) {
			index.setName(prefix + index.getName() + suffix);
		}
		for (final ForeignKeyConstraint key : temporary.getConstraints().getForeignKeyConstraints()) {
			if (!source.getName().equals(key.getRelatedTableName()) || key.getRelatedTableSchemaName() != null
					&& !key.getRelatedTableSchemaName().equals(source.getSchemaName())) {
				throw new IllegalArgumentException(
						"DuckDB temporary tables can only reference themselves in foreign keys: " + key.getName());
			}
			final Column[] columns = key.getRelatedColumns().stream()
					.map(column -> new Column(column.getName()).setTableName(temporary.getName()))
					.toArray(Column[]::new);
			key.getRelatedColumns().clear();
			key.setRelatedTableName(temporary.getName());
			key.setRelatedTableSchemaName(null);
			key.getRelatedColumns().addAll(columns);
		}
		final DuckDbCreateTableFactory factory = new DuckDbCreateTableFactory() {
			@Override
			protected void addCreateObject(final Table table, final DuckDbSqlBuilder builder) {
				builder.create().space()._add("TEMPORARY").table().ifNotExists(getOptions().isCreateIfNotExists())
						.space().name(table, false);
			}
		};
		factory.setSqlFactoryRegistry(getSqlFactoryRegistry());
		factory.setDialect(getDialect());
		factory.setOptions(getOptions());
		factory.setTableOptions(getTableOptions());
		final List<SqlOperation> operations = factory.createSql(temporary);
		operations.set(0, createOperation(operations.get(0).getSqlText(), SqlType.CREATE_TEMPORARY, source, temporary));
		return operations;
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}
}

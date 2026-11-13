/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB base tables from {@code duckdb_tables()}.
 */
public class DuckDbTableReader extends TableReader {

	protected DuckDbTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("tables.sql");
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table table = createTable(getString(rs, TABLE_NAME));
				table.setCatalogName(getString(rs, TABLE_CATALOG));
				table.setSchemaName(getString(rs, TABLE_SCHEMA));
				table.setRemarks(getString(rs, REMARKS));
				setStatistics(rs, "estimated_size", table);
				result.add(table);
			}
		});
		return result;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new DuckDbColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new DuckDbIndexReader(getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new DuckDbUniqueConstraintReader(getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new DuckDbCheckConstraintReader(getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new DuckDbForeignKeyConstraintReader(getDialect());
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}

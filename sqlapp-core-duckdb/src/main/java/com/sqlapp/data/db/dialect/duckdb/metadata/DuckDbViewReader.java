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
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ExcludeConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.ViewReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB views. DuckDB stores the complete, executable
 * {@code CREATE VIEW} statement, which is kept as the view definition.
 */
public class DuckDbViewReader extends ViewReader {

	protected DuckDbViewReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("views.sql");
		final List<Table> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Table view = createTable(getString(rs, TABLE_NAME));
				view.setCatalogName(getString(rs, TABLE_CATALOG));
				view.setSchemaName(getString(rs, TABLE_SCHEMA));
				view.setRemarks(getString(rs, REMARKS));
				final String sql = getString(rs, "view_sql");
				if (sql == null || sql.isBlank()) {
					throw new SQLException("DuckDB view definition is unavailable: " + view.getName());
				}
				view.setDefinition(stripTerminator(sql));
				result.add(view);
			}
		});
		return result;
	}

	static String stripTerminator(final String sql) {
		String text = sql.strip();
		while (text.endsWith(";")) {
			text = text.substring(0, text.length() - 1).strip();
		}
		return text;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new DuckDbColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return null;
	}

	@Override
	protected ExcludeConstraintReader newExcludeConstraintReader() {
		return null;
	}
}

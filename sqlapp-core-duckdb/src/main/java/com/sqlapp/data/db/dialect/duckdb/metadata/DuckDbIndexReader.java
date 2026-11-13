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
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbDdlParser;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB indexes. Key columns are taken from the stored index DDL because
 * {@code duckdb_indexes().expressions} is formatted differently across DuckDB
 * releases. DuckDB ART indexes do not keep a sort order, so every key is read
 * as ascending.
 */
public class DuckDbIndexReader extends IndexReader {

	/**
	 * Specifics key holding the original key list when an index contains
	 * expressions.
	 */
	public static final String INDEX_KEYS = "duckdb.index.keys";

	public DuckDbIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("indexes.sql");
		final List<Index> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Index index = new Index(getString(rs, INDEX_NAME));
				index.setCatalogName(getString(rs, TABLE_CATALOG));
				index.setSchemaName(getString(rs, TABLE_SCHEMA));
				index.setTableName(getString(rs, TABLE_NAME));
				index.setUnique(rs.getBoolean("is_unique"));
				index.setRemarks(getString(rs, REMARKS));
				final String sql = getString(rs, "index_sql");
				final List<String> keys;
				try {
					keys = DuckDbDdlParser.indexKeys(sql);
				} catch (final IllegalArgumentException e) {
					throw new SQLException("Failed to read DuckDB index keys: " + index.getName(), e);
				}
				boolean expression = false;
				for (final String key : keys) {
					final String columnName = DuckDbDdlParser.identifierOrNull(key);
					if (columnName == null) {
						expression = true;
						index.getColumns().add(key, Order.Asc);
					} else {
						index.getColumns().add(columnName, Order.Asc);
					}
				}
				if (expression) {
					index.setIndexType(IndexType.Function);
					index.getSpecifics().put(INDEX_KEYS, String.join(", ", keys));
				}
				result.add(index);
			}
		});
		return result;
	}
}

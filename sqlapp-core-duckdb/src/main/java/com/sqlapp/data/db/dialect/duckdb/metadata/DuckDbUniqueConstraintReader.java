/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB primary key and unique constraints (DuckDB 1.1+). DuckDB
 * generates constraint names itself; names given in DDL are not retained.
 */
public class DuckDbUniqueConstraintReader extends UniqueConstraintReader {

	public DuckDbUniqueConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		DuckDbMetadataSupport.requireConstraintMetadata(getDialect());
		final SqlNode node = getSqlNodeCache().getString("uniqueConstraints.sql");
		final List<UniqueConstraint> result = list();
		final Map<List<String>, UniqueConstraint> map = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String catalogName = getString(rs, TABLE_CATALOG);
				final String schemaName = getString(rs, TABLE_SCHEMA);
				final String tableName = getString(rs, TABLE_NAME);
				final String constraintName = getString(rs, CONSTRAINT_NAME);
				final List<String> key = Arrays.asList(catalogName, schemaName, tableName, constraintName);
				UniqueConstraint constraint = map.get(key);
				if (constraint == null) {
					final boolean primary = "PRIMARY KEY".equalsIgnoreCase(getString(rs, "constraint_type"));
					constraint = new UniqueConstraint(constraintName, primary);
					constraint.setCatalogName(catalogName);
					constraint.setSchemaName(schemaName);
					constraint.setTableName(tableName);
					map.put(key, constraint);
					result.add(constraint);
				}
				final Column column = new Column(getString(rs, COLUMN_NAME));
				column.setTableName(tableName);
				constraint.getColumns().add(column, Order.Asc);
			}
		});
		return result;
	}
}

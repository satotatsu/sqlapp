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
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads DuckDB CHECK constraints (DuckDB 1.1+). NOT NULL constraints are read
 * as column nullability instead.
 */
public class DuckDbCheckConstraintReader extends CheckConstraintReader {

	public DuckDbCheckConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		DuckDbMetadataSupport.requireConstraintMetadata(getDialect());
		final SqlNode node = getSqlNodeCache().getString("checkConstraints.sql");
		final List<CheckConstraint> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final CheckConstraint constraint = new CheckConstraint(getString(rs, CONSTRAINT_NAME),
						getString(rs, "check_expression"));
				constraint.setCatalogName(getString(rs, TABLE_CATALOG));
				constraint.setSchemaName(getString(rs, TABLE_SCHEMA));
				constraint.setTableName(getString(rs, TABLE_NAME));
				result.add(constraint);
			}
		});
		return result;
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.metadata;

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

/** Cloud Spanner CHECK constraint metadata reader. */
public class SpannerCheckConstraintReader extends CheckConstraintReader {
	public SpannerCheckConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<CheckConstraint> result = list();
		execute(connection, getSqlNode(productVersionInfo), context,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet rs)
							throws SQLException {
						final CheckConstraint constraint = new CheckConstraint(
								getString(rs, CONSTRAINT_NAME),
								getString(rs, "check_clause"));
						constraint.setDialect(getDialect());
						constraint.setCatalogName(getString(rs, TABLE_CATALOG));
						constraint.setSchemaName(getString(rs, TABLE_SCHEMA));
						constraint.setTableName(getString(rs, TABLE_NAME));
						setSpecifics(rs, "spanner_state", constraint);
						result.add(constraint);
					}
				});
		return result;
	}

	protected SqlNode getSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("checkConstraints.sql");
	}
}

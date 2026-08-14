/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

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

/** SAP HANA check-constraint reader backed by {@code CONSTRAINTS}. */
public class SapHanaCheckConstraintReader extends CheckConstraintReader {

	protected SapHanaCheckConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("checkConstraints.sql");
		final List<CheckConstraint> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs)
					throws SQLException {
				result.add(createCheckConstraint(rs));
			}
		});
		return result;
	}

	protected CheckConstraint createCheckConstraint(final ExResultSet rs)
			throws SQLException {
		final CheckConstraint constraint = new CheckConstraint(
				getString(rs, CONSTRAINT_NAME),
				getString(rs, "CHECK_CONDITION"));
		constraint.setSchemaName(getString(rs, SCHEMA_NAME));
		constraint.setTableName(getString(rs, TABLE_NAME));
		return constraint;
	}
}

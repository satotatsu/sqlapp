/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
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
 * Reads MySQL and MariaDB check constraints from INFORMATION_SCHEMA.
 */
public class MySqlCheckConstraintReader extends CheckConstraintReader {

	public MySqlCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("checkConstraints.sql");
		List<CheckConstraint> result = new ArrayList<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalogName = getString(rs, "CONSTRAINT_CATALOG");
				String schemaName = getString(rs, "CONSTRAINT_SCHEMA");
				String tableName = getString(rs, TABLE_NAME);
				String constraintName = getString(rs, CONSTRAINT_NAME);
				CheckConstraint constraint = new CheckConstraint(constraintName, getString(rs, "CHECK_CLAUSE"));
				constraint.setCatalogName(catalogName);
				constraint.setSchemaName(schemaName);
				constraint.setTableName(tableName);
				String enforced = getString(rs, "ENFORCED");
				if (enforced != null) {
					constraint.setEnable("YES".equalsIgnoreCase(enforced));
				}
				result.add(constraint);
			}
		});
		return result;
	}
}

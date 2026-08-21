/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.QuadKeyMap;

/** Reads the CHECK metadata exposed by Vertica constraint_columns. */
public class VirticaCheckConstraintReader extends CheckConstraintReader {
	public VirticaCheckConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("checkConstraints.sql");
		QuadKeyMap<String, String, String, String, CheckConstraint> map = CommonUtils.quadKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				String schemaName = getString(rs, TABLE_SCHEMA);
				String tableName = getString(rs, TABLE_NAME);
				String name = getString(rs, CONSTRAINT_NAME);
				CheckConstraint constraint = map.get(null, schemaName, tableName, name);
				if (constraint == null) {
					constraint = new CheckConstraint(name, (String) null);
					constraint.setSchemaName(schemaName);
					constraint.setTableName(tableName);
					constraint.setEnable(rs.getBoolean("IS_ENABLED"));
					constraint.setRemarks(getString(rs, "REMARKS"));
					map.put(null, schemaName, tableName, name, constraint);
				}
				Column column = new Column(getString(rs, COLUMN_NAME));
				column.setSchemaName(schemaName);
				column.setTableName(tableName);
				column.setCheckConstraint(constraint);
			}
		});
		return map.toList();
	}
}

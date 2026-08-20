/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import static com.sqlapp.util.CommonUtils.tripleKeyMap;

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
import com.sqlapp.util.TripleKeyMap;

/** Reads Informix check constraints from the system catalog. */
public class InformixCheckConstraintReader extends CheckConstraintReader {
	public InformixCheckConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("checkConstraints.sql");
		TripleKeyMap<String, String, String, CheckConstraint> map = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				readConstraint(rs, map);
			}
		});
		return map.toList();
	}

	private void readConstraint(final ExResultSet rs,
			final TripleKeyMap<String, String, String, CheckConstraint> map)
			throws SQLException {
		String schemaName = getString(rs, SCHEMA_NAME);
		String tableName = getString(rs, TABLE_NAME);
		String constraintName = getString(rs, CONSTRAINT_NAME);
		String text = getString(rs, "definition");
		CheckConstraint constraint = map.get(schemaName, tableName, constraintName);
		if (constraint == null) {
			constraint = new CheckConstraint(constraintName, text);
			constraint.setCatalogName(getString(rs, CATALOG_NAME));
			constraint.setSchemaName(schemaName);
			constraint.setTableName(tableName);
			map.put(schemaName, tableName, constraintName, constraint);
		} else {
			constraint.setExpression(constraint.getExpression() + text);
		}
	}
}

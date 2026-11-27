/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Informix check constraints from the system catalog. */
public class InformixCheckConstraintReader extends CheckConstraintReader {
	public InformixCheckConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<CheckConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("checkConstraints.sql");
		final Map<Integer, CheckText> map = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				readConstraint(rs, map);
			}
		});
		return map.values().stream().map(value -> {
			final String expression = value.text.toString().stripTrailing();
			if (expression.isBlank())
				throw new IllegalStateException("Empty Informix CHECK expression: " + value.constraint.getName());
			value.constraint.setExpression(expression);
			return value.constraint;
		}).toList();
	}

	private void readConstraint(final ExResultSet rs, final Map<Integer, CheckText> map) throws SQLException {
		String schemaName = getString(rs, SCHEMA_NAME);
		String tableName = getString(rs, TABLE_NAME);
		String constraintName = getString(rs, CONSTRAINT_NAME);
		final String text = rs.getString("definition");
		if (text == null)
			throw new IllegalStateException("Missing Informix CHECK expression: " + schemaName + "." + constraintName);
		final int id = rs.getInt("constraint_id");
		CheckText value = map.get(id);
		if (value == null) {
			final CheckConstraint constraint = new CheckConstraint(constraintName, null);
			constraint.setCatalogName(getString(rs, CATALOG_NAME));
			constraint.setSchemaName(schemaName);
			constraint.setTableName(tableName);
			com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.apply(constraint,
					getString(rs, "constraint_state"));
			final String owner = getString(rs, "constraint_owner");
			if (owner != null && !owner.equals(schemaName))
				constraint.getSpecifics().put(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER, owner);
			value = new CheckText(constraint);
			map.put(id, value);
		}
		value.text.append(text); // Do not trim the catalog's 32-character chunks individually.
	}

	private static class CheckText {
		final CheckConstraint constraint;
		final StringBuilder text = new StringBuilder();

		CheckText(CheckConstraint constraint) {
			this.constraint = constraint;
		}
	}
}

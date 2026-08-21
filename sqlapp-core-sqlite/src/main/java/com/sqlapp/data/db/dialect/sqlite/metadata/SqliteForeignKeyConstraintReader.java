/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Reads SQLite foreign keys from {@code PRAGMA foreign_key_list}. */
public class SqliteForeignKeyConstraintReader
		extends ForeignKeyConstraintReader {
	public SqliteForeignKeyConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final String tableName = getTableName(context);
		if (tableName == null) {
			return list();
		}
		final String schemaName = getSchemaName(context) == null
				? "main" : getSchemaName(context);
		final String sql = "PRAGMA " + quoteIdentifier(schemaName)
				+ ".foreign_key_list(" + quoteString(tableName) + ")";
		final Map<Integer, ForeignKeyConstraint> constraints =
				new LinkedHashMap<>();
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				final int id = resultSet.getInt("id");
				final ForeignKeyConstraint constraint = constraints.computeIfAbsent(
						id, key -> createConstraint(context, schemaName, tableName,
								key, getUnchecked(resultSet, "table"),
								getUnchecked(resultSet, "on_update"),
								getUnchecked(resultSet, "on_delete")));
				constraint.getColumns().add(
						new Column(resultSet.getString("from")));
				constraint.getRelatedColumns().add(resultSet.getString("to"));
			}
			return List.copyOf(constraints.values());
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private ForeignKeyConstraint createConstraint(
			final ParametersContext context, final String schemaName,
			final String tableName, final int id, final String relatedTable,
			final String updateRule, final String deleteRule) {
		final ForeignKeyConstraint constraint = new ForeignKeyConstraint(
				"sqlite_fk_" + tableName + "_" + id);
		constraint.setDialect(getDialect());
		constraint.setCatalogName(getCatalogName(context));
		constraint.setSchemaName(getSchemaName(context));
		constraint.setTableName(tableName);
		constraint.setRelatedTableSchemaName(schemaName);
		constraint.setRelatedTableName(relatedTable);
		constraint.setUpdateRule(CascadeRule.parse(updateRule));
		constraint.setDeleteRule(CascadeRule.parse(deleteRule));
		return constraint;
	}

	private String getUnchecked(final java.sql.ResultSet resultSet,
			final String name) {
		try {
			return resultSet.getString(name);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String quoteString(final String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}

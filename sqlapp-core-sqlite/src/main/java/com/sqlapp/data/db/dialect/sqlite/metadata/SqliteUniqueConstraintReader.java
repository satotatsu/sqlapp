/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcPrimaryKeyConstraintReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Reads SQLite primary keys and UNIQUE constraints. */
public class SqliteUniqueConstraintReader extends UniqueConstraintReader {
	public SqliteUniqueConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<UniqueConstraint> result = readPrimaryKeys(connection, context);
		final String tableName = getTableName(context);
		if (tableName == null) {
			return result;
		}
		final String schemaName = getSchemaName(context) == null
				? "main" : getSchemaName(context);
		final String sql = "PRAGMA " + quoteIdentifier(schemaName)
				+ ".index_list(" + quoteString(tableName) + ")";
		try (var statement = connection.createStatement();
				ResultSet indexes = statement.executeQuery(sql)) {
			while (indexes.next()) {
				if (!indexes.getBoolean("unique")
						|| !hasColumn(indexes, "origin")
						|| !"u".equalsIgnoreCase(indexes.getString("origin"))) {
					continue;
				}
				final String indexName = indexes.getString("name");
				final UniqueConstraint constraint = new UniqueConstraint(indexName);
				constraint.setDialect(getDialect());
				constraint.setCatalogName(getCatalogName(context));
				constraint.setSchemaName(getSchemaName(context));
				constraint.setTableName(tableName);
				loadColumns(connection, schemaName, indexName, constraint);
				result.add(constraint);
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private List<UniqueConstraint> readPrimaryKeys(final Connection connection,
			final ParametersContext context) {
		final JdbcPrimaryKeyConstraintReader reader =
				new JdbcPrimaryKeyConstraintReader(getDialect());
		reader.setCatalogName(getCatalogName(context));
		reader.setSchemaName(getSchemaName(context));
		reader.setObjectName(getTableName(context));
		return reader.getAll(connection);
	}

	private void loadColumns(final Connection connection,
			final String schemaName, final String indexName,
			final UniqueConstraint constraint) throws SQLException {
		final String sql = "PRAGMA " + quoteIdentifier(schemaName)
				+ ".index_info(" + quoteString(indexName) + ")";
		try (var statement = connection.createStatement();
				ResultSet columns = statement.executeQuery(sql)) {
			while (columns.next()) {
				constraint.getColumns().add(new Column(columns.getString("name")));
			}
		}
	}

	private boolean hasColumn(final ResultSet resultSet, final String name)
			throws SQLException {
		for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
			if (name.equalsIgnoreCase(resultSet.getMetaData().getColumnLabel(i))) {
				return true;
			}
		}
		return false;
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String quoteString(final String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}

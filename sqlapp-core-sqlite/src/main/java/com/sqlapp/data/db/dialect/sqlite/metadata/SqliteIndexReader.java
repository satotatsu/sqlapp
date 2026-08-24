/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaProperties;

/** Calls SQLite JDBC's index API once per table, as required by the driver. */
public class SqliteIndexReader extends JdbcIndexReader {
	private static final Pattern WHERE_PATTERN = Pattern.compile("(?is)\\bWHERE\\b\\s+(.+)$");

	public SqliteIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Index> result = list();
		try {
			for (String tableName : tableNames(context)) {
				final ParametersContext tableContext = context.clone();
				tableContext.put(SchemaProperties.TABLE_NAME.getLabel(), tableName);
				result.addAll(getAllIndex(connection.getMetaData(), tableContext,
						false));
			}
			loadPartialIndexPredicates(connection, result);
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private void loadPartialIndexPredicates(final Connection connection,
			final List<Index> indexes) throws SQLException {
		for (Index index : indexes) {
			final String schemaName = index.getSchemaName() == null
					? "main" : index.getSchemaName();
			final String sql = "SELECT sql FROM " + quoteIdentifier(schemaName)
					+ ".sqlite_master WHERE type='index' AND name=?";
			String definition = null;
			try (var statement = connection.prepareStatement(sql)) {
				statement.setString(1, index.getName());
				try (var resultSet = statement.executeQuery()) {
					if (resultSet.next()) {
						definition = resultSet.getString(1);
						index.setWhere(extractWhere(definition));
					}
				}
			}
			loadColumnDetails(connection, schemaName, index, definition);
		}
	}

	private void loadColumnDetails(final Connection connection,
			final String schemaName, final Index index,
			final String definition) throws SQLException {
		final String sql = "PRAGMA " + quoteIdentifier(schemaName)
				+ ".index_xinfo(" + quoteString(index.getName()) + ")";
		final List<String> definitions =
				SqliteColumnReader.splitColumnDefinitions(definition);
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			final boolean hasDescending = hasColumn(resultSet, "desc");
			final boolean hasKey = hasColumn(resultSet, "key");
			index.getColumns().clear();
			while (resultSet.next()) {
				final String columnName = resultSet.getString("name");
				final int columnId = resultSet.getInt("cid");
				if (hasKey && !resultSet.getBoolean("key")
						|| !hasKey && columnName == null && columnId != -2) {
					continue;
				}
				final Order order = hasDescending && resultSet.getBoolean("desc")
						? Order.Desc : Order.Asc;
				if (columnName != null) {
					index.getColumns().add(new Column(columnName), order);
				} else {
					final int position = resultSet.getInt("seqno");
					if (position < definitions.size()) {
						index.getColumns().add(stripOrder(definitions.get(position)),
								order);
					}
				}
			}
		}
	}

	static String stripOrder(final String definition) {
		return definition.replaceFirst("(?is)\\s+(?:ASC|DESC)\\s*$", "")
				.trim();
	}

	private boolean hasColumn(final java.sql.ResultSet resultSet,
			final String name) throws SQLException {
		for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
			if (name.equalsIgnoreCase(resultSet.getMetaData().getColumnLabel(i))) {
				return true;
			}
		}
		return false;
	}

	static String extractWhere(final String sql) {
		if (sql == null) {
			return null;
		}
		final Matcher matcher = WHERE_PATTERN.matcher(sql);
		return matcher.find() ? matcher.group(1).trim() : null;
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String quoteString(final String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private List<String> tableNames(final ParametersContext context) {
		final List<String> result = list();
		final Object value = context.get(SchemaProperties.TABLE_NAME.getLabel());
		if (value instanceof Collection<?> collection) {
			collection.forEach(name -> result.add(String.valueOf(name)));
		} else if (value != null && value.getClass().isArray()) {
			for (int i = 0; i < Array.getLength(value); i++) {
				result.add(String.valueOf(Array.get(value, i)));
			}
		} else if (value != null) {
			result.add(String.valueOf(value));
		}
		return result;
	}
}

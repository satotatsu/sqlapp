/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.TableObjectReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;

/** SQLite table reader. */
public class SqliteTableReader extends JdbcTableReader {
	public SqliteTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Table> tables = new ArrayList<>();
		final String schemaName = getSchemaName(context) == null
				? "main" : getSchemaName(context);
		final String requestedTable = getObjectName(context);
		final String sql = "SELECT name FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_schema WHERE type='table' "
				+ "AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' ORDER BY name";
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				final String tableName = resultSet.getString("name");
				if (requestedTable != null
						&& !requestedTable.equalsIgnoreCase(tableName)) {
					continue;
				}
				final Table table = new Table(tableName);
				table.setDialect(getDialect());
				table.setCatalogName(getCatalogName(context));
				table.setSchemaName(schemaName);
				tables.add(table);
			}
			return tables;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SqliteColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new SqliteIndexReader(getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new SqliteUniqueConstraintReader(getDialect());
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> tables)
			throws SQLException {
		for (Table table : tables) {
			table.setDialect(getDialect());
			load(connection, table, getColumnReader());
			load(connection, table, getIndexReader());
			load(connection, table, getUniqueConstraintReader());
			load(connection, table, getForeignKeyConstraintReader());
			loadTableOptions(connection, table);
		}
	}

	private void loadTableOptions(final Connection connection, final Table table)
			throws SQLException {
		final String schemaName = table.getSchemaName() == null
				? "main" : table.getSchemaName();
		final String sql = "SELECT sql FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_schema WHERE type='table' AND name=?";
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, table.getName());
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return;
				}
				final String definition = resultSet.getString(1);
				if (definition == null) {
					return;
				}
				final String normalized = definition.toUpperCase(Locale.ROOT);
				final int closingParenthesis = normalized.lastIndexOf(')');
				final String options = closingParenthesis < 0 ? ""
						: normalized.substring(closingParenthesis + 1);
				if (options.matches("(?s).*\\bWITHOUT\\s+ROWID\\b.*")) {
					table.getSpecifics().put("without_rowid", Boolean.TRUE.toString());
				}
				if (options.matches("(?s).*\\bSTRICT\\b.*")) {
					table.getSpecifics().put("strict", Boolean.TRUE.toString());
				}
			}
		}
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private void load(final Connection connection, final Table table,
			final TableObjectReader<?> reader) {
		if (reader == null) {
			return;
		}
		reader.setCatalogName(table.getCatalogName());
		reader.setSchemaName(table.getSchemaName());
		reader.setObjectName(table.getName());
		reader.loadFull(connection, table);
	}
}

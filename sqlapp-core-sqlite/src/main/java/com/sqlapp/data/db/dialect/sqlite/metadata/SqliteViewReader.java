/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcViewReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

/** Reads SQLite views from each database's historical schema-table alias. */
public class SqliteViewReader extends JdbcViewReader {

	public SqliteViewReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SqliteColumnReader(getDialect());
	}

	@Override
	protected List<Table> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Table> result = list();
		final String schemaName = getSchemaName(context) == null ? "main" : getSchemaName(context);
		final String requestedView = getObjectName(context);
		final String sql = "SELECT name, sql FROM " + quoteIdentifier(schemaName)
				+ ".sqlite_master WHERE type='view' ORDER BY name";
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				final String viewName = resultSet.getString("name");
				if (requestedView != null && !requestedView.equalsIgnoreCase(viewName)) {
					continue;
				}
				final View view = new View(viewName);
				view.setDialect(getDialect());
				view.setCatalogName(getCatalogName(context));
				view.setSchemaName(schemaName);
				final String definition = resultSet.getString("sql");
				if (getReaderOptions().isReadDefinition()) {
					view.setDefinition(definition);
				}
				if (getReaderOptions().isReadStatement()) {
					view.setStatement(extractStatement(definition));
				}
				result.add(view);
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	static String extractStatement(final String definition) {
		return SqliteDdl.viewStatement(definition);
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Table> views)
			throws SQLException {
		if (views.isEmpty())
			return;
		var version = new ProductVersionInfo().setMajorVersion(connection.getMetaData().getDatabaseMajorVersion())
				.setMinorVersion(connection.getMetaData().getDatabaseMinorVersion());
		if (!SqliteIndexReader.supportsBulkIndexes(version)) {
			super.setMetadataDetail(connection, context, views);
			return;
		}
		var bulkContext = context.clone();
		bulkContext.put("tableName", views.stream().map(Table::getName).toList());
		var byName = views.stream().collect(java.util.stream.Collectors.toMap(Table::getName, view -> view));
		getColumnReader().getAllFull(connection, bulkContext)
				.forEach(column -> byName.get(column.getTableName()).getColumns().add(column));
	}

	private String quoteIdentifier(final String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}

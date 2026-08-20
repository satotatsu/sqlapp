/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcViewReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Informix view definitions from the system catalog. */
public class InformixViewReader extends JdbcViewReader {
	public InformixViewReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Table> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("views.sql");
		List<Table> result = list();
		Map<Table, StringBuilder> definitions = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				String name = getString(rs, TABLE_NAME);
				String schemaName = getString(rs, SCHEMA_NAME);
				Table view = result.isEmpty() ? null : result.get(result.size() - 1);
				if (view == null || !name.equals(view.getName())
						|| !schemaName.equals(view.getSchemaName())) {
					view = createTable(name);
					view.setCatalogName(getString(rs, CATALOG_NAME));
					view.setSchemaName(schemaName);
					result.add(view);
					definitions.put(view, new StringBuilder());
				}
				definitions.get(view).append(getString(rs, "view_definition"));
			}
		});
		definitions.forEach((view, definition) -> {
			if (getReaderOptions().isReadDefinition()) {
				view.setDefinition(definition.toString());
			}
			if (getReaderOptions().isReadStatement()) {
				view.setStatement(definition.toString());
			}
		});
		return result;
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new InformixColumnReader(getDialect());
	}
}

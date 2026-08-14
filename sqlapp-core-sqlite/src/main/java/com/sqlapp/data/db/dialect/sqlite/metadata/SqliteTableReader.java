/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableObjectReader;
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
		final List<Table> tables = super.doGetAll(connection, context,
				productVersionInfo);
		// SQLite JDBC exposes the backing indexes of UNIQUE constraints through
		// getTables() as well. They are indexes, not Schema model tables.
		tables.removeIf(table -> table.getName() != null
				&& table.getName().startsWith("sqlite_autoindex_"));
		return tables;
	}

	@Override
	protected IndexReader newIndexReader() {
		return new SqliteIndexReader(getDialect());
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
		}
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

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.TableObjectReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;

/** Reads table details one table at a time as required by UCanAccess metadata. */
public class MdbTableReader extends JdbcTableReader {

	public MdbTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> tables)
			throws SQLException {
		for (final Table table : tables) {
			table.setDialect(this.getDialect());
			loadTableObjects(connection, getColumnReader(), table);
			loadTableObjects(connection, getUniqueConstraintReader(), table);
			loadTableObjects(connection, getIndexReader(), table);
			loadTableObjects(connection, getForeignKeyConstraintReader(), table);
		}
	}

	private void loadTableObjects(final Connection connection,
			final TableObjectReader<?> reader, final Table table) {
		reader.setCatalogName(table.getCatalogName());
		reader.setSchemaName(table.getSchemaName());
		reader.setObjectName(table.getName());
		setTableObjects(connection, reader, table);
	}
}

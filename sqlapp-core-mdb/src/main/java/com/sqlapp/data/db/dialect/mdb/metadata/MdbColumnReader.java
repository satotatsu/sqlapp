/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;

import io.github.spannm.jackcess.Database;
import net.ucanaccess.jdbc.UcanaccessConnection;

/** Restores Access AutoNumber metadata exposed by UCanAccess. */
public class MdbColumnReader extends JdbcColumnReader {

	public MdbColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Column> columns = super.doGetAll(connection, context,
				productVersionInfo);
		try {
			if (!connection.isWrapperFor(UcanaccessConnection.class)) {
				return columns;
			}
			final UcanaccessConnection ucanaccessConnection = connection
					.unwrap(UcanaccessConnection.class);
			ucanaccessConnection.reloadDbIO();
			final Database database = ucanaccessConnection.getDbIO();
			for (final Column column : columns) {
				final io.github.spannm.jackcess.Table table = database
						.getTable(column.getTableName());
				if (table != null && table.getColumn(column.getName()) != null) {
					column.setIdentity(
							table.getColumn(column.getName()).isAutoNumber());
				}
			}
			return columns;
		} catch (final SQLException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected Column createColumn(final ExResultSet resultSet)
			throws SQLException {
		final Column column = super.createColumn(resultSet);
		final String autoIncrement = getString(resultSet, "IS_AUTOINCREMENT");
		final String typeName = getString(resultSet, "TYPE_NAME");
		if ("YES".equalsIgnoreCase(autoIncrement)
				|| "COUNTER".equalsIgnoreCase(typeName)
				|| "AUTOINCREMENT".equalsIgnoreCase(typeName)) {
			column.setIdentity(true);
		}
		return column;
	}
}

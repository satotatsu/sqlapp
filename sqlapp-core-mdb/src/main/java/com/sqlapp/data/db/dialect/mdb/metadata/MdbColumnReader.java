/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcColumnReader;
import com.sqlapp.data.db.dialect.mdb.util.MdbColumnSupport;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;

import io.github.spannm.jackcess.Database;

/** Restores Access AutoNumber metadata exposed by UCanAccess. */
public class MdbColumnReader extends JdbcColumnReader {

	public MdbColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		try {
			final Database database = MdbMetadataSupport.database(connection);
			final List<Column> columns = new ArrayList<>();
			MdbMetadataSupport.withDatabase(connection, database,
					() -> columns.addAll(super.doGetAll(connection, context, version)));
			return columns;
		} catch (final SQLException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void completeColumn(final Connection connection, final ExResultSet rs, final Column column)
			throws SQLException {
		try {
			final Database database = MdbMetadataSupport.database(connection);
			if (database == null) {
				return;
			}
			final io.github.spannm.jackcess.Table table = database.getTable(column.getTableName());
			final var source = table == null ? null : table.getColumn(column.getName());
			if (source != null) {
				// UCanAccess reports its HSQLDB mirror type; preserve the physical Access type.
				final DataType dataType = MdbColumnSupport.dataType(source.getType());
				if (dataType != DataType.OTHER && !source.getType().isTextual()) {
					column.setDataType(dataType);
				}
				MdbColumnSupport.enrich(column, source);
			}
		} catch (final IOException e) {
			throw new SQLException(
					"Cannot read Access column metadata for " + column.getTableName() + "." + column.getName(), e);
		}
	}

	@Override
	protected Column createColumn(final ExResultSet resultSet) throws SQLException {
		final Column column = super.createColumn(resultSet);
		final String autoIncrement = getString(resultSet, "IS_AUTOINCREMENT");
		final String typeName = getString(resultSet, "TYPE_NAME");
		if ("YES".equalsIgnoreCase(autoIncrement) || "COUNTER".equalsIgnoreCase(typeName)
				|| "AUTOINCREMENT".equalsIgnoreCase(typeName)) {
			column.setIdentity(true);
		}
		return column;
	}
}

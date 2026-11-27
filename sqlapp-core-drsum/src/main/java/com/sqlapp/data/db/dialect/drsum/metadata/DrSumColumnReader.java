/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.drsum.DrSum;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Reads Dr.Sum columns from {@code __all_tables__}. {@code __all_tables__} has
 * no ordinal column; columns are kept in the order the system table returns
 * them. Column default values are not exposed by the documented system tables
 * and are therefore not read.
 */
public class DrSumColumnReader extends ColumnReader {

	protected DrSumColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Column> result = list();
		execute(connection, getSqlNodeCache().getString("columns.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String tableName = getString(rs, "table_name");
				if (getReaderOptions().isExcludeSystemObjects()
						&& DrSumMetadataSupport.isSystemTable(tableName)) {
					return;
				}
				if (DrSumMetadataSupport.isEmpty(getString(rs, "column_name"))) {
					return;
				}
				result.add(createColumn(rs, tableName));
			}
		});
		return result;
	}

	protected Column createColumn(final ExResultSet rs, final String tableName) throws SQLException {
		final String name = getString(rs, "column_name");
		final Column column = createColumn(name);
		column.setTableName(tableName);
		final String type = getString(rs, "column_type");
		if (DrSumMetadataSupport.isEmpty(type)) {
			throw new IllegalStateException("Dr.Sum column has no column_type. column=" + tableName + "." + name);
		}
		Long precision = positive(rs.getLong("column_precision"), rs.wasNull());
		if (precision == null && "VARCHAR".equalsIgnoreCase(type.trim())) {
			// An unsized VARCHAR holds up to 65,535 characters; never narrow it.
			precision = (long) DrSum.MAX_VARCHAR_LENGTH;
		}
		final Integer scale = scale(rs.getInt("column_scale"), rs.wasNull());
		getDialect().setDbType(type, precision, scale, column);
		if (precision == null && isFractionalSecondType(column.getDataType())) {
			// No fractional second digits recorded: keep it unspecified instead of the
			// generic type default.
			column.setLength((Long) null);
		}
		column.setNotNull(DrSumMetadataSupport.flag(getString(rs, "column_null"), "column_null", tableName, name));
		column.setRemarks(getString(rs, "comments"));
		return column;
	}

	private static boolean isFractionalSecondType(final DataType type) {
		return type == DataType.TIME || type == DataType.TIMESTAMP || type == DataType.INTERVAL_DAY_TO_SECOND;
	}

	private static Long positive(final long value, final boolean wasNull) {
		return wasNull || value <= 0 ? null : value;
	}

	private static Integer scale(final int value, final boolean wasNull) {
		return wasNull || value < 0 ? null : value;
	}
}

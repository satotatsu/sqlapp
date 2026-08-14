/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** SAP HANA view-column reader backed by {@code VIEW_COLUMNS}. */
public class SapHanaViewColumnReader extends ColumnReader {

	protected SapHanaViewColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Column> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final SqlNode node = getSqlNodeCache().getString("viewColumns.sql");
		final List<Column> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs)
					throws SQLException {
				result.add(createColumn(rs));
			}
		});
		return result;
	}

	protected Column createColumn(final ExResultSet rs) throws SQLException {
		final Column column = new Column(getString(rs, COLUMN_NAME));
		column.setSchemaName(getString(rs, SCHEMA_NAME));
		column.setTableName(getString(rs, "VIEW_NAME"));
		column.setNullable("TRUE".equalsIgnoreCase(
				getString(rs, "IS_NULLABLE")));
		getDialect().setDbType(getString(rs, "DATA_TYPE_NAME"),
				rs.getLong("LENGTH"), getInteger(rs, "SCALE"), column);
		column.setRemarks(getString(rs, "COMMENTS"));
		return column;
	}
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * SQL Server 2017+ temporal history retention metadata, read in the table
 * query.
 */
public class SqlServer2017TableReader extends SqlServer2016TableReader {
	protected SqlServer2017TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("tables2017.sql");
	}

	@Override
	protected Table createTable(ExResultSet rs) throws SQLException {
		Table table = super.createTable(rs);
		if (table.getSystemVersioning() != null && table.getSystemVersioning().isEnable()) {
			String unit = getString(rs, "history_retention_period_unit_desc");
			Object duration = rs.getObject("history_retention_period");
			if (unit == null || duration == null)
				throw new SQLException("Missing SQL Server temporal history retention metadata for " + table.getName());
			try {
				SqlServerTemporalOptions.historyRetentionPeriod(table.getSystemVersioning(),
						"INFINITE".equalsIgnoreCase(unit) ? "INFINITE" : duration + " " + unit);
			} catch (IllegalArgumentException e) {
				throw new SQLException("Unsupported SQL Server temporal history retention for " + table.getName(), e);
			}
		}
		return table;
	}
}

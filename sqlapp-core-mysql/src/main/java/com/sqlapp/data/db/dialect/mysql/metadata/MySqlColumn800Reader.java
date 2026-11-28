/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSpatialReference;
import com.sqlapp.jdbc.ExResultSet;

/**
 * MySQL 8.0 column metadata reader.
 */
public class MySqlColumn800Reader extends MySqlColumn570Reader {

	protected MySqlColumn800Reader(Dialect dialect) {
		super(dialect);
	}

	/**
	 * From 8.0.3 information_schema.TABLES.AUTO_INCREMENT comes from cached
	 * statistics (information_schema_stats_expiry, one day by default), so a
	 * counter read from it can be far behind. Read the columns with the cache
	 * disabled for this session and restore the previous setting afterwards.
	 */
	@Override
	protected java.util.List<Column> doGetAll(Connection connection,
			com.sqlapp.data.parameter.ParametersContext context, ProductVersionInfo productVersionInfo) {
		if (connection == null || productVersionInfo == null || !"MySQL".equalsIgnoreCase(getDialect().getProductName())
				|| !productVersionInfo.gte(8, 0, 3))
			return super.doGetAll(connection, context, productVersionInfo);
		Long previous;
		try (var statement = connection.createStatement();
				var rs = statement.executeQuery("SELECT @@SESSION.information_schema_stats_expiry")) {
			previous = rs.next() ? rs.getLong(1) : null;
			if (previous != null && previous != 0)
				statement.execute("SET SESSION information_schema_stats_expiry = 0");
		} catch (SQLException e) {
			// Without the variable the cached value is used, as before.
			return super.doGetAll(connection, context, productVersionInfo);
		}
		try {
			return super.doGetAll(connection, context, productVersionInfo);
		} finally {
			if (previous != null && previous != 0) {
				try (var statement = connection.createStatement()) {
					statement.execute("SET SESSION information_schema_stats_expiry = " + previous);
				} catch (SQLException e) {
					throw new IllegalStateException("Cannot restore information_schema_stats_expiry", e);
				}
			}
		}
	}

	@Override
	protected Column createColumn(Connection connection, ExResultSet rs, ProductVersionInfo productVersionInfo)
			throws SQLException {
		Column column = super.createColumn(connection, rs, productVersionInfo);
		if ("MySQL".equalsIgnoreCase(getDialect().getProductName()) && productVersionInfo.gte(8, 0, 3)) {
			String srid = rs.getString("SRS_ID");
			if (srid != null)
				column.getSpecifics().put(MySqlSpatialReference.SRID, MySqlSpatialReference.normalize(srid));
		}
		return column;
	}

	@Override
	protected Column createColumn(Connection connection, ExResultSet rs) throws SQLException {
		Column column = super.createColumn(connection, rs);
		String extra = getString(rs, "EXTRA");
		column.setHidden(extra != null && extra.toUpperCase(java.util.Locale.ROOT).contains("INVISIBLE"));
		if (extra != null && extra.toUpperCase(java.util.Locale.ROOT).contains("DEFAULT_GENERATED")) {
			String expression = getString(rs, "COLUMN_DEFAULT");
			if (expression != null && !(column.getDataType().isDateTime()
					&& expression.matches("(?i)current_timestamp(?:\\(\\d*\\))?"))) {
				column.setDefaultValue("(" + expression + ")");
			}
		}
		return column;
	}
}

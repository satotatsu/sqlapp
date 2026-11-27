/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TemporalPeriod;
import com.sqlapp.data.schemas.TemporalPeriodType;

class MariadbTable11_40Reader extends MariadbTable10_27Reader {

	MariadbTable11_40Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new MariadbColumn11_40Reader(getDialect());
	}

	@Override
	protected com.sqlapp.data.db.metadata.UniqueConstraintReader newUniqueConstraintReader() {
		return new MariadbUniqueConstraint11_40Reader(getDialect());
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Table> tableList)
			throws SQLException {
		super.setMetadataDetail(connection, context, tableList);
		readApplicationPeriods(connection, context, tableList);
		for (Table table : tableList) {
			Column start = null;
			Column end = null;
			for (Column column : table.getColumns()) {
				if (Boolean.TRUE.equals(column.getSpecifics().get("SYSTEM_TIME_PERIOD_START", Boolean.class))) {
					start = column;
				}
				if (Boolean.TRUE.equals(column.getSpecifics().get("SYSTEM_TIME_PERIOD_END", Boolean.class))) {
					end = column;
				}
			}
			if (start != null && end != null) {
				table.getTemporalPeriods()
						.add(new TemporalPeriod("SYSTEM_TIME").setPeriodType(TemporalPeriodType.SYSTEM_TIME)
								.setStartColumnName(start.getName()).setEndColumnName(end.getName()));
				table.toSystemVersioning().setImplicit(false);
			}
		}
	}

	protected void readApplicationPeriods(Connection connection, ParametersContext context, List<Table> tables) {
		if (tables.isEmpty()) {
			return;
		}
		var byIdentity = new java.util.HashMap<List<String>, Table>();
		for (Table table : tables) {
			byIdentity.put(java.util.Arrays.asList(table.getCatalogName(), table.getSchemaName(), table.getName()),
					table);
		}
		execute(connection, getSqlNodeCache().getString("periods.sql"), context,
				new com.sqlapp.jdbc.sql.ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(com.sqlapp.jdbc.ExResultSet rs) throws SQLException {
						Table table = byIdentity.get(java.util.Arrays.asList(getString(rs, "TABLE_CATALOG"),
								getString(rs, "TABLE_SCHEMA"), getString(rs, "TABLE_NAME")));
						if (table == null) {
							return;
						}
						String name = getString(rs, "PERIOD");
						String start = getString(rs, "START_COLUMN_NAME");
						String end = getString(rs, "END_COLUMN_NAME");
						if (name == null || name.isBlank() || start == null || start.isBlank() || end == null
								|| end.isBlank() || start.equalsIgnoreCase(end)) {
							throw new IllegalStateException(
									"Incomplete MariaDB application period metadata: " + table.getName());
						}
						table.getTemporalPeriods()
								.add(new TemporalPeriod(name).setPeriodType(TemporalPeriodType.APPLICATION_TIME)
										.setStartColumnName(start).setEndColumnName(end));
					}
				});
	}

}

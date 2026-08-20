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
	protected void setMetadataDetail(Connection connection, ParametersContext context,
			List<Table> tableList) throws SQLException {
		super.setMetadataDetail(connection, context, tableList);
		for (Table table : tableList) {
			Column start = null;
			Column end = null;
			for (Column column : table.getColumns()) {
				if (Boolean.TRUE.equals(column.getSpecifics().get(
						"SYSTEM_TIME_PERIOD_START", Boolean.class))) {
					start = column;
				}
				if (Boolean.TRUE.equals(column.getSpecifics().get(
						"SYSTEM_TIME_PERIOD_END", Boolean.class))) {
					end = column;
				}
			}
			if (start != null && end != null) {
				table.getTemporalPeriods().add(new TemporalPeriod("SYSTEM_TIME")
						.setPeriodType(TemporalPeriodType.SYSTEM_TIME)
						.setStartColumnName(start.getName())
						.setEndColumnName(end.getName()));
				table.toSystemVersioning().setImplicit(false);
			}
		}
	}
}

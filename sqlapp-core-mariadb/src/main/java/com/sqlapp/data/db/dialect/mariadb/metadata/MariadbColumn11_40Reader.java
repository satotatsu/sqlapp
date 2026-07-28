/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;

class MariadbColumn11_40Reader extends MariadbColumn10_27Reader {

	MariadbColumn11_40Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Column createColumn(Connection connection, ExResultSet rs) throws SQLException {
		Column column = super.createColumn(connection, rs);
		if ("YES".equalsIgnoreCase(getString(rs, "IS_SYSTEM_TIME_PERIOD_START"))) {
			column.getSpecifics().put("SYSTEM_TIME_PERIOD_START", true);
		}
		if ("YES".equalsIgnoreCase(getString(rs, "IS_SYSTEM_TIME_PERIOD_END"))) {
			column.getSpecifics().put("SYSTEM_TIME_PERIOD_END", true);
		}
		return column;
	}
}

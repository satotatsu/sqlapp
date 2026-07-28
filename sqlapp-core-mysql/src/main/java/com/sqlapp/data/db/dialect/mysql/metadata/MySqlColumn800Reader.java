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
import com.sqlapp.jdbc.ExResultSet;

/**
 * MySQL 8.0 column metadata reader.
 */
public class MySqlColumn800Reader extends MySqlColumn570Reader {

	protected MySqlColumn800Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Column createColumn(Connection connection, ExResultSet rs) throws SQLException {
		Column column = super.createColumn(connection, rs);
		String extra = getString(rs, "EXTRA");
		column.setHidden(extra != null && extra.toUpperCase().contains("INVISIBLE"));
		return column;
	}
}

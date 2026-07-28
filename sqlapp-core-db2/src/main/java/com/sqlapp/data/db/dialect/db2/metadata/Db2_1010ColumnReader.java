/*
 * Copyright (C) 2007-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;

/**
 * Db2 10.1 column reader with temporal-column attributes.
 */
public class Db2_1010ColumnReader extends Db2ColumnReader {

	protected Db2_1010ColumnReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Column createColumn(final ExResultSet rs) throws SQLException {
		final Column column = super.createColumn(rs);
		setSpecifics(rs, "ROWBEGIN", column);
		setSpecifics(rs, "ROWEND", column);
		setSpecifics(rs, "TRANSACTIONSTARTID", column);
		setSpecifics(rs, "HIDDEN", column);
		setSpecifics(rs, "GENERATED", column);
		return column;
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;

/**
 * MySQL 8.0 table metadata reader.
 */
public class MySqlTable800Reader extends MySqlTable570Reader {

	protected MySqlTable800Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new MySqlColumn800Reader(getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new MySqlCheckConstraintReader(getDialect());
	}
}

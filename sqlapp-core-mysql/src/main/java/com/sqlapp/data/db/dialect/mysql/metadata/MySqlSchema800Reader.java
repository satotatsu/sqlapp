/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

/**
 * MySQL 8.0 schema metadata reader.
 */
public class MySqlSchema800Reader extends MySqlSchema570Reader {

	public MySqlSchema800Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new MySqlTable800Reader(getDialect());
	}
}

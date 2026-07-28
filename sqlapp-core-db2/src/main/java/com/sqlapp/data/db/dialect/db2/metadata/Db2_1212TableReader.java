/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;

public class Db2_1212TableReader extends Db2_1110TableReader {

	protected Db2_1212TableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new Db2_1212ColumnReader(this.getDialect());
	}
}

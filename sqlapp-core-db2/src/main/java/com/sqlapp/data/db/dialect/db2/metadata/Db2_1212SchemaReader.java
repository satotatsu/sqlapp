/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

public class Db2_1212SchemaReader extends Db2_1110SchemaReader {

	protected Db2_1212SchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new Db2_1212TableReader(this.getDialect());
	}
}

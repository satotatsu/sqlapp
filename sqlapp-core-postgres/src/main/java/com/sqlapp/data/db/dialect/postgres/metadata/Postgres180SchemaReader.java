/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

public class Postgres180SchemaReader extends Postgres160SchemaReader {
	protected Postgres180SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new Postgres180TableReader(this.getDialect());
	}
}

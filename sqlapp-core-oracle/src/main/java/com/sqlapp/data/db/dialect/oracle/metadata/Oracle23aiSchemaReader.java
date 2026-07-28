/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

public class Oracle23aiSchemaReader extends Oracle12cSchemaReader {

	protected Oracle23aiSchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new Oracle23aiTableReader(getDialect());
	}
}

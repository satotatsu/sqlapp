/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 */
package com.sqlapp.data.db.dialect.firebird.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

/** Firebird 5.0 schema metadata reader. */
public class Firebird50SchemaReader extends Firebird30SchemaReader {

	protected Firebird50SchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new Firebird50TableReader(getDialect());
	}
}

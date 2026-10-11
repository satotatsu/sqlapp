/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 */
package com.sqlapp.data.db.dialect.firebird.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.IndexReader;

/** Firebird 5.0 table metadata reader. */
public class Firebird50TableReader extends Firebird30TableReader {

	protected Firebird50TableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected IndexReader newIndexReader() {
		return new Firebird50IndexReader(getDialect());
	}
}

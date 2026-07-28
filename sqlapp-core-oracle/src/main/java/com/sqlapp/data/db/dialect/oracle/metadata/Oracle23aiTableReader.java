/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.IndexReader;

public class Oracle23aiTableReader extends Oracle12cTableReader {

	protected Oracle23aiTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new Oracle23aiColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new Oracle23aiIndexReader(getDialect());
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.CheckConstraintReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.IndexReader;

/** Informix JDBC table reader. */
public class InformixTableReader extends JdbcTableReader {
	public InformixTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new InformixColumnReader(getDialect());
	}

	@Override
	protected CheckConstraintReader newCheckConstraintReader() {
		return new InformixCheckConstraintReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new InformixIndexReader(getDialect());
	}
}

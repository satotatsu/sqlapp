/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.IndexReader;

/** Oracle 23ai materialized views with current column and index metadata. */
public class Oracle23aiMviewReader extends Oracle10gMviewReader {
	protected Oracle23aiMviewReader(Dialect dialect) {
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

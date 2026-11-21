/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;

/** SQL Server 2025 table metadata reader. */
public class SqlServer2025TableReader extends SqlServer2022TableReader {

	protected SqlServer2025TableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new SqlServer2025ColumnReader(getDialect());
	}

	@Override
	protected com.sqlapp.data.db.metadata.IndexReader newIndexReader() {
		return new SqlServer2025IndexReader(getDialect());
	}
}

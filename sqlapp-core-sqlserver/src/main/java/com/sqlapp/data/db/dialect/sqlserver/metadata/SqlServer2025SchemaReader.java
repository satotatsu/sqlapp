/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

/** SQL Server 2025 schema metadata reader. */
public class SqlServer2025SchemaReader extends SqlServer2022SchemaReader {

	public SqlServer2025SchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new SqlServer2025TableReader(getDialect());
	}
}

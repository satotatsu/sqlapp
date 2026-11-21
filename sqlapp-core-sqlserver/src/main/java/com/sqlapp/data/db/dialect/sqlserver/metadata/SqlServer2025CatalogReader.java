/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;

/** SQL Server 2025 catalog metadata reader. */
public class SqlServer2025CatalogReader extends SqlServer2022CatalogReader {

	public SqlServer2025CatalogReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new SqlServer2025SchemaReader(getDialect());
	}
}

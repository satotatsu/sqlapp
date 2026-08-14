/*
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;

/** SQL Server 2022 catalog metadata reader. */
public class SqlServer2022CatalogReader extends SqlServer2019CatalogReader {

	public SqlServer2022CatalogReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new SqlServer2022SchemaReader(getDialect());
	}
}

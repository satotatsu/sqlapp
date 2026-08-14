/*
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

/** SQL Server 2022 schema metadata reader. */
public class SqlServer2022SchemaReader extends SqlServer2019SchemaReader {

	public SqlServer2022SchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new SqlServer2022TableReader(getDialect());
	}
}

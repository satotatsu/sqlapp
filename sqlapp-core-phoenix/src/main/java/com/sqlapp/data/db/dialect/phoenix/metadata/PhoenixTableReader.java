/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;

public class PhoenixTableReader extends JdbcTableReader {
	public PhoenixTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected java.util.List<com.sqlapp.data.schemas.Table> doGetAll(java.sql.Connection connection,
			com.sqlapp.data.parameter.ParametersContext context, com.sqlapp.data.schemas.ProductVersionInfo version) {
		return PhoenixJdbcHeaders.read(connection,
				com.sqlapp.util.CommonUtils.coalesce(getCatalogName(context), getCatalogName()),
				com.sqlapp.util.CommonUtils.coalesce(getSchemaName(context), getSchemaName()),
				com.sqlapp.util.CommonUtils.coalesce(getObjectName(context), getObjectName()), false,
				getReaderOptions());
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new PhoenixColumnReader(getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new PhoenixPrimaryKeyReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new PhoenixIndexReader(getDialect());
	}
}

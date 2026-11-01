/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcViewReader;
import com.sqlapp.data.db.metadata.ColumnReader;

/** Retains the JDBC view path while using Phoenix-specific column metadata. */
public class PhoenixViewReader extends JdbcViewReader {
	public PhoenixViewReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected java.util.List<com.sqlapp.data.schemas.Table> doGetAll(java.sql.Connection connection,
			com.sqlapp.data.parameter.ParametersContext context, com.sqlapp.data.schemas.ProductVersionInfo version) {
		return PhoenixJdbcHeaders.read(connection,
				com.sqlapp.util.CommonUtils.coalesce(getCatalogName(context), getCatalogName()),
				com.sqlapp.util.CommonUtils.coalesce(getSchemaName(context), getSchemaName()),
				com.sqlapp.util.CommonUtils.coalesce(getObjectName(context), getObjectName()), true,
				getReaderOptions());
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new PhoenixColumnReader(getDialect());
	}
}

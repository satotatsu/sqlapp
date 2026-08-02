/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcCatalogReader;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** JDBC metadata reader for Microsoft Access databases. */
public class MdbCatalogReader extends JdbcCatalogReader {

	public MdbCatalogReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Catalog> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		return list(new Catalog(""));
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new MdbSchemaReader(this.getDialect());
	}

	@Override
	protected ObjectPrivilegeReader newObjectPrivilegeReader() {
		return null;
	}

	@Override
	protected ColumnPrivilegeReader newColumnPrivilegeReader() {
		return null;
	}
}

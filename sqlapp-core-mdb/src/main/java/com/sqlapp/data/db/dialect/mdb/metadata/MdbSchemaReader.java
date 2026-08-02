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
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcSchemaReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Schema;

/** Represents an Access database, which has no JDBC schema hierarchy, as one schema. */
public class MdbSchemaReader extends JdbcSchemaReader {

	public MdbSchemaReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Schema> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		return list(new Schema(""));
	}

	@Override
	protected TableReader newTableReader() {
		return new MdbTableReader(this.getDialect());
	}
}

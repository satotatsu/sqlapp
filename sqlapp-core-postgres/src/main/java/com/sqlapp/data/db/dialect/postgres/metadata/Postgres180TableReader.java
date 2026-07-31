/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.schemas.Table;

public class Postgres180TableReader extends Postgres130TableReader {
	protected Postgres180TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new Postgres180ColumnReader(this.getDialect());
	}

	@Override
	protected void setMetadataDetail(Connection connection,
			ParametersContext context, List<Table> list) throws SQLException {
		super.setMetadataDetail(connection, context, list);
		for (Table table : list) {
			Postgres180ColumnMetadata.moveNamedNotNullConstraints(table);
		}
	}
}

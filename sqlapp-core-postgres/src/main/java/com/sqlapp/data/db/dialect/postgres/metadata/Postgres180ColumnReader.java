/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

public class Postgres180ColumnReader extends Postgres100ColumnReader {
	protected Postgres180ColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("columns180.sql");
	}

	@Override
	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column column = super.createColumn(rs);
		Postgres180ColumnMetadata.applyNamedNotNull(column,
				getString(rs, "not_null_constraint_name"),
				rs.getBoolean("not_null_no_inherit"),
				rs.getBoolean("not_null_validated"));
		return column;
	}
}

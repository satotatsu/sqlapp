/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 */
package com.sqlapp.data.db.dialect.firebird.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads Firebird 5.0 indexes, including partial-index conditions. */
public class Firebird50IndexReader extends FirebirdIndexReader {

	public Firebird50IndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(final ProductVersionInfo productVersionInfo) {
		return getSqlNodeCache().getString("indexes50.sql");
	}
}

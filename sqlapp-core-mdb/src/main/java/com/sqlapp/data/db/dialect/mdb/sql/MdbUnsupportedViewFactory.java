/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.View;

/** Rejects view DDL because UCanAccess cannot persist Access saved queries. */
public class MdbUnsupportedViewFactory extends SimpleSqlFactory<View, MdbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final View view) {
		throw new UnsupportedOperationException(
				"UCanAccess 5.1.6 does not support CREATE or DROP VIEW for Access saved queries: "
						+ view.getName());
	}
}

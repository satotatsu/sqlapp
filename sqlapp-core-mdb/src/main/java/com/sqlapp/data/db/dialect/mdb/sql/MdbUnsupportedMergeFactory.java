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
import com.sqlapp.data.schemas.Table;

/** Rejects MERGE, which is not part of Access SQL. */
public class MdbUnsupportedMergeFactory
		extends SimpleSqlFactory<Table, MdbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		throw new UnsupportedOperationException(
				"Microsoft Access MERGE is not supported by UCanAccess 5.1.6");
	}
}

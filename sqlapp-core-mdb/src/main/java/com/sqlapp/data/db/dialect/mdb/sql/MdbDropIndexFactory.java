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
import com.sqlapp.data.schemas.Index;

/** Rejects index drop because UCanAccess cannot persist that Access DDL. */
public class MdbDropIndexFactory extends SimpleSqlFactory<Index, MdbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Index index) {
		throw new UnsupportedOperationException(
				"UCanAccess 5.1.6 does not support DROP INDEX for Access databases: "
						+ index.getName());
	}
}

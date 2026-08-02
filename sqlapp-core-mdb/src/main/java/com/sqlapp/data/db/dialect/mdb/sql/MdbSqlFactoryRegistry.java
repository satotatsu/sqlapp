/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/** SQL factory registry for Microsoft Access SQL. */
public class MdbSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public MdbSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE,
				MdbCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE,
				MdbTruncateTableFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE,
				MdbCreateIndexFactory.class);
	}
}

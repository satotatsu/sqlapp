/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;

/**
 * SQL factory registry for Db2 LUW 12.1.5.
 */
public class Db2_1215SqlFactoryRegistry extends Db2_1110SqlFactoryRegistry {

	public Db2_1215SqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Index.class, SqlType.CREATE, Db2_1215CreateIndexFactory.class);
	}
}

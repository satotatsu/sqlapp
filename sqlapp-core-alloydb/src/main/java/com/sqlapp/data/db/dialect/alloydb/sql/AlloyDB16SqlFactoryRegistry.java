/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres160SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;

public class AlloyDB16SqlFactoryRegistry extends Postgres160SqlFactoryRegistry {
	public AlloyDB16SqlFactoryRegistry(Dialect dialect) { super(dialect); }
	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Index.class, SqlType.CREATE, AlloyDBCreateIndexFactory.class);
	}
}

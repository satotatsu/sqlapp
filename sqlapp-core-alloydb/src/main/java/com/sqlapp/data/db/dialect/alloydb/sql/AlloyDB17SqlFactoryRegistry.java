/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres170SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;

public class AlloyDB17SqlFactoryRegistry extends Postgres170SqlFactoryRegistry {
	public AlloyDB17SqlFactoryRegistry(Dialect dialect) { super(dialect); }
	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Index.class, SqlType.CREATE, AlloyDBCreateIndexFactory.class);
	}
}

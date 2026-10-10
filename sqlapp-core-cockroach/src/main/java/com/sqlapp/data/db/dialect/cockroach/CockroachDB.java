/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach;

import com.sqlapp.data.db.dialect.postgres.Postgres110;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres110SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.dialect.cockroach.metadata.CockroachCatalogReader;
import com.sqlapp.data.db.dialect.cockroach.sql.*;

/** CockroachDB 24.3+; PostgreSQL wire compatibility does not imply catalog compatibility. */
public class CockroachDB extends Postgres110 {
	private static final long serialVersionUID = 1L;
	public CockroachDB() { super(() -> null); }
	@Override public String getProductName() { return "CockroachDB"; }
	@Override public String getSimpleName() { return "cockroach"; }
	@Override public CatalogReader getCatalogReader() { return new CockroachCatalogReader(this); }
	@Override public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres110SqlFactoryRegistry(this) {
			@Override protected void initializeAllSqls() {
				super.initializeAllSqls();
				registerSqlFactory(Index.class, SqlType.CREATE, CockroachCreateIndexFactory.class);
				registerSqlFactory(Table.class, SqlType.CREATE, CockroachCreateTableFactory.class);
				registerSqlFactory(Sequence.class, SqlType.CREATE, CockroachCreateSequenceFactory.class);
				registerSqlFactory(Table.class, SqlType.MERGE, CockroachMergeFactory.class);
			}
		};
	}
}

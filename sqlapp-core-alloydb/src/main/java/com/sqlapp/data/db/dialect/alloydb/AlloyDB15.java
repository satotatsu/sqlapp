/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb;

import com.sqlapp.data.db.dialect.postgres.Postgres150;

/** AlloyDB PostgreSQL 15 engine baseline. */
public class AlloyDB15 extends Postgres150 implements AlloyDB {
	private static final long serialVersionUID = 1L;

	/** Creates the PostgreSQL 15 compatible dialect. */
	public AlloyDB15() {
		super(() -> null);
	}

	@Override
	public String getProductName() {
		return "AlloyDB";
	}

	@Override
	public String getSimpleName() {
		return "alloydb";
	}
	@Override
	public com.sqlapp.data.db.metadata.CatalogReader getCatalogReader() {
		return new com.sqlapp.data.db.dialect.alloydb.metadata.AlloyDB15CatalogReader(this);
	}

	@Override
	public com.sqlapp.data.db.sql.SqlFactoryRegistry createSqlFactoryRegistry() {
		return new com.sqlapp.data.db.dialect.alloydb.sql.AlloyDB15SqlFactoryRegistry(this);
	}

}

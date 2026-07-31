package com.sqlapp.data.db.dialect.postgres;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres180CatalogReader;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres180SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

public class Postgres180 extends Postgres170 {
	private static final long serialVersionUID = 1L;

	protected Postgres180(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Postgres180CatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres180SqlFactoryRegistry(this);
	}
}

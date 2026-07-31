package com.sqlapp.data.db.dialect.postgres;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres160CatalogReader;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres170SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

public class Postgres170 extends Postgres160 {
	private static final long serialVersionUID = 1L;

	protected Postgres170(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Postgres160CatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres170SqlFactoryRegistry(this);
	}
}

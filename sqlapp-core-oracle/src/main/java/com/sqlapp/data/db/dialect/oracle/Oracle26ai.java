/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.oracle.metadata.Oracle26aiCatalogReader;
import com.sqlapp.data.db.dialect.oracle.sql.Oracle26aiSqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * Oracle AI Database 26ai dialect.
 */
public class Oracle26ai extends Oracle23ai {

	private static final long serialVersionUID = 1L;

	protected Oracle26ai(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Oracle26aiCatalogReader(this);
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Oracle26aiSqlFactoryRegistry(this);
	}
}

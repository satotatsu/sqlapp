/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mariadb.metadata.MariadbCatalog11_50Reader;
import com.sqlapp.data.db.metadata.CatalogReader;

/**
 * MariaDB 11.5 dialect.
 */
public class Mariadb11_50 extends Mariadb11_40 {

	private static final long serialVersionUID = 1L;

	protected Mariadb11_50(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new MariadbCatalog11_50Reader(this);
	}
}

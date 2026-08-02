/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mariadb.metadata.MariadbCatalog11_40Reader;
import com.sqlapp.data.db.metadata.CatalogReader;

/**
 * MariaDB 11.4 long-term release.
 */
public class Mariadb11_40 extends Mariadb10_50 {

	private static final long serialVersionUID = 1L;

	protected Mariadb11_40(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new MariadbCatalog11_40Reader(this);
	}
}

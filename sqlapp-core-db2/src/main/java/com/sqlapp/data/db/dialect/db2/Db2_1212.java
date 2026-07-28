/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.metadata.Db2_1212CatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;

/**
 * Db2 LUW 12.1.2 dialect.
 */
public class Db2_1212 extends Db2_1210 {

	private static final long serialVersionUID = 1L;

	public Db2_1212(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addVector();
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Db2_1212CatalogReader(this);
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.vertica.metadata.Vertica11_1_1CatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;

/**
 * Vertica 11.1.1.
 */
public class Vertica11_1_1 extends Vertica90 {

	private static final long serialVersionUID = 1L;

	protected Vertica11_1_1(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsValues() {
		return true;
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Vertica11_1_1CatalogReader(this);
	}
}

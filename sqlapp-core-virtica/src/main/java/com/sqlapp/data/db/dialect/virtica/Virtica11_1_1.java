/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.virtica.metadata.Virtica11_1_1CatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;

/**
 * Vertica 11.1.1.
 */
public class Virtica11_1_1 extends Virtica90 {

	private static final long serialVersionUID = 1L;

	protected Virtica11_1_1(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsValues() {
		return true;
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Virtica11_1_1CatalogReader(this);
	}
}

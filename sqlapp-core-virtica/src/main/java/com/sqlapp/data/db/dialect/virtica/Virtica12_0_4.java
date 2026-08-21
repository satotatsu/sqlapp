/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.virtica.metadata.Virtica12_0_4CatalogReader;
import com.sqlapp.data.db.metadata.CatalogReader;

/** Vertica 12.0.4. */
public class Virtica12_0_4 extends Virtica11_1_1 {

	private static final long serialVersionUID = 1L;

	protected Virtica12_0_4(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Virtica12_0_4CatalogReader(this);
	}
}

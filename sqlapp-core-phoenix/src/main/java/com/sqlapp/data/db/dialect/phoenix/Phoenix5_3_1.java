/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-phoenix.
 */
package com.sqlapp.data.db.dialect.phoenix;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/** Apache Phoenix 5.3.1. */
public class Phoenix5_3_1 extends Phoenix {

	private static final long serialVersionUID = 1L;

	protected Phoenix5_3_1(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsValues() {
		return true;
	}
}

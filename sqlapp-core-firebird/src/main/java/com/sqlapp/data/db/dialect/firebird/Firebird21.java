/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 */
package com.sqlapp.data.db.dialect.firebird;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Firebird 2.1 dialect.
 * <p>
 * 2.1 adds common table expressions (WITH), global temporary tables, MERGE and
 * UPDATE OR INSERT. Metadata reading and other SQL generation are shared with
 * {@link Firebird20}.
 * </p>
 */
public class Firebird21 extends Firebird20 {

	private static final long serialVersionUID = 1L;

	protected Firebird21(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsWith() {
		return true;
	}

	@Override
	public boolean equals(final Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		return true;
	}
}

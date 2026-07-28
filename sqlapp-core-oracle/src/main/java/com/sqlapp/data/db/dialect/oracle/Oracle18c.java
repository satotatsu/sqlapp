/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Oracle Database 18c dialect.
 */
public class Oracle18c extends Oracle12c {

	private static final long serialVersionUID = 1L;

	protected Oracle18c(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Oracle Database 19c dialect.
 */
public class Oracle19c extends Oracle18c {

	private static final long serialVersionUID = 1L;

	protected Oracle19c(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}

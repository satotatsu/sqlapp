/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Db2 LUW 11.5 dialect.
 */
public class Db2_1150 extends Db2_1110 {

	private static final long serialVersionUID = 1L;

	public Db2_1150(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}

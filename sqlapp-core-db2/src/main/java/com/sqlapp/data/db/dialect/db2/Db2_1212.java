/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-db2.
 */
package com.sqlapp.data.db.dialect.db2;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Db2 LUW 12.1.2 dialect.
 */
public class Db2_1212 extends Db2_1210 {

	private static final long serialVersionUID = 1L;

	public Db2_1212(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}

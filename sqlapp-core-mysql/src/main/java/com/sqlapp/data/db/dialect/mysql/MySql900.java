/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 */
package com.sqlapp.data.db.dialect.mysql;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * MySQL 9.0 and later innovation releases.
 */
public class MySql900 extends MySql840 {

	private static final long serialVersionUID = 1L;

	protected MySql900(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}

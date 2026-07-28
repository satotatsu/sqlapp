/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * MariaDB 12.1 and later releases.
 */
public class Mariadb12_10 extends Mariadb11_80 {

	private static final long serialVersionUID = 1L;

	protected Mariadb12_10(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}

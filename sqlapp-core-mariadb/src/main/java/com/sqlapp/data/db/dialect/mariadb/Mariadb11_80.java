/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * MariaDB 11.8 long-term release.
 */
public class Mariadb11_80 extends Mariadb11_50 {

	private static final long serialVersionUID = 1L;

	protected Mariadb11_80(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}

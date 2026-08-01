/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * MariaDB 10.5 dialect.
 */
public class Mariadb10_50 extends Mariadb10_30 {

	private static final long serialVersionUID = 1L;

	protected Mariadb10_50(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsInsertReturningResultSet() {
		return true;
	}

	@Override
	public boolean supportsValues() {
		return true;
	}

	@Override
	public String handleInsertReturningSql(final Table table, final Column identityColumn, final String sql) {
		return sql + "\nRETURNING " + getObjectFullName(identityColumn.getName());
	}
}

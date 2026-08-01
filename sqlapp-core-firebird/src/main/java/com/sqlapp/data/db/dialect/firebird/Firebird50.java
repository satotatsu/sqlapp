/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 */
package com.sqlapp.data.db.dialect.firebird;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/** Firebird 5.0 dialect. */
public class Firebird50 extends Firebird30 {

	private static final long serialVersionUID = 1L;

	protected Firebird50(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsInsertReturningResultSet() {
		return true;
	}

	@Override
	public String handleInsertReturningSql(final Table table, final Column identityColumn, final String sql) {
		return sql + "\nRETURNING " + getObjectFullName(identityColumn.getName());
	}
}

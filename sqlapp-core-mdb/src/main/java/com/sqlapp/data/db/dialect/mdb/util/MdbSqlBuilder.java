/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.util;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;

/** SQL builder for Microsoft Access SQL. */
public class MdbSqlBuilder extends AbstractSqlBuilder<MdbSqlBuilder> {

	private static final long serialVersionUID = 1L;

	public MdbSqlBuilder(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected MdbSqlBuilder autoIncrement(final AbstractColumn<?> column) {
		return this;
	}

	@Override
	protected MdbSqlBuilder typeDefinition(final Column column) {
		if (column.isIdentity()) {
			return _add(getDialect().getIdentityColumnString());
		}
		return super.typeDefinition(column);
	}

	@Override
	public MdbSqlBuilder clone() {
		return (MdbSqlBuilder) super.clone();
	}
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Table;

/** Keeps explicit DML table identities independent of SQLite's search order. */
final class SqliteDmlSqlBuilder extends SqliteSqlBuilder {
	private static final long serialVersionUID = 1L;

	SqliteDmlSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	@Override
	public SqliteSqlBuilder name(AbstractSchemaObject<?> object, boolean withSchemaName) {
		return super.name(object, withSchemaName || object instanceof Table && object.getSchemaName() != null);
	}
}

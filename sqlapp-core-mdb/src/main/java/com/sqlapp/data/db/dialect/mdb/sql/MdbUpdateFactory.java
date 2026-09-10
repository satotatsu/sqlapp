/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractUpdateFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/** Access UPDATE using ordinal Row expressions. */
public class MdbUpdateFactory extends AbstractUpdateFactory<MdbSqlBuilder> {
	@Override
	protected SqlType getSqlType() {
		return SqlType.UPDATE;
	}

	@Override
	protected void addKeyColumnsCondition(final Table table,
			final SqlSignature signature, final MdbSqlBuilder builder) {
		super.addKeyColumnsCondition(table, signature, null, builder);
		addLockVersionColumnCondition(table, builder);
	}

	@Override
	protected String getColumnParameterExpression(final Column column,
			final String defaultValue) {
		return MdbParameterExpression.of(column, defaultValue);
	}
}

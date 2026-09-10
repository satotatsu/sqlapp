/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDeleteTableFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/** Access DELETE using ordinal Row expressions. */
public class MdbDeleteFactory extends AbstractDeleteTableFactory<MdbSqlBuilder> {
	@Override
	protected SqlType getSqlType() {
		return SqlType.DELETE;
	}

	@Override
	protected boolean addWhereBeforeConditions() {
		return false;
	}

	@Override
	protected void addDeleteConditionColumns(final Table table,
			final SqlSignature signature, final MdbSqlBuilder builder) {
		addKeyColumnsCondition(table, signature, builder);
		addLockVersionColumnCondition(table, builder);
	}

	@Override
	protected String getColumnParameterExpression(final Column column,
			final String defaultValue) {
		return MdbParameterExpression.of(column, defaultValue);
	}
}

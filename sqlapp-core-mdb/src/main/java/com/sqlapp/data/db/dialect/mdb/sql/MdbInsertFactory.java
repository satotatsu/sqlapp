/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractInsertFactory;
import com.sqlapp.data.schemas.Column;

/** Access INSERT using ordinal Row expressions for arbitrary column names. */
public class MdbInsertFactory extends AbstractInsertFactory<MdbSqlBuilder> {
	@Override
	protected String getColumnParameterExpression(final Column column,
			final String defaultValue) {
		return MdbParameterExpression.of(column, defaultValue);
	}
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSelectFactory;
import com.sqlapp.data.schemas.Column;

/** Access key SELECT using ordinal Row expressions. */
public class MdbSelectFactory extends AbstractSelectFactory<MdbSqlBuilder> {
	@Override
	protected String getColumnParameterExpression(final Column column,
			final String defaultValue) {
		return MdbParameterExpression.of(column, defaultValue);
	}
}

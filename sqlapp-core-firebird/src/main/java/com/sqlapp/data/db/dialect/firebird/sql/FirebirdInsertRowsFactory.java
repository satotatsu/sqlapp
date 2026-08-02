/*
 * Copyright (C) 2007-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.sql.InsertRowsFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Column;

/** Generates typed parameters for Firebird's INSERT ... SELECT rows form. */
public class FirebirdInsertRowsFactory extends InsertRowsFactory {

	@Override
	protected String getValueDefinitionForInsert(final Column column, final SqlSignature sqlSignature) {
		final String expression = super.getValueDefinitionForInsert(column, sqlSignature);
		if (expression == null) {
			return null;
		}
		return "CAST(" + expression + " AS TYPE OF COLUMN " + column.getTable().getName() + "."
				+ getQuoteName(column) + ")";
	}
}

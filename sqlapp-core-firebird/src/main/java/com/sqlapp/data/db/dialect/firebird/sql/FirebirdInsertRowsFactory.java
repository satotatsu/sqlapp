/*
 * Copyright (C) 2007-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-firebird.
 */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.InsertRowsFactory;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Domain;

/**
 * Generates typed parameters for Firebird's INSERT ... SELECT rows form.
 * <p>
 * A parameter in a SELECT list has no type, so each value is cast. Firebird
 * 2.5+ casts to {@code TYPE OF COLUMN "table"."column"}; older versions cast to
 * the column's declared type, or the underlying type of its domain.
 * </p>
 */
public class FirebirdInsertRowsFactory extends InsertRowsFactory {

	@Override
	protected String getValueDefinitionForInsert(final Column column, final SqlSignature sqlSignature) {
		final String expression = super.getValueDefinitionForInsert(column, sqlSignature);
		if (expression == null) {
			return null;
		}
		return "CAST(" + expression + " AS " + castType(column) + ")";
	}

	private String castType(final Column column) {
		if (getDialect().compareTo(DialectHolder.defaultDialect25) >= 0) {
			// Same identifier rules as the INSERT target and column list.
			return "TYPE OF COLUMN " + createSqlBuilder(getDialect()).name(column.getTable(), false) + "."
					+ getQuoteName(column);
		}
		if (column.getArrayDimension() > 0) {
			throw new IllegalArgumentException("Firebird before 2.5 cannot cast a parameter to the array column "
					+ column.getName() + "; insert array columns without INSERT_ROWS");
		}
		final FirebirdSqlBuilder builder = new FirebirdSqlBuilder(getDialect());
		final Domain domain = column.getDomain();
		final String characterSet;
		if (domain != null && domain.getDataType() != null) {
			builder.typeDefinition(domain);
			characterSet = domain.getCharacterSet();
		} else if (domain != null) {
			// only the domain name is known: use the type declared on the column
			builder.columnType(new Column(column.getName()).setDataType(column.getDataType())
					.setDataTypeName(column.getDataTypeName()).setLength(column.getLength())
					.setScale(column.getScale()));
			characterSet = column.getCharacterSet();
		} else {
			builder.columnType(column);
			characterSet = column.getCharacterSet();
		}
		if (characterSet != null) {
			builder._add(" CHARACTER SET ")._add(characterSet);
		}
		return builder.toString();
	}
}

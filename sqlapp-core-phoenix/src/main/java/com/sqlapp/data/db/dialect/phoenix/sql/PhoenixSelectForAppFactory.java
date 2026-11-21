/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.SelectForAppFactory;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Native LIMIT/OFFSET paging, evaluated only for non-count queries. */
public class PhoenixSelectForAppFactory extends SelectForAppFactory {
	@Override
	protected void addOffsetRowsOnly(Table table, AbstractSqlBuilder<?> builder) {
		final String row = ParameterDefinition.ROW_KEY_PARANETER_NAME;
		final String offset = ParameterDefinition.OFFSET_KEY_PARANETER_NAME;
		final String count = ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME;
		final String validator = PhoenixSelectForAppFactory.class.getName();
		final String prefix = "import " + validator + "; !" + count + " && ";
		builder.lineBreak()._add(toIfExpression(prefix + toIsNotEmptyExpression(row)
				+ " && PhoenixSelectForAppFactory.validatePage(" + row + ", false, true)"));
		builder.lineBreak()._add("LIMIT /*" + row + "*/1");
		builder.lineBreak()._add(getEndIfExpression());
		builder.lineBreak()._add(
				toIfExpression(prefix + toIsNotEmptyExpression(offset) + " && PhoenixSelectForAppFactory.validatePage("
						+ offset + ", true, " + getDialect().supportsLimitOffset() + ")"));
		builder.lineBreak()._add("OFFSET /*" + offset + "*/0");
		builder.lineBreak()._add(getEndIfExpression());
	}

	/**
	 * Validates values at template evaluation, including legacy OFFSET requests.
	 */
	public static boolean validatePage(Object value, boolean offset, boolean supported) {
		final long number;
		try {
			number = new java.math.BigDecimal(value.toString()).longValueExact();
		} catch (NumberFormatException | ArithmeticException e) {
			throw new IllegalArgumentException("Phoenix paging requires an integer row count or offset", e);
		}
		if (number < 0)
			throw new IllegalArgumentException("Phoenix paging requires a nonnegative row count or offset");
		if (offset && !supported) {
			if (number == 0)
				return false;
			throw new UnsupportedOperationException("Phoenix OFFSET requires version 4.8 or later");
		}
		return true;
	}
}

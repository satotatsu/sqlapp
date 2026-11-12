/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.regex.Pattern;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Table;

/** Validation shared by GoogleSQL TTL creation and migration. */
final class SpannerRowDeletionPolicy {
	private static final Pattern EXPRESSION = Pattern.compile(
			"\\s*OLDER_THAN\\s*\\(\\s*(`[^`\\r\\n]+`|[A-Za-z_][A-Za-z_0-9]*)\\s*,\\s*INTERVAL\\s+([0-9]+)\\s+DAY\\s*\\)\\s*",
			Pattern.CASE_INSENSITIVE);

	private SpannerRowDeletionPolicy() {
	}

	static String expression(final Table table) {
		return expression(table, table.getSpecifics().get(SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION));
	}

	static String expression(final Table table, final String value) {
		if (value == null)
			return null;
		final var matcher = EXPRESSION.matcher(value);
		if (!matcher.matches())
			throw new IllegalArgumentException("Spanner TTL requires OLDER_THAN(timestamp_column, INTERVAL nonnegative_days DAY)");
		final String identifier = matcher.group(1);
		final String name = identifier.startsWith("`") ? identifier.substring(1, identifier.length() - 1) : identifier;
		final var column = table.getColumns().get(name);
		if (column == null || column.getDataType() != DataType.TIMESTAMP || column.getArrayDimension() != 0)
			throw new IllegalArgumentException("Spanner TTL requires an existing scalar TIMESTAMP column: " + name);
		if (!name.equals(column.getName()))
			throw new IllegalArgumentException("Spanner TTL must use the column's original casing: " + column.getName());
		return value.trim();
	}
}

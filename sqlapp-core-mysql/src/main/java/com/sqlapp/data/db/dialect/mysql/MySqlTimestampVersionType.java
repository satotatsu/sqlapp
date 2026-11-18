/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.TimestampType;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;

/** Native MySQL TIMESTAMP with fractional seconds, distinct from DATETIME. */
final class MySqlTimestampVersionType extends TimestampType {
	private static final long serialVersionUID = 1L;

	MySqlTimestampVersionType() {
		super("TIMESTAMP");
		setDataType(DataType.TIMESTAMPVERSION);
		setTypeName("TIMESTAMP");
		clearColumnTypeMatchers();
		addColumnTypeMatcher(new RegexColumnTypeMatcher(
				"TIMESTAMP\\s*(?:\\(\\s*(?<precision>[0-6])\\s*\\))?", (matcher, info) -> {
					final String precision = matcher.group("precision");
					info.setLength(precision == null ? "0" : precision);
				}));
		setCreateFormat("TIMESTAMP({p})").setLiteral("'", "'").setDefaultPrecision(0);
	}
}

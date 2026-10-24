/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.Locale;
import com.sqlapp.data.schemas.Table;

/** Native table options shared by CREATE and ALTER. */
final class MySqlTableOptions {
	private MySqlTableOptions() { }

	static String token(String name, String value) {
		if (value == null || !value.matches("[A-Za-z0-9_]+")) {
			throw new IllegalArgumentException("Specify an explicit MySQL/MariaDB " + name + " name: " + value);
		}
		return value;
	}

	static String rowFormat(Table table) {
		String value = table.getSpecifics().get("ROW_FORMAT");
		if (value != null) {
			value = token("ROW_FORMAT", value).toUpperCase(Locale.ROOT);
			if (table.isCompression() && !"COMPRESSED".equals(value)) {
				throw new IllegalArgumentException("compression=true conflicts with ROW_FORMAT=" + value);
			}
			return value;
		}
		return table.isCompression() ? "COMPRESSED" : null;
	}
}

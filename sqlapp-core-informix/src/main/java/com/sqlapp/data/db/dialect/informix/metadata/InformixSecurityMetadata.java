/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.SQLException;
import com.sqlapp.jdbc.ExResultSet;

final class InformixSecurityMetadata {
	private InformixSecurityMetadata() {
	}

	record Privilege(String name, boolean grantable) {
	}

	static java.util.List<Privilege> privileges(String pattern, boolean column) {
		String codes = column ? "sur" : "su*idxarn";
		String[] names = column ? new String[] { "SELECT", "UPDATE", "REFERENCES" }
				: new String[] { "SELECT", "UPDATE", null, "INSERT", "DELETE", "INDEX", "ALTER", "REFERENCES",
						"UNDER" };
		if (pattern == null || (column ? pattern.length() != 3 : pattern.length() != 8 && pattern.length() != 9))
			throw new IllegalStateException("Invalid Informix privilege pattern length");
		var result = new java.util.ArrayList<Privilege>();
		for (int i = 0; i < pattern.length(); i++) {
			char ch = pattern.charAt(i);
			if (ch == '-')
				continue;
			if (Character.toLowerCase(ch) != codes.charAt(i))
				throw new IllegalStateException("Invalid Informix privilege code at position " + (i + 1));
			if (names[i] != null)
				result.add(new Privilege(names[i], Character.isUpperCase(ch)));
		}
		return result;
	}

	static String required(final ExResultSet rs, final String label) throws SQLException {
		final String value = rs.getString(label);
		if (value == null || value.isBlank())
			throw new IllegalStateException("Missing Informix security metadata: " + label);
		return value.stripTrailing();
	}
}

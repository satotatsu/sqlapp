/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Catalog privilege views omit row filters. SCRIPT is a bulk schema-only read.
 */
final class HsqlPrivilegeFilters {
	private HsqlPrivilegeFilters() {
	}

	static String scope(Connection connection) {
		try (var sql = connection.createStatement()) {
			try (var rs = sql
					.executeQuery("SELECT ADMIN FROM INFORMATION_SCHEMA.SYSTEM_USERS WHERE USER_NAME=CURRENT_USER")) {
				if (!rs.next() || !rs.getBoolean(1))
					return "UNKNOWN";
			}
			try (var rs = sql.executeQuery("SCRIPT")) {
				while (rs.next()) {
					String command = rs.getString(1);
					if (command.stripLeading().toUpperCase(Locale.ROOT).startsWith("GRANT ")) {
						// Ignore quoted identifiers/literals, including escaped quote characters.
						String tokens = command.replaceAll("\"(?:\"\"|[^\"])*\"|'(?:''|[^'])*'", " ");
						if (Pattern.compile("\\bFILTER\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(tokens).find())
							return "FILTERED";
					}
				}
			}
			return "UNFILTERED";
		} catch (SQLException failure) {
			throw new IllegalStateException("Cannot verify HSQLDB privilege row filters", failure);
		}
	}
}

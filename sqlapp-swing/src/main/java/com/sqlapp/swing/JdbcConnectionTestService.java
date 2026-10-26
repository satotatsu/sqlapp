/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.Arrays;

/**
 * Opens a short-lived read-only pool and checks product/dialect metadata only.
 */
final class JdbcConnectionTestService {
	String test(final PlanningJdbc.Spec spec, final char[] password) {
		try (var pool = PlanningJdbc.open(spec, password); var connection = pool.getConnection()) {
			final var dialect = PlanningJdbc.requireDialect(connection);
			final var metadata = connection.getMetaData();
			return redact(metadata.getDatabaseProductName() + " " + metadata.getDatabaseProductVersion() + " ("
					+ dialect.getClass().getSimpleName() + ")", spec, password);
		} catch (final Exception failure) {
			// Retain SQLState/vendor diagnostics without retaining an unredacted cause.
			throw new IllegalArgumentException(redact(EditorStatusPanel.failureDetails(failure), spec, password));
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	private static String redact(final String text, final PlanningJdbc.Spec spec, final char[] password) {
		String result = text.replace(spec.jdbcUrl(), "[JDBC URL]");
		if (password.length > 0)
			result = result.replace(new String(password), "[redacted]");
		return result;
	}
}

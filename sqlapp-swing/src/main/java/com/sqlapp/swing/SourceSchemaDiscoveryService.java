/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Lists minimal Schema metadata without reading tables or generating files. */
final class SourceSchemaDiscoveryService {
	List<String> list(PlanningJdbc.Spec source, char[] password) {
		try (var pool = PlanningJdbc.open(source, password); var connection = pool.getConnection()) {
			final var reader = PlanningJdbc.requireDialect(connection).getCatalogReader().getSchemaReader();
			final var names = reader.getAll(connection).stream().map(schema -> schema.getName()).toList();
			final var unique = new HashSet<String>();
			for (var name : names) {
				if (name == null || name.isBlank() || !name.equals(name.trim()) || !unique.add(name))
					throw new IllegalArgumentException(
							"Source schema names are not uniquely selectable. Choose a specific database/catalog in the JDBC connection or use a saved migration job with resolved identities.");
			}
			if (names.isEmpty())
				throw new IllegalArgumentException(
						"No source schemas are visible. Check the selected database and metadata permissions.");
			return names.stream().sorted().toList();
		} catch (Exception failure) {
			String message = EditorStatusPanel.failureDetails(failure).replace(source.jdbcUrl(), "[JDBC URL]");
			if (password.length > 0)
				message = message.replace(new String(password), "[redacted]");
			throw new IllegalArgumentException(message);
		} finally {
			Arrays.fill(password, '\0');
		}
	}
}

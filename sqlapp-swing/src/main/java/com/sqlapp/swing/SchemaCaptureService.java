/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;

/**
 * Captures one canonical Schema with full metadata and without a row iterator.
 */
final class SchemaCaptureService {
	Path capture(PlanningJdbc.Spec source, char[] password, String requestedSchema, Path parent) {
		try {
			if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent))
				throw new IllegalArgumentException("Choose an existing writable folder for the Schema XML.");
			try (var pool = PlanningJdbc.open(source, password); var connection = pool.getConnection()) {
				final var reader = PlanningJdbc.requireDialect(connection).getCatalogReader().getSchemaReader();
				String name = requestedSchema == null ? "" : requestedSchema.trim();
				if (name.isEmpty()) {
					try {
						name = reader.getCurrentSchemaName(connection);
					} catch (java.sql.SQLFeatureNotSupportedException unavailable) {
						name = null;
					}
				}
				if (name != null && !name.isBlank())
					reader.setSchemaName(name);
				final String selected = name;
				final var choices = reader.getAll(connection).stream()
						.filter(schema -> selected == null || selected.isBlank() || schema.getName().equals(selected))
						.toList();
				if (choices.size() != 1)
					throw new IllegalArgumentException(
							"Schema could not be resolved uniquely. Choose its exact name and try again.");
				final var schema = choices.getFirst();
				reader.setSchemaName(schema.getName());
				reader.setCatalogName(schema.getCatalogName());
				reader.loadFull(connection, schema);
				final Path output = Files.createTempDirectory(parent.toAbsolutePath().normalize(), "schema-")
						.resolve("schema.xml");
				AtomicMigrationFile.write(output, temporary -> {
					try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
						schema.writeXml(writer);
					} catch (javax.xml.stream.XMLStreamException failure) {
						throw new java.io.IOException("Unable to write Schema XML.", failure);
					}
				});
				return output;
			}
		} catch (Exception failure) {
			String message = EditorStatusPanel.failureDetails(failure).replace(source.jdbcUrl(), "[JDBC URL]");
			if (password.length > 0)
				message = message.replace(new String(password), "[redacted]");
			throw new IllegalArgumentException("Unable to capture Schema metadata: " + message);
		} finally {
			Arrays.fill(password, '\0');
		}
	}
}

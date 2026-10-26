/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.*;
import java.util.Arrays;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobConfigurationEditorIO;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

/** Read-only source discovery and durable artifacts for the common GUI path. */
final class DataMigrationDraftService {
	record Prepared(Path directory, BulkMigrationJobDraftBuilder.SchemaSnapshot schema) {
	}

	Prepared discover(final PlanningJdbc.Spec source, final char[] password, final String requestedSchema,
			final Path parent) {
		try {
			if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent))
				throw new IllegalArgumentException("Choose a writable folder for the migration settings.");
			try (var pool = PlanningJdbc.open(source, password); var connection = pool.getConnection()) {
				final var dialect = PlanningJdbc.requireDialect(connection);
				final var reader = dialect.getCatalogReader().getSchemaReader();
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
				final String selectedName = name;
				final var choices = reader.getAll(connection).stream().filter(schema -> selectedName == null
						|| selectedName.isBlank() || schema.getName().equals(selectedName)).toList();
				if (choices.size() != 1)
					throw new IllegalArgumentException(
							"Source schema could not be resolved uniquely. Enter its exact name in Advanced options, then try New migration again.");
				final Schema schema = choices.getFirst();
				final var tables = reader.getTableReader();
				tables.setCatalogName(schema.getCatalogName());
				tables.setSchemaName(schema.getName());
				tables.loadFull(connection, schema);
				SchemaUtils.validate(schema);
				final Path folder = Files.createTempDirectory(parent.toAbsolutePath().normalize(), "migration-");
				final Path xml = folder.resolve("source-schema.xml");
				try (var writer = Files.newBufferedWriter(xml, java.nio.charset.StandardCharsets.UTF_8,
						StandardOpenOption.CREATE_NEW)) {
					schema.writeXml(writer);
				}
				return new Prepared(folder, new BulkMigrationJobDraftBuilder().inspect(xml));
			}
		} catch (final Exception failure) {
			String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
			if (password.length > 0)
				message = message.replace(new String(password), "[redacted]");
			message = message.replace(source.jdbcUrl(), "[JDBC URL]");
			throw new IllegalArgumentException("Unable to read source tables: " + message);
		} finally {
			Arrays.fill(password, '\0');
		}
	}

	static BulkMigrationJobConfigurationEditorIO.Snapshot save(final Path folder, final String yaml) throws Exception {
		return new BulkMigrationJobConfigurationEditorIO().write(folder.resolve("migration.yaml"), yaml, null);
	}
}

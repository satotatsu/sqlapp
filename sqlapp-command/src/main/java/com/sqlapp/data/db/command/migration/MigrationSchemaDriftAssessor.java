/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.SchemaCompatibility;
import com.sqlapp.data.schemas.migration.SchemaCompatibilityAnalyzer;
import com.sqlapp.data.schemas.migration.SchemaCompatibilityChange;
import com.sqlapp.data.schemas.migration.SchemaCompatibilityReport;

/**
 * Reads only migration tables from the live target and classifies structural
 * drift.
 */
public final class MigrationSchemaDriftAssessor {

	private MigrationSchemaDriftAssessor() {
	}

	public static SchemaCompatibilityReport assess(final Connection connection, final Collection<Table> expectedTables)
			throws SQLException {
		Objects.requireNonNull(connection, "connection");
		Objects.requireNonNull(expectedTables, "expectedTables");
		final TableReader reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader()
				.getSchemaReader().getTableReader();
		final List<SchemaCompatibilityChange> changes = new ArrayList<>();
		SchemaCompatibility compatibility = SchemaCompatibility.COMPATIBLE;
		synchronized (reader) {
			final String oldCatalog = reader.getCatalogName();
			final String oldSchema = reader.getSchemaName();
			final String oldObject = reader.getObjectName();
			try {
				for (final Table expectedTable : expectedTables) {
					reader.setCatalogName(expectedTable.getCatalogName());
					reader.setSchemaName(expectedTable.getSchemaName());
					reader.setObjectName(expectedTable.getName());
					final List<Table> actualTables = reader.getAllFull(connection);
					final Schema expected = new Schema(expectedTable.getSchemaName());
					expected.getTables().add(expectedTable.clone());
					final Schema actual = new Schema(expectedTable.getSchemaName());
					actual.getTables().addAll(actualTables);
					final SchemaCompatibilityReport report = SchemaCompatibilityAnalyzer.compare(expected, actual);
					compatibility = compatibility.merge(report.compatibility());
					changes.addAll(report.changes());
				}
			} finally {
				reader.setCatalogName(oldCatalog);
				reader.setSchemaName(oldSchema);
				reader.setObjectName(oldObject);
			}
		}
		return new SchemaCompatibilityReport(compatibility, changes);
	}
}

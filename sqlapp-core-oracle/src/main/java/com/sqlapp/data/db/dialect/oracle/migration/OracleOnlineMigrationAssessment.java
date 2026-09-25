/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Evidence;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Finding;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.ObjectId;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Severity;

/** Read-only Oracle catalog and optional character-data scanner. */
final class OracleOnlineMigrationAssessment {
	private static final String REFERENCE = "https://docs.oracle.com/en/database/oracle/dmu/23.1/dumag/ch1_overview.html";

	private OracleOnlineMigrationAssessment() { }

	static MigrationAssessment assess(final Connection connection, final List<Schema> schemas,
			final MigrationAssessment offline, final boolean scanCharacterData) throws SQLException {
		final String product = connection.getMetaData().getDatabaseProductName();
		if (product == null || !product.toLowerCase(Locale.ROOT).contains("oracle")) {
			throw new IllegalArgumentException("Online Oracle assessment requires an Oracle JDBC connection; found " + product);
		}
		final Map<String, String> nls = readNls(connection);
		final String sourceCharacterSet = nls.get("NLS_CHARACTERSET");
		if (sourceCharacterSet == null || sourceCharacterSet.isBlank()) {
			throw new SQLException("NLS_DATABASE_PARAMETERS did not return NLS_CHARACTERSET");
		}
		final var findings = new ArrayList<>(offline.findings());
		findings.add(new Finding("oracle.charset.database-settings", Severity.REVIEW, Evidence.DATABASE, null,
				"Connected source reports NLS_CHARACTERSET=" + sourceCharacterSet + "; NLS_NCHAR_CHARACTERSET="
						+ nls.getOrDefault("NLS_NCHAR_CHARACTERSET", "UNKNOWN") + ".",
				"Preserve these captured values with the assessment and compare them with the target before import.", REFERENCE));
		for (final Schema schema : schemas) {
			if (schema.getName() == null || schema.getName().isBlank()) {
				throw new IllegalArgumentException("Online assessment requires every Schema to have an Oracle owner name");
			}
			final String modeledCharacterSet = OracleCharacterSetAssessment.sourceCharacterSet(schema);
			if (modeledCharacterSet != null && !modeledCharacterSet.isBlank()
					&& !modeledCharacterSet.equalsIgnoreCase(sourceCharacterSet)) {
				findings.add(new Finding("oracle.charset.source-mismatch", Severity.WARNING, Evidence.DATABASE,
						new ObjectId(schema.getCatalogName(), schema.getName(), "schema", schema.getName()),
						"Schema evidence reports source character set=" + modeledCharacterSet
								+ "; connected database reports NLS_CHARACTERSET=" + sourceCharacterSet + ".",
						"Regenerate or correct the Schema snapshot and verify that the DataSource points to the intended export database before relying on this assessment.",
						REFERENCE));
			}
			assessColumns(connection, schema, sourceCharacterSet, scanCharacterData, findings);
		}
		return new MigrationAssessment(findings, offline.inventory());
	}

	private static Map<String, String> readNls(final Connection connection) throws SQLException {
		final var values = new LinkedHashMap<String, String>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT parameter, value FROM nls_database_parameters
				WHERE parameter IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET')
				"""); ResultSet rs = statement.executeQuery()) {
			while (rs.next()) {
				values.put(rs.getString(1).toUpperCase(Locale.ROOT), rs.getString(2));
			}
		}
		return values;
	}

	private static void assessColumns(final Connection connection, final Schema schema, final String sourceCharacterSet,
			final boolean scanCharacterData, final List<Finding> findings) throws SQLException {
		final Set<String> selectedTables = schema.getTables().stream().map(table -> table.getName())
				.collect(Collectors.toSet());
		final Set<String> availableTables = readTableNames(connection, schema.getName());
		final var resolvedTables = new LinkedHashSet<String>();
		final var missingTables = new ArrayList<String>();
		final var ambiguousTables = new ArrayList<String>();
		for (final String selected : selectedTables) {
			if (availableTables.contains(selected)) {
				resolvedTables.add(selected);
				continue;
			}
			final var matches = availableTables.stream().filter(table -> table.equalsIgnoreCase(selected)).toList();
			if (matches.size() == 1) {
				resolvedTables.add(matches.getFirst());
			} else if (matches.isEmpty()) {
				missingTables.add(selected);
			} else {
				ambiguousTables.add(selected);
			}
		}
		missingTables.sort(String::compareTo);
		ambiguousTables.sort(String::compareTo);
		for (final String table : missingTables) {
			findings.add(new Finding("oracle.charset.source-table-missing", Severity.WARNING, Evidence.DATABASE,
					new ObjectId(schema.getCatalogName(), schema.getName(), "table", table),
					"The table selected by the Schema XML was not visible in ALL_TABLES for owner " + schema.getName() + ".",
					"Verify the DataSource, owner name, object name and SELECT catalog privileges before relying on column or data-scan coverage.",
					REFERENCE));
		}
		for (final String table : ambiguousTables) {
			findings.add(new Finding("oracle.charset.source-table-ambiguous", Severity.WARNING, Evidence.DATABASE,
					new ObjectId(schema.getCatalogName(), schema.getName(), "table", table),
					"The table selected by the Schema XML matched multiple case-sensitive names in ALL_TABLES for owner "
							+ schema.getName() + ".",
					"Use the exact Oracle identifier spelling in the Schema XML; no table was selected for column or data scanning.",
					REFERENCE));
		}
		int characterColumns = 0;
		int scanCandidates = 0;
		int successfulScans = 0;
		int failedScans = 0;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT table_name, column_name, data_type, char_used, char_length, data_length
				FROM all_tab_columns
				WHERE owner = ?
				  AND data_type IN ('CHAR', 'VARCHAR2', 'NCHAR', 'NVARCHAR2', 'CLOB', 'NCLOB', 'LONG')
				ORDER BY table_name, column_id
				""")) {
			statement.setString(1, schema.getName());
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					final String table = rs.getString("TABLE_NAME");
					final String column = rs.getString("COLUMN_NAME");
					if (!resolvedTables.contains(table)) {
						continue;
					}
					characterColumns++;
					final String type = rs.getString("DATA_TYPE");
					final String semantics = rs.getString("CHAR_USED");
					final long charLength = rs.getLong("CHAR_LENGTH");
					final long byteLength = rs.getLong("DATA_LENGTH");
					final var id = new ObjectId(schema.getCatalogName(), schema.getName(), "column", column, table);
					findings.add(new Finding("oracle.charset.database-column", Severity.REVIEW, Evidence.DATABASE, id,
							"Source metadata: dataType=" + type + "; CHAR_USED="
									+ (semantics == null ? "UNKNOWN" : semantics) + "; CHAR_LENGTH=" + charLength
									+ "; DATA_LENGTH=" + byteLength + "; NLS_CHARACTERSET=" + sourceCharacterSet + ".",
							"Compare this authoritative source metadata with generated import DDL and the target definition.", REFERENCE));
					if ("B".equalsIgnoreCase(semantics) && ("CHAR".equals(type) || "VARCHAR2".equals(type))) {
						scanCandidates++;
						if (!scanCharacterData) {
							continue;
						}
						if (scanColumn(connection, id, sourceCharacterSet, byteLength, findings)) {
							successfulScans++;
						} else {
							failedScans++;
						}
					}
				}
			}
		}
		findings.add(new Finding("oracle.charset.online-coverage", Severity.REVIEW, Evidence.DATABASE,
				new ObjectId(schema.getCatalogName(), schema.getName(), "schema", schema.getName()),
				"Online assessment coverage: selected tables=" + selectedTables.size() + "; matched tables="
						+ resolvedTables.size() + "; missing tables=" + missingTables.size() + "; ambiguous tables="
						+ ambiguousTables.size()
						+ "; character columns="
						+ characterColumns + "; scan candidates=" + scanCandidates + "; successful scans="
						+ successfulScans + "; failed scans=" + failedScans + ".",
				"Confirm the selected Schema XML scope and retain this coverage with the migration evidence.", REFERENCE));
		if (!scanCharacterData) {
			findings.add(new Finding("oracle.charset.data-scan-disabled", Severity.REVIEW, Evidence.MANUAL_CHECK,
					new ObjectId(schema.getCatalogName(), schema.getName(), "schema", schema.getName()),
					"Connected metadata was read, but character data was not scanned.",
					"Set scanCharacterData=true only in an approved window after evaluating full-scan load, or use Oracle DMU/scanner evidence.", REFERENCE));
		}
	}

	private static Set<String> readTableNames(final Connection connection, final String owner) throws SQLException {
		final var names = new LinkedHashSet<String>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT table_name FROM all_tables
				WHERE owner = ?
				""")) {
			statement.setString(1, owner);
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					names.add(rs.getString(1));
				}
			}
		}
		return names;
	}

	private static boolean scanColumn(final Connection connection, final ObjectId id, final String sourceCharacterSet,
			final long byteLimit, final List<Finding> findings) {
		final String expression = "LENGTHB(CONVERT(" + identifier(id.name()) + ", 'AL32UTF8', '"
				+ sourceCharacterSet.replace("'", "''") + "'))";
		final String sql = "SELECT MAX(" + expression + "), SUM(CASE WHEN " + expression + " > " + byteLimit
				+ " THEN 1 ELSE 0 END) FROM " + identifier(id.schema()) + "." + identifier(id.table());
		try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
			rs.next();
			final long maximum = rs.getLong(1);
			final boolean noValues = rs.wasNull();
			final long overflows = rs.getLong(2);
			findings.add(new Finding(overflows > 0 ? "oracle.charset.data-overflow" : "oracle.charset.data-scan",
					overflows > 0 ? Severity.WARNING : Severity.REVIEW, Evidence.DATABASE, id,
					"Full source scan: target=AL32UTF8; source byte limit=" + byteLimit + "; maximum converted bytes="
							+ (noValues ? "NO_NON_NULL_VALUES" : maximum) + "; overflow rows=" + overflows + ".",
					overflows > 0
							? "Revise the target column definition or cleanse data, then repeat the scan and Data Pump rehearsal."
							: "Retain the scan evidence and still validate invalid source bytes, target DDL, indexes and application buffers.",
					REFERENCE));
			return true;
		} catch (SQLException e) {
			findings.add(new Finding("oracle.charset.data-scan-failed", Severity.WARNING, Evidence.DATABASE, id,
					"Character data scan failed with Oracle error code " + e.getErrorCode() + " and SQLState " + e.getSQLState() + ".",
					"Resolve object access, unsupported conversion, or source-data issues and rerun. No successful scan is claimed for this column.", REFERENCE));
			return false;
		}
	}

	private static String identifier(final String value) {
		if (value == null) {
			throw new IllegalArgumentException("Oracle identifier must not be null");
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}
}

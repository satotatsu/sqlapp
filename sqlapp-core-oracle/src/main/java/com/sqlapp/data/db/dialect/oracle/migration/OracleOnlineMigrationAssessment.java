/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
					if (!selectedTables.contains(table)) {
						continue;
					}
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
					if (scanCharacterData && "B".equalsIgnoreCase(semantics)
							&& ("CHAR".equals(type) || "VARCHAR2".equals(type))) {
						scanColumn(connection, id, sourceCharacterSet, byteLength, findings);
					}
				}
			}
		}
		if (!scanCharacterData) {
			findings.add(new Finding("oracle.charset.data-scan-disabled", Severity.REVIEW, Evidence.MANUAL_CHECK,
					new ObjectId(schema.getCatalogName(), schema.getName(), "schema", schema.getName()),
					"Connected metadata was read, but character data was not scanned.",
					"Set scanCharacterData=true only in an approved window after evaluating full-scan load, or use Oracle DMU/scanner evidence.", REFERENCE));
		}
	}

	private static void scanColumn(final Connection connection, final ObjectId id, final String sourceCharacterSet,
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
		} catch (SQLException e) {
			findings.add(new Finding("oracle.charset.data-scan-failed", Severity.WARNING, Evidence.DATABASE, id,
					"Character data scan failed with Oracle error code " + e.getErrorCode() + " and SQLState " + e.getSQLState() + ".",
					"Resolve object access, unsupported conversion, or source-data issues and rerun. No successful scan is claimed for this column.", REFERENCE));
		}
	}

	private static String identifier(final String value) {
		if (value == null) {
			throw new IllegalArgumentException("Oracle identifier must not be null");
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}
}

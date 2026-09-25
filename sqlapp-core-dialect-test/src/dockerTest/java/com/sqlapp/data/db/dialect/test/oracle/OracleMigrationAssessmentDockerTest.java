/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.oracle.OracleContainer;

import com.sqlapp.data.db.command.migration.assessment.AssessMigrationCommand;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.CharacterSemantics;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Method;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** Real JDBC coverage for the Oracle online migration assessment. */
class OracleMigrationAssessmentDockerTest {
	private static final String ASSESSOR = "MIGRATION_ASSESSOR";
	private static final String ASSESSOR_PASSWORD = "migration-assessor-password";
	private static final String NO_ACCESS_ASSESSOR = "MIGRATION_NO_ACCESS";
	private static final String NO_ACCESS_PASSWORD = "migration-no-access-password";
	private static final OracleContainer ORACLE = ReusableTestcontainers
			.configure(new OracleContainer(OracleTestEnvironment.image()));

	@TempDir
	Path directory;

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ORACLE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ORACLE);
	}

	@Test
	void executesMetadataAndAggregateChecksThroughADataSource() throws Exception {
		try (HikariDataSource ownerDataSource = dataSource(ORACLE.getUsername(), ORACLE.getPassword());
				Connection connection = ownerDataSource.getConnection();
				Statement statement = connection.createStatement()) {
			assertJapaneseByteExpansion(connection);
			dropTableIfPresent(statement);
			statement.executeUpdate("""
					CREATE TABLE MIGRATION_CHARSET_TEST (
					  ID NUMBER PRIMARY KEY,
					  TEXT_VALUE VARCHAR2(20 BYTE),
					  TEXT_CHAR_VALUE VARCHAR2(20 CHAR),
					  NATIONAL_VALUE NVARCHAR2(20)
					)
					""");
			statement.executeUpdate("CREATE INDEX IDX_MIGRATION_CHARSET_TEXT ON MIGRATION_CHARSET_TEST (TEXT_VALUE)");
			statement.executeUpdate("CREATE INDEX IDX_MIGRATION_CHARSET_UPPER ON MIGRATION_CHARSET_TEST (UPPER(TEXT_VALUE))");
			statement.executeUpdate("""
					INSERT INTO MIGRATION_CHARSET_TEST
					  (ID, TEXT_VALUE, TEXT_CHAR_VALUE, NATIONAL_VALUE)
					VALUES (1, '日本語', '日本語', N'日本語')
					""");
			createAssessor();
			statement.executeUpdate("GRANT SELECT ON MIGRATION_CHARSET_TEST TO " + ASSESSOR);

			final String owner = ORACLE.getUsername().toUpperCase(Locale.ROOT);
			final var schema = new Schema(owner).setProductName("Oracle").setProductMajorVersion(23)
					.setCharacterSet("AL32UTF8");
			final var table = new Table("MIGRATION_CHARSET_TEST");
			table.getColumns().add(new Column("TEXT_VALUE").setDataType(DataType.VARCHAR).setLength(20)
					.setOctetLength(20).setCharacterSemantics(CharacterSemantics.Byte));
			table.getColumns().add(new Column("TEXT_CHAR_VALUE").setDataType(DataType.VARCHAR).setLength(20)
					.setOctetLength(80).setCharacterSemantics(CharacterSemantics.Char));
			table.getColumns().add(new Column("NATIONAL_VALUE").setDataType(DataType.NVARCHAR).setLength(20)
					.setOctetLength(40).setCharacterSemantics(CharacterSemantics.Char));
			schema.getTables().add(table);
			final var schemaFile = directory.resolve("oracle-source.xml").toFile();
			schema.writeXml(schemaFile);

			final var command = new AssessMigrationCommand();
			try (HikariDataSource assessorDataSource = dataSource(ASSESSOR, ASSESSOR_PASSWORD)) {
				assertSelectOnly(assessorDataSource);
				command.setSchemaFile(schemaFile);
				command.setOutputFile(directory.resolve("assessment.json").toFile());
				command.setTargetVersion("26ai");
				command.setMigrationMethod(Method.LOGICAL_MIGRATION);
				command.setTargetCharacterSet("AL32UTF8");
				command.setDataSource(assessorDataSource);
				command.setScanCharacterData(true);
				command.setScanQueryTimeoutSeconds(60);
				command.run();
			}
			assertSourceDataUnchanged(statement);

			final var report = command.getReport();
			assertTrue(report.onlineAssessment());
			assertTrue(report.scanCharacterData());
			assertTrue(report.assessment().findings().stream()
					.anyMatch(finding -> finding.ruleId().equals("oracle.source.database-identity")));
			assertTrue(report.assessment().findings().stream().anyMatch(finding ->
					finding.ruleId().equals("oracle.charset.database-settings")
							&& finding.reason().contains("NLS_CHARACTERSET=AL32UTF8")));
			assertTrue(report.assessment().findings().stream().anyMatch(finding ->
					finding.ruleId().equals("oracle.charset.database-column")
							&& finding.object().name().equals("TEXT_VALUE")
							&& finding.reason().contains("CHAR_USED=B")
							&& finding.reason().contains("DATA_LENGTH=20")));
			assertTrue(report.assessment().findings().stream().anyMatch(finding ->
					finding.ruleId().equals("oracle.charset.database-column")
							&& finding.object().name().equals("TEXT_CHAR_VALUE")
							&& finding.reason().contains("CHAR_USED=C")));
			assertTrue(report.assessment().findings().stream().anyMatch(finding ->
					finding.ruleId().equals("oracle.charset.database-column")
							&& finding.object().name().equals("NATIONAL_VALUE")
							&& finding.reason().contains("dataType=NVARCHAR2")));
			assertTrue(report.assessment().findings().stream()
					.anyMatch(finding -> finding.ruleId().equals("oracle.charset.indexed-byte-column")));
			assertTrue(report.assessment().findings().stream().anyMatch(finding ->
					finding.ruleId().equals("oracle.charset.function-based-index")
							&& finding.reason().contains("IDX_MIGRATION_CHARSET_UPPER")));
			assertTrue(report.assessment().findings().stream().anyMatch(finding ->
					finding.ruleId().equals("oracle.charset.data-scan")
							&& finding.reason().contains("maximum converted bytes=9")
							&& finding.reason().contains("overflow rows=0")));
			assertFalse(report.assessment().findings().stream()
					.anyMatch(finding -> finding.ruleId().equals("oracle.charset.data-scan-failed")
							|| finding.ruleId().equals("oracle.charset.data-scan-timeout")));
			assertTrue(report.assessment().findings().stream().anyMatch(finding ->
					finding.ruleId().equals("oracle.charset.online-coverage")
							&& finding.reason().contains("database character columns=3")
							&& finding.reason().contains("scan candidates=1")
							&& finding.reason().contains("successful scans=1")));
			assertTrue(command.getOutputFile().isFile());
			final String json = Files.readString(command.getOutputFile().toPath(), StandardCharsets.UTF_8);
			assertTrue(json.contains("maximum converted bytes=9"));
			assertTrue(json.contains("overflow rows=0"));
			assertFalse(json.contains("日本語"));
			assertFalse(json.contains(ASSESSOR));
			assertFalse(json.contains(ASSESSOR_PASSWORD));

			assertWrongSourceIsReported(owner);
			assertMissingGrantIsReported(schemaFile);
		}
	}

	private void assertWrongSourceIsReported(final String owner) throws Exception {
		final var schema = new Schema(owner).setProductName("Oracle").setProductMajorVersion(10)
				.setProductMinorVersion(2).setCharacterSet("JA16SJIS");
		final var table = new Table("MIGRATION_CHARSET_TEST");
		table.getColumns().add(new Column("TEXT_VALUE").setDataType(DataType.VARCHAR).setLength(20)
				.setOctetLength(20).setCharacterSemantics(CharacterSemantics.Byte));
		schema.getTables().add(table);
		final var schemaFile = directory.resolve("oracle-10g-ja16sjis-source.xml").toFile();
		schema.writeXml(schemaFile);

		final var command = new AssessMigrationCommand();
		try (HikariDataSource dataSource = dataSource(ASSESSOR, ASSESSOR_PASSWORD)) {
			command.setSchemaFile(schemaFile);
			command.setOutputFile(directory.resolve("assessment-wrong-source.json").toFile());
			command.setTargetVersion("26ai");
			command.setMigrationMethod(Method.LOGICAL_MIGRATION);
			command.setTargetCharacterSet("AL32UTF8");
			command.setDataSource(dataSource);
			command.run();
		}
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(finding ->
				finding.ruleId().equals("oracle.source.version-mismatch")
						&& finding.reason().contains("version=10.2")
						&& finding.reason().contains("version=23")));
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(finding ->
				finding.ruleId().equals("oracle.charset.source-mismatch")
						&& finding.reason().contains("JA16SJIS")
						&& finding.reason().contains("NLS_CHARACTERSET=AL32UTF8")));
	}

	private void assertMissingGrantIsReported(final java.io.File schemaFile) throws Exception {
		createUser(NO_ACCESS_ASSESSOR, NO_ACCESS_PASSWORD);
		final var command = new AssessMigrationCommand();
		try (HikariDataSource dataSource = dataSource(NO_ACCESS_ASSESSOR, NO_ACCESS_PASSWORD)) {
			command.setSchemaFile(schemaFile);
			command.setOutputFile(directory.resolve("assessment-no-access.json").toFile());
			command.setTargetVersion("26ai");
			command.setMigrationMethod(Method.LOGICAL_MIGRATION);
			command.setTargetCharacterSet("AL32UTF8");
			command.setDataSource(dataSource);
			command.run();
		}
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(finding ->
				finding.ruleId().equals("oracle.charset.source-table-missing")
						&& finding.reason().contains("not visible in ALL_TABLES")));
		assertTrue(command.getReport().assessment().findings().stream().anyMatch(finding ->
				finding.ruleId().equals("oracle.charset.online-coverage")
						&& finding.reason().contains("matched tables=0")
						&& finding.reason().contains("missing tables=1")));
	}

	private static void assertSelectOnly(final HikariDataSource dataSource) throws SQLException {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			assertThrows(SQLException.class, () -> statement.executeUpdate(
					"UPDATE " + ORACLE.getUsername() + ".MIGRATION_CHARSET_TEST SET TEXT_VALUE = 'changed' WHERE ID = 1"));
		}
	}

	private static void assertSourceDataUnchanged(final Statement statement) throws SQLException {
		try (ResultSet rs = statement.executeQuery(
				"SELECT COUNT(*), MIN(TEXT_VALUE) FROM MIGRATION_CHARSET_TEST")) {
			assertTrue(rs.next());
			assertEquals(1, rs.getInt(1));
			assertEquals("日本語", rs.getString(2));
		}
	}

	private static void createAssessor() throws SQLException {
		createUser(ASSESSOR, ASSESSOR_PASSWORD);
	}

	private static void createUser(final String username, final String password) throws SQLException {
		try (Connection connection = DriverManager.getConnection(ORACLE.getJdbcUrl(), "system", ORACLE.getPassword());
				Statement statement = connection.createStatement()) {
			try {
				statement.executeUpdate("DROP USER " + username + " CASCADE");
			} catch (SQLException e) {
				if (e.getErrorCode() != 1918) {
					throw e;
				}
			}
			statement.executeUpdate("CREATE USER " + username + " IDENTIFIED BY \"" + password + "\"");
			statement.executeUpdate("GRANT CREATE SESSION TO " + username);
		}
	}

	private static void assertJapaneseByteExpansion(final Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT UTL_RAW.LENGTH(UTL_I18N.STRING_TO_RAW(?, 'JA16SJIS')),
				       UTL_RAW.LENGTH(UTL_RAW.CONVERT(
				           UTL_I18N.STRING_TO_RAW(?, 'JA16SJIS'), 'AL32UTF8', 'JA16SJIS'))
				FROM dual
				""")) {
			statement.setString(1, "日本語");
			statement.setString(2, "日本語");
			try (ResultSet rs = statement.executeQuery()) {
				assertTrue(rs.next());
				assertEquals(6, rs.getInt(1));
				assertEquals(9, rs.getInt(2));
			}
		}
	}

	private static void dropTableIfPresent(final Statement statement) throws SQLException {
		try {
			statement.executeUpdate("DROP TABLE MIGRATION_CHARSET_TEST PURGE");
		} catch (SQLException e) {
			if (e.getErrorCode() != 942) {
				throw e;
			}
		}
	}

	private static HikariDataSource dataSource(final String username, final String password) {
		final var config = new HikariConfig();
		config.setJdbcUrl(ORACLE.getJdbcUrl());
		config.setUsername(username);
		config.setPassword(password);
		config.setMaximumPoolSize(2);
		return new HikariDataSource(config);
	}
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment.Method;
import com.sqlapp.exceptions.CommandException;

class AssessMigrationCommandTest {
	@TempDir Path directory;

	private AssessMigrationCommand command() throws Exception {
		final var input = directory.resolve("schema.xml").toFile();
		new Schema("日本語").setProductName("Oracle").setProductMajorVersion(10).writeXml(input);
		final var command = new AssessMigrationCommand();
		command.setSchemaFile(input);
		command.setOutputFile(directory.resolve("report.json").toFile());
		command.setTargetVersion("26ai");
		command.setMigrationMethod(Method.DIRECT_UPGRADE);
		return command;
	}

	@Test
	void writesEvidenceBeforeFailingGateAndPreservesInput() throws Exception {
		final var command = command();
		final byte[] before = Files.readAllBytes(command.getSchemaFile().toPath());
		assertThrows(CommandException.class, command::run);
		final String json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("BLOCKED"));
		assertTrue(json.contains("日本語"));
		assertTrue(json.contains("DOCUMENTED_RULE"));
		assertTrue(command.getReport().schemaFingerprint().matches("sha256:[a-f0-9]{64}"));
		assertArrayEquals(before, Files.readAllBytes(command.getSchemaFile().toPath()));
		command.setFailOnBlockers(false);
		command.run();
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}

	@Test
	void assessesCatalogAndLogicalMigrationWithoutClaimingReadiness() throws Exception {
		final var command = command();
		final var catalog = new Catalog("DB").setProductName("Oracle").setProductMajorVersion(10);
		catalog.getSchemas().add(schema -> schema.setName("A"));
		catalog.getSchemas().add(schema -> schema.setName("B"));
		catalog.writeXml(command.getSchemaFile());
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.run();
		assertEquals("REVIEW_REQUIRED", command.getReport().status());
		assertEquals(2, command.getReport().sources().size());
		assertEquals("DB", command.getReport().sources().getFirst().catalog());
	}

	@Test
	void rejectsInvalidInputsAndDoesNotOverwriteSource() throws Exception {
		final var command = command();
		command.setOutputFile(command.getSchemaFile());
		assertThrows(CommandException.class, command::run);
		assertTrue(Files.readString(command.getSchemaFile().toPath()).contains("schema"));
		command.setOutputFile(directory.resolve("out.json").toFile());
		command.setMigrationMethod(null);
		assertThrows(CommandException.class, command::run);
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.setTargetVersion("19c");
		assertThrows(CommandException.class, command::run);
		command.setTargetVersion("26ai");
		new Table("T").writeXml(command.getSchemaFile());
		assertThrows(CommandException.class, command::run);
		Files.writeString(command.getSchemaFile().toPath(), "invalid XML");
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
		assertFalse(command.getOutputFile().exists());
	}

	@Test
	void recordsTargetCharsetAndUnknownColumnSemanticsWithoutChangingXml() throws Exception {
		final var command = command();
		final var schema = new Schema("日本語").setProductName("Oracle").setProductMajorVersion(10);
		final var table = new Table("T");
		table.getColumns().add(new Column("C").setDataType(DataType.VARCHAR).setLength(20));
		schema.getTables().add(table);
		schema.writeXml(command.getSchemaFile());
		final byte[] before = Files.readAllBytes(command.getSchemaFile().toPath());
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.setTargetCharacterSet("AL32UTF8");
		command.run();
		assertEquals("AL32UTF8", command.getReport().targetCharacterSet());
		assertFalse(command.getReport().onlineAssessment());
		assertFalse(command.getReport().scanCharacterData());
		assertNull(command.getReport().scanQueryTimeoutSeconds());
		final String json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("oracle.charset.source-unknown"));
		assertTrue(json.contains("oracle.charset.semantics-unknown"));
		assertTrue(json.contains("\"targetCharacterSet\""));
		assertArrayEquals(before, Files.readAllBytes(command.getSchemaFile().toPath()));
		command.setTargetCharacterSet("UTF8-32");
		assertThrows(CommandException.class, command::run);
		assertNull(command.getReport());
		assertEquals(json, Files.readString(command.getOutputFile().toPath()));
	}

	@Test
	void rejectsDataScanWithoutDataSource() throws Exception {
		final var command = command();
		command.setScanCharacterData(true);
		assertThrows(CommandException.class, command::run);
		assertFalse(command.getOutputFile().exists());
		command.setDataSource((DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { DataSource.class }, (proxy, method, args) -> null));
		command.setScanQueryTimeoutSeconds(0);
		assertThrows(CommandException.class, command::run);
	}

	@Test
	void onlinePathMarksConnectionReadOnlyClosesItAndPreservesExistingReportOnFailure() throws Exception {
		final var command = command();
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.setTargetCharacterSet("AL32UTF8");
		Files.writeString(command.getOutputFile().toPath(), "previous-report");
		final boolean[] readOnly = { false };
		final boolean[] closed = { false };
		final DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getDatabaseProductName" -> "PostgreSQL";
				case "getDatabaseProductVersion" -> "test";
				default -> defaultValue(method.getReturnType());
				});
		final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getMetaData" -> metadata;
				case "isReadOnly" -> readOnly[0];
				case "setReadOnly" -> { readOnly[0] = (boolean) args[0]; yield null; }
				case "close" -> { closed[0] = true; yield null; }
				default -> defaultValue(method.getReturnType());
				});
		final DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { DataSource.class }, (proxy, method, args) ->
						"getConnection".equals(method.getName()) ? connection : defaultValue(method.getReturnType()));
		command.setDataSource(dataSource);
		assertThrows(CommandException.class, command::run);
		assertTrue(readOnly[0]);
		assertTrue(closed[0]);
		assertEquals("previous-report", Files.readString(command.getOutputFile().toPath()));
		assertNull(command.getReport());
	}

	@Test
	void onlinePathWritesDatabaseIdentityAndDatabaseEvidence() throws Exception {
		final var command = command();
		command.setMigrationMethod(Method.LOGICAL_MIGRATION);
		command.setTargetCharacterSet("AL32UTF8");
		final boolean[] readOnly = { false };
		final boolean[] closed = { false };
		final DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getDatabaseProductName" -> "Oracle Database";
				case "getDatabaseProductVersion" -> "10.2.0.5.0";
				default -> defaultValue(method.getReturnType());
				});
		final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> switch (method.getName()) {
				case "getMetaData" -> metadata;
				case "isReadOnly" -> readOnly[0];
				case "setReadOnly" -> { readOnly[0] = (boolean) args[0]; yield null; }
				case "prepareStatement" -> statement((String) args[0]);
				case "close" -> { closed[0] = true; yield null; }
				default -> defaultValue(method.getReturnType());
				});
		final DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { DataSource.class }, (proxy, method, args) ->
						"getConnection".equals(method.getName()) ? connection : defaultValue(method.getReturnType()));
		command.setDataSource(dataSource);
		command.setScanCharacterData(true);
		command.setScanQueryTimeoutSeconds(45);
		command.run();
		assertTrue(readOnly[0]);
		assertTrue(closed[0]);
		assertEquals("Oracle Database", command.getReport().databaseProductName());
		assertEquals("10.2.0.5.0", command.getReport().databaseProductVersion());
		assertTrue(command.getReport().onlineAssessment());
		assertTrue(command.getReport().scanCharacterData());
		assertEquals(45, command.getReport().scanQueryTimeoutSeconds());
		final String json = Files.readString(command.getOutputFile().toPath());
		assertTrue(json.contains("oracle.charset.database-settings"));
		assertTrue(json.contains("JA16SJIS"));
		assertTrue(json.contains("Oracle Database"));
		assertTrue(json.contains("\"scanQueryTimeoutSeconds\" : 45"));
	}

	private PreparedStatement statement(final String sql) {
		return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
					if (!"executeQuery".equals(method.getName())) return defaultValue(method.getReturnType());
					if (sql.contains("nls_database_parameters")) {
						return resultSet(new String[][] {
							{ "NLS_CHARACTERSET", "JA16SJIS" },
							{ "NLS_NCHAR_CHARACTERSET", "AL16UTF16" }
						});
					}
					return resultSet(new String[0][]);
				});
	}

	private ResultSet resultSet(final String[][] rows) {
		final int[] index = { -1 };
		return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
				(proxy, method, args) -> switch (method.getName()) {
				case "next" -> ++index[0] < rows.length;
				case "getString" -> rows[index[0]][((Integer) args[0]) - 1];
				default -> defaultValue(method.getReturnType());
				});
	}

	private static Object defaultValue(final Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		return 0;
	}
}

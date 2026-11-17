/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.host.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.migration.legacy.GenerateLegacyMigrationContractCommand;
import com.sqlapp.data.db.command.migration.legacy.GenerateLegacyRdbLoaderCommand;
import com.sqlapp.data.db.command.migration.legacy.JdbcTreeStagingLoader;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationContractIO;
import com.sqlapp.data.db.command.migration.legacy.LegacyMigrationLoadPlanIO;
import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationContract;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Copybook to tables: import, contract, binary extraction, generated loader,
 * staged load.
 */
class CobolLegacyMigrationEndToEndTest extends AbstractDbCommandTest {

	@TempDir
	File directory;

	@Test
	void headerDetailDataSetIsLoadedIntoParentAndChildTables() throws Exception {
		File copybook = new File(directory, "orders.cpy");
		Files.writeString(copybook.toPath(), CobolCsvExtractCommandTest.ORDERS);
		File importConfiguration = new File(directory, "import.yaml");
		Files.writeString(importConfiguration.toPath(),
				CobolCsvExtractCommandTest.ORDERS_IMPORT.replace("schemaName: HOST", "schemaName: PUBLIC"));
		CobolSchemaImportCommand importer = new CobolSchemaImportCommand();
		importer.setTargetFile(copybook);
		importer.setConfigurationFile(importConfiguration);
		importer.setOutputDirectory(directory);
		importer.run();

		GenerateLegacyMigrationContractCommand contractCommand = new GenerateLegacyMigrationContractCommand();
		contractCommand.setMappingFile(new File(directory, "orders-legacy-migration.yaml"));
		contractCommand.setOutputDirectory(directory);
		contractCommand.run();
		File contract = new File(directory, "orders-contract.yaml");
		LegacyMigrationContract generated = new LegacyMigrationContractIO().read(contract);
		assertEquals(LegacyMigrationContract.OccurrenceSourceMode.RECORD_SEQUENCE,
				generated.getDataSets().stream().filter(dataSet -> "ORDER_DETAIL".equals(dataSet.getTargetTable()))
						.findFirst().orElseThrow().getOccurrenceSourceMode());

		File data = new File(directory, "orders.bin");
		Files.write(data.toPath(),
				CobolCsvExtractCommandTest.fixed(new byte[][] { CobolCsvExtractCommandTest.header("000100", "20261116"),
						CobolCsvExtractCommandTest.detail("A0001", 3), CobolCsvExtractCommandTest.detail("B0002", -1),
						CobolCsvExtractCommandTest.header("000200", "20261117"),
						CobolCsvExtractCommandTest.detail("C0003", 10), CobolCsvExtractCommandTest.trailer(5) }));
		File extractConfiguration = new File(directory, "extract.yaml");
		Files.writeString(extractConfiguration.toPath(), CobolCsvExtractCommandTest.ORDERS_EXTRACT);
		File csvDirectory = new File(directory, "csv");
		CobolCsvExtractCommand extract = new CobolCsvExtractCommand();
		extract.setContractFile(contract);
		extract.setConfigurationFile(extractConfiguration);
		extract.setDataFile(data);
		extract.setOutputDirectory(csvDirectory);
		extract.run();

		File schemaFile = new File(directory, "orders.xml");
		File loaderDirectory = new File(directory, "loader");
		GenerateLegacyRdbLoaderCommand generator = new GenerateLegacyRdbLoaderCommand();
		generator.setContractFile(contract);
		generator.setSchemaFile(schemaFile);
		generator.setOutputDirectory(loaderDirectory);
		generator.run();
		var plan = new LegacyMigrationLoadPlanIO().read(new File(loaderDirectory, "orders-load-plan.yaml"));
		Schema schema = (Schema) SchemaUtils.readXml(schemaFile);

		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			for (int i = schema.getTables().size() - 1; i >= 0; i--) {
				dropTables(connection, schema.getTables().get(i).getName());
			}
			for (var dataSet : plan.getDataSets()) {
				dropTables(connection, dataSet.getStagingTable());
			}
			createTables(connection, schema);
			for (String statement : Files.readString(new File(loaderDirectory, "orders-staging.sql").toPath())
					.split(";")) {
				if (!statement.isBlank()) {
					executeSql(connection, statement.trim());
				}
			}
			for (var dataSet : generated.getDataSets()) {
				stage(connection, dataSet.getStagingTable(), new File(csvDirectory, dataSet.getFileName()));
			}
			connection.commit();
			connection.setAutoCommit(false);

			var result = new JdbcTreeStagingLoader(connection, schema, plan).load();

			assertEquals(2, result.roots());
			assertEquals(List.of("100:20261116", "200:20261117"),
					rows(connection, "SELECT ORDER_NO, ORDER_DATE FROM ORDER_HEADER ORDER BY ORDER_NO"));
			assertEquals(List.of("100:1:A0001:3", "100:2:B0002:-1", "200:1:C0003:10"), rows(connection,
					"SELECT ORDER_NO, LINE_NO, ITEM_CODE, QTY FROM ORDER_DETAIL ORDER BY ORDER_NO, LINE_NO"));
		}
	}

	/**
	 * Inserts a contract CSV (no quoted values in this test) into its staging
	 * table.
	 */
	private void stage(Connection connection, String table, File csv) throws Exception {
		List<String> lines = Files.readAllLines(csv.toPath(), StandardCharsets.UTF_8);
		String columns = lines.getFirst();
		String placeholders = String.join(",", java.util.Collections.nCopies(columns.split(",").length, "?"));
		try (var statement = connection
				.prepareStatement("INSERT INTO " + table + "(" + columns + ") VALUES (" + placeholders + ")")) {
			for (String line : lines.subList(1, lines.size())) {
				String[] values = line.split(",", -1);
				for (int i = 0; i < values.length; i++) {
					statement.setString(i + 1, values[i].isEmpty() ? null : values[i]);
				}
				statement.executeUpdate();
			}
		}
	}

	private static void createTables(Connection connection, Schema schema) throws Exception {
		var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
		schema.setDialect(dialect);
		var registry = dialect.createSqlFactoryRegistry();
		for (Table table : schema.getTables()) {
			table.setDialect(dialect);
			com.sqlapp.data.db.sql.SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
			for (SqlOperation operation : factory.createSql(table)) {
				if (operation.getSqlType().isSql()) {
					try (var statement = connection.createStatement()) {
						statement.execute(operation.getSqlText());
					}
				}
			}
		}
	}

	private static List<String> rows(Connection connection, String sql) throws Exception {
		List<String> rows = new java.util.ArrayList<>();
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			int columns = resultSet.getMetaData().getColumnCount();
			while (resultSet.next()) {
				StringBuilder row = new StringBuilder();
				for (int i = 1; i <= columns; i++) {
					row.append(i > 1 ? ":" : "").append(resultSet.getString(i).trim());
				}
				rows.add(row.toString());
			}
		}
		return rows;
	}
}

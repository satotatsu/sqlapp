package com.sqlapp.data.db.command.generator;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.generator.config.ColumnGeneratorConfig;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.zaxxer.hikari.HikariDataSource;

class GeneratorSchemaInsertTest extends AbstractGeneratorCommandTest {
	private TableGeneratorConfig config(String schema) {
		var config = new TableGeneratorConfig();
		config.setSchemaName(schema);
		config.setName("SAME_NAME");
		config.setStartValueSql("SELECT 0 AS ID FROM (VALUES(0))");
		config.setDataSourceExpression("iterator(1)");
		config.setColumnMappingExpression("new java.util.HashMap()");
		var column = new ColumnGeneratorConfig();
		column.setName("ID");
		column.setMinValue("1");
		column.setNextValue("_previous.ID + 1");
		config.addColumn(column);
		return config;
	}

	private void createTables(HikariDataSource ds) throws Exception {
		executeSql(ds, "CREATE SCHEMA GEN_A AUTHORIZATION DBA");
		executeSql(ds, "CREATE SCHEMA GEN_B AUTHORIZATION DBA");
		executeSql(ds, "CREATE TABLE GEN_A.SAME_NAME (ID INTEGER PRIMARY KEY)");
		executeSql(ds, "CREATE TABLE GEN_B.SAME_NAME (ID INTEGER PRIMARY KEY)");
	}

	private void dropSchemas(HikariDataSource ds) throws Exception {
		executeSql(ds, "DROP SCHEMA GEN_A CASCADE");
		executeSql(ds, "DROP SCHEMA GEN_B CASCADE");
	}

	private int count(HikariDataSource ds, String schema) throws Exception {
		try (var connection = ds.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + schema + ".SAME_NAME")) {
			assertTrue(rows.next());
			return rows.getInt(1);
		}
	}

	private GenerateDataInsertCommand command(HikariDataSource ds, List<TableGeneratorConfig> configs) {
		var command = new GenerateDataInsertCommand();
		command.setDataSource(ds);
		command.setCloseDataSource(false);
		command.setIncludeSchemas("GEN_A", "GEN_B");
		command.setIncludeTables("SAME_NAME");
		command.setDmlBatchSize(1);
		command.setTableConfigs(Map.of("SAME_NAME", configs));
		return command;
	}

	@Test
	void insertsIntoBothSchemasUsingExplicitIdentity() throws Exception {
		try (var ds = newInternalDataSource()) {
			createTables(ds);
			try {
				command(ds, List.of(config("GEN_A"), config("GEN_B"))).run();
				assertEquals(1, count(ds, "GEN_A"));
				assertEquals(1, count(ds, "GEN_B"));
			} finally {
				dropSchemas(ds);
			}
		}
	}

	@Test
	void schemaDirectoriesWorkWithProgrammaticConfigurationsWithoutDirectoryProperty() throws Exception {
		try (var ds = newInternalDataSource()) {
			createTables(ds);
			try {
				var a = config(null);
				a.setFile(new File(testProjectDir, "GEN_A/SAME_NAME.yaml"));
				var b = config(null);
				b.setFile(new File(testProjectDir, "GEN_B/SAME_NAME.yaml"));
				var command = command(ds, List.of(a, b));
				command.setUseSchemaNameDirectory(true);
				command.run();
				assertEquals(1, count(ds, "GEN_A"));
				assertEquals(1, count(ds, "GEN_B"));
			} finally {
				dropSchemas(ds);
			}
		}
	}

	@Test
	void ambiguousConfigurationFailsBeforeEarlierConfigurationInserts() throws Exception {
		try (var ds = newInternalDataSource()) {
			createTables(ds);
			try {
				var command = command(ds, List.of(config("GEN_A"), config(null)));
				assertThrows(MultiTableFoundException.class, command::run);
				assertEquals(0, count(ds, "GEN_A"));
				assertEquals(0, count(ds, "GEN_B"));
			} finally {
				dropSchemas(ds);
			}
		}
	}
}

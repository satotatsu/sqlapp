/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.data.db.command.host.pli.PliSchemaImportCommand;
import com.sqlapp.data.db.command.normalization.FirstNormalFormCommand;
import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.zaxxer.hikari.HikariDataSource;

class LegacyMigrationEndToEndTest extends AbstractDbCommandTest {

	@TempDir
	File temporaryDirectory;

	@Test
	void testPliToNormalizedRdbAndThreeLevelLoad() throws Exception {
		File pli = new File(temporaryDirectory, "company.pli");
		Files.writeString(pli.toPath(), """
				DCL 1 COMPANY_MASTER,
				      2 COMPANY_ID CHAR(4),
				      2 COMPANY_NAME CHAR(40),
				      2 CONTACT_DATE_1 CHAR(8),
				      2 CONTACT_DATE_2 CHAR(8),
				      2 DEPARTMENT_GROUP(10),
				        3 DEPT_CODE CHAR(5),
				        3 EMPLOYEE_LIST(50),
				          4 EMP_ID CHAR(6),
				          4 EMP_NAME CHAR(30);
				""");
		File config = new File(temporaryDirectory, "company.yaml");
		Files.writeString(config.toPath(), """
				formatVersion: 1
				schemaName: PUBLIC
				includeDeclarations: [COMPANY_MASTER]
				structures:
				  - name: COMPANY_MASTER
				    primaryKey: [COMPANY_ID]
				""");
		File imported = new File(temporaryDirectory, "imported");
		File mappingDirectory = new File(temporaryDirectory, "mapping");
		PliSchemaImportCommand importer = new PliSchemaImportCommand();
		importer.setTargetFile(pli);
		importer.setConfigurationFile(config);
		importer.setOutputDirectory(imported);
		importer.setMigrationMappingDirectory(mappingDirectory);
		importer.run();

		File mapping = new File(mappingDirectory, "company-legacy-migration.yaml");
		File normalized = new File(temporaryDirectory, "normalized");
		FirstNormalFormCommand normalizer = new FirstNormalFormCommand();
		normalizer.setTargetFile(new File(imported, "company.xml"));
		normalizer.setOutputDirectory(normalized);
		normalizer.setMigrationMappingFile(mapping);
		normalizer.setMinimumColumnCount(1);
		normalizer.setConvertCompositePrimaryKey(true);
		normalizer.run();

		File targetSchemaFile = new File(normalized, "company.xml");
		Schema targetSchema = (Schema) SchemaUtils.readXml(targetSchemaFile);
		assertEquals(4, targetSchema.getTables().size());
		assertNotNull(targetSchema.getTables().get("COMPANY_MASTER").getColumns().get("COMPANY_ID"));
		assertNotNull(targetSchema.getTables().get("DEPARTMENT_GROUP").getColumns().get("ID"));
		assertNotNull(targetSchema.getTables().get("DEPARTMENT_GROUP").getColumns().get("COMPANY_ID"));
		assertNotNull(targetSchema.getTables().get("EMPLOYEE_LIST").getColumns().get("ID"));
		assertNotNull(targetSchema.getTables().get("EMPLOYEE_LIST").getColumns().get("PARENT_ID"));
		assertNotNull(targetSchema.getTables().get("COMPANY_MASTER_DETAIL_1")
				.getColumns().get("CONTACT_DATE"));
		var accumulatedMapping = new LegacyMigrationMappingIO().read(mapping);
		for (var relationship : accumulatedMapping.getRelationships()) {
			assertNotEquals(relationship.getParentMappingId(), relationship.getChildMappingId(),
					() -> "Self relationship: id=" + relationship.getId()
							+ ", parent=" + relationship.getParentMappingId()
							+ ", child=" + relationship.getChildMappingId()
							+ ", sourceKeys=" + relationship.getSourceKeys()
							+ ", targetKeys=" + relationship.getTargetKeys());
		}

		File contractDirectory = new File(temporaryDirectory, "contract");
		GenerateLegacyMigrationContractCommand contractCommand =
				new GenerateLegacyMigrationContractCommand();
		contractCommand.setMappingFile(mapping);
		contractCommand.setOutputDirectory(contractDirectory);
		contractCommand.run();
		File contract = new File(contractDirectory, "company-contract.yaml");
		var generatedContract = new LegacyMigrationContractIO().read(contract);
		var numberedDetail = generatedContract.getDataSets().stream()
				.filter(dataSet -> "COMPANY_MASTER_DETAIL_1".equals(dataSet.getTargetTable()))
				.findFirst().orElseThrow();
		assertEquals("NUMBERED_COLUMNS", numberedDetail.getOccurrenceSourceMode());
		assertEquals(2, numberedDetail.getFields().stream()
				.filter(field -> "CONTACT_DATE".equals(field.getStagingColumn()))
				.findFirst().orElseThrow().getIndexedSources().size());

		File loaderDirectory = new File(temporaryDirectory, "loader");
		GenerateLegacyRdbLoaderCommand generator = new GenerateLegacyRdbLoaderCommand();
		generator.setContractFile(contract);
		generator.setSchemaFile(targetSchemaFile);
		generator.setOutputDirectory(loaderDirectory);
		generator.setRootBatchSize(2);
		generator.setCommitEveryRootBatches(1);
		generator.setRootCursorStrategy("REOPEN");
		generator.run();
		var plan = new LegacyMigrationLoadPlanIO()
				.read(new File(loaderDirectory, "company-load-plan.yaml"));

		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			dropGeneratedTables(connection, plan, targetSchema);
			createTargetTables(connection, targetSchema);
			executeScript(connection,
					Files.readString(new File(loaderDirectory, "company-staging.sql").toPath()));
			executeSql(connection, """
					INSERT INTO TMP_COMPANY_MASTER
						(COMPANY_ID,COMPANY_NAME)
					VALUES ('C001','Company 1')
					""");
			executeSql(connection, """
					INSERT INTO TMP_DEPARTMENT_GROUP
						(COMPANY_ID,DEPARTMENT_GROUP_NO,DEPT_CODE)
					VALUES ('C001',1,'D0001')
					""");
			executeSql(connection, """
					INSERT INTO TMP_EMPLOYEE_LIST
						(COMPANY_ID,DEPARTMENT_GROUP_NO,EMPLOYEE_LIST_NO,EMP_ID,EMP_NAME)
					VALUES ('C001',1,1,'E00001','Employee 1')
					""");
			executeSql(connection, """
					INSERT INTO TMP_COMPANY_MASTER_DETAIL_1
						(COMPANY_ID,ROW_NO,CONTACT_DATE)
					VALUES ('C001',1,'20260101'),('C001',2,'20260102')
					""");
			connection.commit();
			connection.setAutoCommit(false);

			assertEquals(1,
					new JdbcTreeStagingLoader(connection, targetSchema, plan).load());
			assertEquals(1, count(connection, "COMPANY_MASTER"));
			assertEquals(1, count(connection, "DEPARTMENT_GROUP"));
			assertEquals(1, count(connection, "EMPLOYEE_LIST"));
			assertEquals(2, count(connection,
					"COMPANY_MASTER_DETAIL_1 WHERE COMPANY_ID='C001'"));
			assertEquals(1, count(connection, """
					EMPLOYEE_LIST E
					JOIN DEPARTMENT_GROUP D ON D.ID=E.PARENT_ID
					JOIN COMPANY_MASTER C ON C.COMPANY_ID=D.COMPANY_ID
					WHERE C.COMPANY_ID='C001' AND D.DEPT_CODE='D0001'
					  AND E.EMP_ID='E00001'
					"""));
		}
	}

	private void createTargetTables(Connection connection, Schema schema) throws Exception {
		var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance()
				.getDialect(connection);
		schema.setDialect(dialect);
		var registry = dialect.createSqlFactoryRegistry();
		for (Table table : schema.getTables()) {
			table.setDialect(dialect);
			SqlFactory<Table> factory = registry.getSqlFactory(table, SqlType.CREATE);
			for (SqlOperation operation : factory.createSql(table)) {
				if (operation.getSqlType().isSql()) {
					executeSql(connection, operation.getSqlText());
				}
			}
		}
	}

	private void dropGeneratedTables(Connection connection,
			com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan plan,
			Schema schema) throws Exception {
		for (int i = schema.getTables().size() - 1; i >= 0; i--) {
			dropTables(connection, schema.getTables().get(i).getName());
		}
		for (var dataSet : plan.getDataSets()) {
			dropTables(connection, dataSet.getStagingTable());
		}
	}

	private void executeScript(Connection connection, String sql) throws Exception {
		for (String statement : sql.split(";")) {
			if (!statement.isBlank()) {
				executeSql(connection, statement.trim());
			}
		}
	}

	private int count(Connection connection, String expression) throws Exception {
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + expression)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}
}

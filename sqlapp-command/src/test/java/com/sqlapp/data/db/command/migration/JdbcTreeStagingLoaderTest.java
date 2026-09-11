/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.command.test.AbstractDbCommandTest;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.JoinKey;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.LoadDataSet;
import com.sqlapp.data.schemas.migration.LegacyMigrationLoadPlan.LoadField;
import com.sqlapp.exceptions.CommandException;
import com.zaxxer.hikari.HikariDataSource;

class JdbcTreeStagingLoaderTest extends AbstractDbCommandTest {

	@Test
	void testValidateSchemaRejectsMissingTargetColumn() {
		LegacyMigrationLoadPlan plan = plan();
		plan.getDataSets().getFirst().getFields().getFirst()
				.setTargetColumn("MISSING_COLUMN");

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validateSchema(plan, targetTables()));

		assertTrue(exception.getMessage().contains("Target column was not found"));
	}

	@Test
	void testValidateSchemaRejectsMissingBusinessKeyColumn() {
		LegacyMigrationLoadPlan plan = plan();
		plan.getDataSets().getFirst().getSourceBusinessKey().clear();
		plan.getDataSets().getFirst().getSourceBusinessKey().add("MISSING_KEY");

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validateSchema(plan, targetTables()));

		assertTrue(exception.getMessage()
				.contains("Source business-key staging column was not found"));
	}

	@Test
	void testValidateSchemaRejectsMissingParentJoinColumn() {
		LegacyMigrationLoadPlan plan = plan();
		plan.getDataSets().get(1).getParentJoinKeys().getFirst()
				.setParentStagingColumn("MISSING_PARENT_KEY");

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validateSchema(plan, targetTables()));

		assertTrue(exception.getMessage()
				.contains("Parent/child staging join column was not found"));
	}

	@Test
	void testValidateSchemaRejectsMissingTargetPrimaryKeyColumn() {
		LegacyMigrationLoadPlan plan = plan();
		plan.getDataSets().getFirst().getTargetPrimaryKey().add("MISSING_PRIMARY_KEY");

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validateSchema(plan, targetTables()));

		assertTrue(exception.getMessage()
				.contains("Target primary-key column was not found"));
	}

	@Test
	void testValidateSchemaRejectsTargetPrimaryKeyThatDisagreesWithSchema() {
		LegacyMigrationLoadPlan plan = plan();
		plan.getDataSets().getFirst().getTargetPrimaryKey().add("COMPANY_ID");

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validateSchema(plan, targetTables()));

		assertTrue(exception.getMessage()
				.contains("Target primary key disagrees with schema"));
	}

	@Test
	void testValidateSchemaRequiresUsableKeyForInsertIgnore() {
		LegacyMigrationLoadPlan plan = plan();
		java.util.List<Table> tables = targetTables();
		tables.getFirst().getConstraints().clear();

		CommandException exception = assertThrows(CommandException.class,
				() -> LegacyMigrationLoadPlanIO.validateSchema(plan, tables));

		assertTrue(exception.getMessage().contains(
				"Target table requires a primary key, unique key, or non-null unique index"));
	}

	@Test
	void testValidateSchemaAllowsKeylessPlainInsert() {
		LegacyMigrationLoadPlan plan = plan();
		plan.setTableOperationMode("INSERT");
		java.util.List<Table> tables = targetTables();
		tables.forEach(table -> table.getConstraints().clear());

		assertDoesNotThrow(() -> LegacyMigrationLoadPlanIO.validateSchema(plan, tables));
	}

	@Test
	void testValidateSchemaAllowsUniqueKeyForInsertIgnore() {
		LegacyMigrationLoadPlan plan = plan();
		java.util.List<Table> tables = targetTables();
		Table company = tables.getFirst();
		company.getConstraints().clear();
		company.getConstraints().addUniqueConstraint("UK_COMPANY_ID",
				company.getColumns().get("COMPANY_ID"));

		assertDoesNotThrow(() -> LegacyMigrationLoadPlanIO.validateSchema(plan, tables));
	}

	@Test
	void testLoadHierarchyAndCleanupOrphansOnNextRun() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			connection.setAutoCommit(false);
			LegacyMigrationLoadPlan plan = plan();
			plan.setRootCursorStrategy("HOLD");
			JdbcTreeStagingLoader loader = new JdbcTreeStagingLoader(connection, schema, plan);

			assertEquals(2, loader.load());
			assertEquals(2, count(connection, "COMPANY_MASTER"));
			assertEquals(3, count(connection, "EMPLOYEE_LIST"));
			assertEquals(0, count(connection, "TMP_COMPANY_MASTER"));
			assertEquals(3, count(connection, "TMP_EMPLOYEE_LIST"));
			assertEquals(3, count(connection, """
					EMPLOYEE_LIST E
					INNER JOIN COMPANY_MASTER C ON C.ID=E.PARENT_ID
					WHERE (C.COMPANY_ID='C001' AND E.EMP_ID IN ('E001','E002'))
					   OR (C.COMPANY_ID='C002' AND E.EMP_ID='E003')
					"""));

			assertEquals(0, new JdbcTreeStagingLoader(connection, schema, plan).load());
			assertEquals(0, count(connection, "TMP_EMPLOYEE_LIST"));
		}
	}

	@Test
	void testRollbackRestoresOrphansWhenTargetLoadFails() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, "DELETE FROM TMP_EMPLOYEE_LIST");
			executeSql(connection, "DELETE FROM TMP_COMPANY_MASTER");
			executeSql(connection, """
					ALTER TABLE COMPANY_MASTER ADD CONSTRAINT CK_COMPANY_ID
					CHECK (COMPANY_ID <> 'FAIL')
					""");
			executeSql(connection, """
					INSERT INTO TMP_EMPLOYEE_LIST(LEGACY_COMPANY_ID,EMP_ID)
					VALUES ('ORPH','E001')
					""");
			executeSql(connection,
					"INSERT INTO TMP_COMPANY_MASTER(LEGACY_COMPANY_ID) VALUES ('FAIL')");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			connection.setAutoCommit(false);
			JdbcTreeStagingLoader loader = new JdbcTreeStagingLoader(connection, schema, plan());

			assertThrows(java.sql.SQLException.class, loader::load);
			connection.rollback();

			assertEquals(1, count(connection, "TMP_EMPLOYEE_LIST"));
			assertEquals(1, count(connection, "TMP_COMPANY_MASTER"));
			assertEquals(0, count(connection, "COMPANY_MASTER"));
		}
	}

	@Test
	void testCommitOrphanCleanupWhenThereAreNoPendingRoots() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource()) {
			try (Connection connection = dataSource.getConnection()) {
				createTables(connection);
				executeSql(connection, "DELETE FROM TMP_COMPANY_MASTER");
				connection.commit();
				Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
				connection.setAutoCommit(false);

				assertEquals(0,
						new JdbcTreeStagingLoader(connection, schema, plan()).load());
			}

			try (Connection verification = dataSource.getConnection()) {
				assertEquals(0, count(verification, "TMP_EMPLOYEE_LIST"));
			}
		}
	}

	@Test
	void testResumeFromFailedRootAfterEarlierRootWasCommitted() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, "DELETE FROM TMP_EMPLOYEE_LIST");
			executeSql(connection, "DELETE FROM TMP_COMPANY_MASTER");
			executeSql(connection, """
					ALTER TABLE COMPANY_MASTER ADD CONSTRAINT CK_COMPANY_ID
					CHECK (COMPANY_ID <> 'FAIL')
					""");
			executeSql(connection, """
					INSERT INTO TMP_COMPANY_MASTER(LEGACY_COMPANY_ID)
					VALUES ('C001'),('FAIL')
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			LegacyMigrationLoadPlan plan = plan();
			plan.setRootBatchSize(1);
			plan.setCommitEveryRootBatches(1);
			connection.setAutoCommit(false);

			assertThrows(java.sql.SQLException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan).load());
			connection.rollback();

			assertEquals(1, count(connection, "COMPANY_MASTER"));
			assertEquals(1, count(connection, "TMP_COMPANY_MASTER"));
			assertEquals(1, count(connection,
					"TMP_COMPANY_MASTER WHERE LEGACY_COMPANY_ID='FAIL' AND SQLAPP_LOAD_STATUS='PENDING'"));

			executeSql(connection,
					"ALTER TABLE COMPANY_MASTER DROP CONSTRAINT CK_COMPANY_ID");
			executeSql(connection,
					"UPDATE TMP_COMPANY_MASTER SET LEGACY_COMPANY_ID='C002' WHERE LEGACY_COMPANY_ID='FAIL'");
			connection.commit();
			Schema resumedSchema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			connection.setAutoCommit(false);

			assertEquals(1,
					new JdbcTreeStagingLoader(connection, resumedSchema, plan).load());
			assertEquals(2, count(connection, "COMPANY_MASTER"));
			assertEquals(0, count(connection, "TMP_COMPANY_MASTER"));
		}
	}

	@Test
	void testResumeFromChildFailureRollsBackOnlyCurrentRoot() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, """
					ALTER TABLE EMPLOYEE_LIST ADD CONSTRAINT CK_EMPLOYEE_ID
					CHECK (EMP_ID <> 'E003')
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			LegacyMigrationLoadPlan plan = plan();
			plan.setRootBatchSize(1);
			plan.setCommitEveryRootBatches(1);
			connection.setAutoCommit(false);

			assertThrows(java.sql.SQLException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan).load());
			connection.rollback();

			assertEquals(1, count(connection, "COMPANY_MASTER"));
			assertEquals(2, count(connection, "EMPLOYEE_LIST"));
			assertEquals(1, count(connection, "TMP_COMPANY_MASTER"));
			assertEquals(3, count(connection, "TMP_EMPLOYEE_LIST"));
			assertEquals(2, count(connection,
					"TMP_EMPLOYEE_LIST WHERE LEGACY_COMPANY_ID='C001'"));
			assertEquals(1, count(connection,
					"TMP_COMPANY_MASTER WHERE LEGACY_COMPANY_ID='C002' AND SQLAPP_LOAD_STATUS='PENDING'"));
			assertEquals(1, count(connection,
					"TMP_EMPLOYEE_LIST WHERE LEGACY_COMPANY_ID='C002' AND EMP_ID='E003'"));

			executeSql(connection,
					"ALTER TABLE EMPLOYEE_LIST DROP CONSTRAINT CK_EMPLOYEE_ID");
			connection.commit();
			Schema resumedSchema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			connection.setAutoCommit(false);

			assertEquals(1,
					new JdbcTreeStagingLoader(connection, resumedSchema, plan).load());
			assertEquals(2, count(connection, "COMPANY_MASTER"));
			assertEquals(3, count(connection, "EMPLOYEE_LIST"));
			assertEquals(0, count(connection, "TMP_COMPANY_MASTER"));
			assertEquals(1, count(connection, "TMP_EMPLOYEE_LIST"));

			assertEquals(0,
					new JdbcTreeStagingLoader(connection, resumedSchema, plan).load());
			assertEquals(0, count(connection, "TMP_EMPLOYEE_LIST"));
		}
	}

	@Test
	void testFailureRollsBackAllRootsInCurrentCommitWindow() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, """
					ALTER TABLE EMPLOYEE_LIST ADD CONSTRAINT CK_EMPLOYEE_ID
					CHECK (EMP_ID <> 'E003')
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			LegacyMigrationLoadPlan plan = plan();
			plan.setRootBatchSize(1);
			plan.setCommitEveryRootBatches(2);
			connection.setAutoCommit(false);

			assertThrows(java.sql.SQLException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan).load());
			connection.rollback();

			assertEquals(0, count(connection, "COMPANY_MASTER"));
			assertEquals(0, count(connection, "EMPLOYEE_LIST"));
			assertEquals(2, count(connection, "TMP_COMPANY_MASTER"));
			assertEquals(2, count(connection,
					"TMP_COMPANY_MASTER WHERE SQLAPP_LOAD_STATUS='PENDING'"));
			assertEquals(3, count(connection, "TMP_EMPLOYEE_LIST"));

			executeSql(connection,
					"ALTER TABLE EMPLOYEE_LIST DROP CONSTRAINT CK_EMPLOYEE_ID");
			connection.commit();
			Schema resumedSchema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			connection.setAutoCommit(false);

			assertEquals(2,
					new JdbcTreeStagingLoader(connection, resumedSchema, plan).load());
			assertEquals(2, count(connection, "COMPANY_MASTER"));
			assertEquals(3, count(connection, "EMPLOYEE_LIST"));
			assertEquals(0, count(connection, "TMP_COMPANY_MASTER"));
		}
	}

	@Test
	void testMarkRootsLoadedWhenStagingDeletionDisabled() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			LegacyMigrationLoadPlan plan = plan();
			plan.setDeleteCommittedRoots(false);
			plan.setRootCursorStrategy("REOPEN");
			connection.setAutoCommit(false);

			assertEquals(2, new JdbcTreeStagingLoader(connection, schema, plan).load());
			assertEquals(2, count(connection,
					"TMP_COMPANY_MASTER WHERE SQLAPP_LOAD_STATUS='LOADED'"));
			assertEquals(3, count(connection, "TMP_EMPLOYEE_LIST"));
			assertEquals(0, new JdbcTreeStagingLoader(connection, schema, plan).load());
		}
	}

	@Test
	void testResumeUsesRootStatusWhenStagingDeletionDisabled() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, "DELETE FROM TMP_EMPLOYEE_LIST");
			executeSql(connection, "DELETE FROM TMP_COMPANY_MASTER");
			executeSql(connection, """
					ALTER TABLE COMPANY_MASTER ADD CONSTRAINT CK_COMPANY_ID
					CHECK (COMPANY_ID <> 'FAIL')
					""");
			executeSql(connection, """
					INSERT INTO TMP_COMPANY_MASTER(LEGACY_COMPANY_ID)
					VALUES ('C001'),('FAIL')
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			LegacyMigrationLoadPlan plan = plan();
			plan.setDeleteCommittedRoots(false);
			plan.setRootBatchSize(1);
			plan.setCommitEveryRootBatches(1);
			plan.setRootCursorStrategy("REOPEN");
			connection.setAutoCommit(false);

			assertThrows(java.sql.SQLException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan).load());
			connection.rollback();

			assertEquals(1, count(connection, "COMPANY_MASTER"));
			assertEquals(1, count(connection,
					"TMP_COMPANY_MASTER WHERE LEGACY_COMPANY_ID='C001' AND SQLAPP_LOAD_STATUS='LOADED' AND SQLAPP_LOADED_AT IS NOT NULL"));
			assertEquals(1, count(connection,
					"TMP_COMPANY_MASTER WHERE LEGACY_COMPANY_ID='FAIL' AND SQLAPP_LOAD_STATUS='PENDING' AND SQLAPP_LOADED_AT IS NULL"));

			executeSql(connection,
					"ALTER TABLE COMPANY_MASTER DROP CONSTRAINT CK_COMPANY_ID");
			executeSql(connection,
					"UPDATE TMP_COMPANY_MASTER SET LEGACY_COMPANY_ID='C002' WHERE LEGACY_COMPANY_ID='FAIL'");
			connection.commit();
			Schema resumedSchema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			connection.setAutoCommit(false);

			assertEquals(1,
					new JdbcTreeStagingLoader(connection, resumedSchema, plan).load());
			assertEquals(2, count(connection, "COMPANY_MASTER"));
			assertEquals(2, count(connection,
					"TMP_COMPANY_MASTER WHERE SQLAPP_LOAD_STATUS='LOADED' AND SQLAPP_LOADED_AT IS NOT NULL"));
		}
	}

	@Test
	void testReportAllMissingDatabaseStagingColumnsBeforeLoading() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();
			LegacyMigrationLoadPlan plan = plan();
			plan.getDataSets().getFirst().getFields().add(
					field(2, "MISSING_COMPANY_COLUMN", "COMPANY_ID", true, false, "COPY"));
			plan.getDataSets().get(1).getFields().add(
					field(3, "MISSING_EMPLOYEE_COLUMN", "EMP_ID", true, false, "COPY"));

			CommandException exception = assertThrows(CommandException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan));

			assertTrue(exception.getMessage().contains("staging table of data set company"));
			assertTrue(exception.getMessage().contains("staging table of data set employee"));
			assertEquals(1, exception.getCause().getSuppressed().length);
		}
	}

	@Test
	void testRejectNullPendingRootBusinessKeyBeforeLoading() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection,
					"INSERT INTO TMP_COMPANY_MASTER(LEGACY_COMPANY_ID) VALUES (NULL)");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();

			CommandException exception = assertThrows(CommandException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan()));

			assertTrue(exception.getMessage()
					.contains("pending root contains a null business key: company"));
		}
	}

	@Test
	void testRejectDuplicatePendingRootBusinessKeyBeforeLoading() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection,
					"INSERT INTO TMP_COMPANY_MASTER(LEGACY_COMPANY_ID) VALUES ('C001')");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();

			CommandException exception = assertThrows(CommandException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan()));

			assertTrue(exception.getMessage()
					.contains("pending root contains a duplicate business key: company"));
		}
	}

	@Test
	void testIgnoreLoadedRootWhenCheckingPendingDuplicateKeys() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, """
					INSERT INTO TMP_COMPANY_MASTER(
						LEGACY_COMPANY_ID,SQLAPP_LOAD_STATUS,SQLAPP_LOADED_AT)
					VALUES ('C001','LOADED',CURRENT_TIMESTAMP)
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();

			assertDoesNotThrow(() -> new JdbcTreeStagingLoader(connection, schema, plan()));
		}
	}

	@Test
	void testRejectUnsupportedRootLoadStatusBeforeLoading() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, """
					INSERT INTO TMP_COMPANY_MASTER(LEGACY_COMPANY_ID,SQLAPP_LOAD_STATUS)
					VALUES ('C003','BROKEN')
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();

			CommandException exception = assertThrows(CommandException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan()));

			assertTrue(exception.getMessage()
					.contains("root contains an unsupported load status: company"));
		}
	}

	@Test
	void testRejectInconsistentRootLoadStatusTimestampsBeforeLoading() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, """
					UPDATE TMP_COMPANY_MASTER
					SET SQLAPP_LOAD_STATUS='LOADED'
					WHERE LEGACY_COMPANY_ID='C001'
					""");
			executeSql(connection, """
					UPDATE TMP_COMPANY_MASTER
					SET SQLAPP_LOADED_AT=CURRENT_TIMESTAMP
					WHERE LEGACY_COMPANY_ID='C002'
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();

			CommandException exception = assertThrows(CommandException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan()));

			assertTrue(exception.getMessage()
					.contains("loaded root has no loaded timestamp: company"));
			assertTrue(exception.getMessage()
					.contains("pending root has a loaded timestamp: company"));
			assertEquals(0, count(connection, "COMPANY_MASTER"));
		}
	}

	@Test
	void testRejectNullChildJoinKeyBeforeOrphanCleanup() throws Exception {
		try (HikariDataSource dataSource = newInternalDataSource();
				Connection connection = dataSource.getConnection()) {
			createTables(connection);
			executeSql(connection, """
					INSERT INTO TMP_EMPLOYEE_LIST(LEGACY_COMPANY_ID,EMP_ID)
					VALUES (NULL,'E004')
					""");
			connection.commit();
			Schema schema = SchemaUtils.getSchema(connection, "PUBLIC").orElseThrow();

			CommandException exception = assertThrows(CommandException.class,
					() -> new JdbcTreeStagingLoader(connection, schema, plan()));

			assertTrue(exception.getMessage()
					.contains("child staging row contains a null join key: employee"));
			assertEquals(4, count(connection, "TMP_EMPLOYEE_LIST"));
		}
	}

	private void createTables(Connection connection) throws Exception {
		dropTables(connection, "EMPLOYEE_LIST");
		dropTables(connection, "COMPANY_MASTER");
		dropTables(connection, "TMP_EMPLOYEE_LIST");
		dropTables(connection, "TMP_COMPANY_MASTER");
		executeSql(connection, """
				CREATE TABLE COMPANY_MASTER
				(
					  ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
					, COMPANY_ID VARCHAR(4) NOT NULL
					, CONSTRAINT UK_COMPANY_MASTER UNIQUE (COMPANY_ID)
				)
				""");
		executeSql(connection, """
				CREATE TABLE EMPLOYEE_LIST
				(
					  ID INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
					, PARENT_ID INT NOT NULL
					, EMP_ID VARCHAR(6) NOT NULL
					, FOREIGN KEY (PARENT_ID) REFERENCES COMPANY_MASTER(ID)
					, CONSTRAINT UK_EMPLOYEE_LIST UNIQUE (PARENT_ID, EMP_ID)
				)
				""");
		executeSql(connection, """
				CREATE TABLE TMP_COMPANY_MASTER
				(
					  LEGACY_COMPANY_ID VARCHAR(4)
					, SQLAPP_LOAD_STATUS VARCHAR(16) DEFAULT 'PENDING' NOT NULL
					, SQLAPP_LOADED_AT TIMESTAMP
				)
				""");
		executeSql(connection, """
				CREATE TABLE TMP_EMPLOYEE_LIST
				(
					  LEGACY_COMPANY_ID VARCHAR(4)
					, EMP_ID VARCHAR(6)
					, SQLAPP_LOADED_AT TIMESTAMP
				)
				""");
		executeSql(connection,
				"INSERT INTO TMP_COMPANY_MASTER(LEGACY_COMPANY_ID) VALUES ('C001'),('C002')");
		executeSql(connection, """
				INSERT INTO TMP_EMPLOYEE_LIST(LEGACY_COMPANY_ID,EMP_ID)
				VALUES ('C001','E001'),('C001','E002'),('C002','E003')
				""");
		connection.commit();
	}

	private LegacyMigrationLoadPlan plan() {
		LegacyMigrationLoadPlan plan = new LegacyMigrationLoadPlan();
		plan.setTableOperationMode("INSERT_IGNORE");
		plan.setRootBatchSize(1);
		plan.setCommitEveryRootBatches(2);
		LoadDataSet company = dataSet("company", "COMPANY_MASTER", "TMP_COMPANY_MASTER", null, 0);
		company.getSourceBusinessKey().add("LEGACY_COMPANY_ID");
		company.getFields().add(field(1, "LEGACY_COMPANY_ID", "COMPANY_ID", true, false, "COPY"));
		company.getFields().add(field(0, "ID", "ID", false, true, "GENERATE"));
		plan.getDataSets().add(company);
		LoadDataSet employee = dataSet("employee", "EMPLOYEE_LIST", "TMP_EMPLOYEE_LIST", company.getId(), 1);
		employee.getFields().add(field(1, "LEGACY_COMPANY_ID", null, true, false, "DROP"));
		employee.getFields().add(field(2, "EMP_ID", "EMP_ID", true, false, "COPY"));
		employee.getFields().add(field(0, "ID", "ID", false, true, "GENERATE"));
		employee.getFields().add(field(0, "PARENT_ID", "PARENT_ID", false, true, "GENERATE"));
		JoinKey key = new JoinKey();
		key.setParentStagingColumn("LEGACY_COMPANY_ID");
		key.setChildStagingColumn("LEGACY_COMPANY_ID");
		key.setTargetForeignKeyColumn("PARENT_ID");
		employee.getParentJoinKeys().add(key);
		plan.getDataSets().add(employee);
		return plan;
	}

	private java.util.List<Table> targetTables() {
		Schema schema = new Schema("PUBLIC");
		Table company = new Table("COMPANY_MASTER");
		company.getColumns().add(new Column("ID"));
		company.getColumns().add(new Column("COMPANY_ID"));
		company.setPrimaryKey(company.getColumns().get("ID"));
		schema.getTables().add(company);
		Table employee = new Table("EMPLOYEE_LIST");
		employee.getColumns().add(new Column("ID"));
		employee.getColumns().add(new Column("PARENT_ID"));
		employee.getColumns().add(new Column("EMP_ID"));
		employee.setPrimaryKey(employee.getColumns().get("ID"));
		schema.getTables().add(employee);
		return SchemaUtils.toTables(schema);
	}

	private LoadDataSet dataSet(String id, String target, String staging, String parent, int order) {
		LoadDataSet dataSet = new LoadDataSet();
		dataSet.setId(id);
		dataSet.setTargetSchema("PUBLIC");
		dataSet.setTargetTable(target);
		dataSet.setStagingTable(staging);
		dataSet.setParentDataSetId(parent);
		dataSet.setHierarchyDepth(order);
		dataSet.setLoadOrder(order);
		return dataSet;
	}

	private LoadField field(int position, String staging, String target, boolean extracted,
			boolean generated, String action) {
		LoadField field = new LoadField();
		field.setCsvPosition(position);
		field.setStagingColumn(staging);
		field.setTargetColumn(target);
		field.setExtracted(extracted);
		field.setTargetGenerated(generated);
		field.setAction(action);
		return field;
	}

	private int count(Connection connection, String expression) throws Exception {
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + expression)) {
			assertTrue(resultSet.next());
			return resultSet.getInt(1);
		}
	}
}

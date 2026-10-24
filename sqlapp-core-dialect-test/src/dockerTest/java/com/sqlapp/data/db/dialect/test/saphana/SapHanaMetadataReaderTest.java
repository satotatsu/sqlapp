/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.saphana;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.saphana.SapHana;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.PartitioningType;
import com.sqlapp.data.schemas.Table.TableDataStoreType;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.jdbc.sql.ParameterDirection;

/** SAP HANA 2.0 integration coverage for the metadata reader tree. */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class SapHanaMetadataReaderTest {
	@Test
	@org.junit.jupiter.api.Order(Integer.MAX_VALUE)
	void testSequenceResetByAfterContainerRestart() throws Exception {
		try (var connection = createConnection(); var statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_RESET_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_RESET_TEST");
			statement.execute("CREATE COLUMN TABLE HANA_RESET_TEST.T(ID INTEGER)");
			statement.execute("INSERT INTO HANA_RESET_TEST.T VALUES(10)");
			var sequence = new com.sqlapp.data.schemas.Sequence("S").setSchemaName("HANA_RESET_TEST").setStartValue(1)
					.setCache(false);
			sequence.getSpecifics().put("RESET_BY_QUERY", "SELECT COALESCE(MAX(ID),0)+1 FROM HANA_RESET_TEST.T");
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			for (var op : registry.createSql(sequence, SqlType.CREATE))
				statement.execute(op.getSqlText());
			try (var rows = statement.executeQuery("SELECT HANA_RESET_TEST.S.NEXTVAL FROM DUMMY")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getLong(1));
			}
			statement.execute("UPDATE HANA_RESET_TEST.T SET ID=99");
		}
		HANA.getDockerClient().restartContainerCmd(HANA.getContainerId()).withTimeout(30).exec();
		// Docker can assign a different ephemeral host port on restart. Inspect
		// fresh state rather than using Testcontainers' cached container info.
		var info = HANA.getDockerClient().inspectContainerCmd(HANA.getContainerId()).exec();
		var binding = info.getNetworkSettings().getPorts().getBindings()
				.get(com.github.dockerjava.api.model.ExposedPort.tcp(39041))[0];
		String url = "jdbc:sap://" + HANA.getHost() + ":" + binding.getHostPortSpec() + "/?databaseName=HXE";
		long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
		SQLException lastFailure = null;
		while (System.nanoTime() < deadline) {
			try (var connection = DriverManager.getConnection(url, "SYSTEM", PASSWORD);
					var statement = connection.createStatement()) {
				try {
					try (var rows = statement.executeQuery("SELECT HANA_RESET_TEST.S.NEXTVAL FROM DUMMY")) {
						assertTrue(rows.next());
						assertEquals(100, rows.getLong(1));
					}
				} finally {
					statement.execute("DROP SCHEMA HANA_RESET_TEST CASCADE");
				}
				return;
			} catch (SQLException failure) {
				lastFailure = failure;
				Thread.sleep(2000);
			}
		}
		throw new IllegalStateException("HANA did not recover after the RESET BY restart test", lastFailure);
	}

	@Test
	void testSingleLevelHashNumaRecreate() throws Exception {
		try (var connection = createConnection(); var statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_HASH_NUMA_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_HASH_NUMA_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_HASH_NUMA_TEST.T(A INTEGER) PARTITION BY HASH(A) PARTITIONS 2");
				for (int bucket = 1; bucket <= 2; bucket++)
					statement.execute(
							"ALTER TABLE HANA_HASH_NUMA_TEST.T ALTER PARTITION " + bucket + " NUMA NODE ('0')");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_HASH_NUMA_TEST");
				var source = reader.getAllFull(connection).getFirst();
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(source.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				statement.execute("DROP TABLE HANA_HASH_NUMA_TEST.T");
				var operations = registry.createSql(restored.getTables().get("T"), SqlType.CREATE);
				assertEquals(3, operations.size());
				for (var operation : operations)
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO HANA_HASH_NUMA_TEST.T VALUES(1)");
				assertEquals(source.getTables().get("T").getPartitioning(),
						reader.getAllFull(connection).getFirst().getTables().get("T").getPartitioning());
			} finally {
				statement.execute("DROP SCHEMA HANA_HASH_NUMA_TEST CASCADE");
			}
		}
	}

	@Test
	void testThreeLevelDefinitionsRecreateWithIndexes() throws Exception {
		try (var connection = createConnection(); var statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_THREE_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_THREE_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_THREE_TEST.T (A INTEGER, B INTEGER, C INTEGER, PRIMARY KEY(A,B,C)) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10) SUBPARTITION BY RANGE(B) ((PARTITION 0 <= VALUES < 100) SUBPARTITION BY HASH(C) PARTITIONS 2, (PARTITION OTHERS)), (PARTITION OTHERS))");
				statement.execute("CREATE INDEX IX_THREE ON HANA_THREE_TEST.T(B)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_THREE_TEST.R (A INTEGER, B INTEGER, C INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10) SUBPARTITION BY RANGE(B) ((PARTITION 0 <= VALUES < 100), (PARTITION OTHERS) SUBPARTITION BY RANGE(C) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS)), (PARTITION OTHERS))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_THREE_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				assertEquals(2, schema.getTables().size());
				for (var table : schema.getTables()) {
					assertEquals("DEFINITION", table.getSpecifics().get("PARTITIONING_REPRESENTATION"));
					assertTrue(table.getPartitioning() == null);
					assertFalse(table.getDefinition().isEmpty());
				}
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				for (var table : restored.getTables()) {
					statement.execute("DROP TABLE HANA_THREE_TEST." + table.getName());
					var operations = registry.createSql(table, SqlType.CREATE);
					assertEquals(table.getName().equals("T") ? 2 : 1, operations.size());
					for (var operation : operations)
						statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO HANA_THREE_TEST." + table.getName() + " VALUES(1, 1, 1)");
					statement.execute("INSERT INTO HANA_THREE_TEST." + table.getName() + " VALUES(20, 200, 20)");
				}
				var reread = reader.getAllFull(connection).getFirst();
				assertNotNull(reread.getTables().get("T").getIndexes().get("IX_THREE"));
				assertTrue(reread.getTables().get("T").getConstraints().stream()
						.anyMatch(c -> c instanceof UniqueConstraint u && u.isPrimaryKey()));
				for (var table : reread.getTables()) {
					assertEquals(3, table.getColumns().size());
					try (var rows = statement.executeQuery(
							"SELECT COUNT(*) FROM TABLE_PARTITIONS WHERE SCHEMA_NAME='HANA_THREE_TEST' AND TABLE_NAME='"
									+ table.getName() + "' AND LEVEL_3_PARTITION>0")) {
						assertTrue(rows.next());
						assertTrue(rows.getInt(1) > 0);
					}
				}
			} finally {
				statement.execute("DROP SCHEMA HANA_THREE_TEST CASCADE");
			}
		}
	}

	@Test
	void testUserLifecycleSystemPrivilegesAndColumnBoundary() throws Exception {
		try (var connection = createConnection(); var statement = connection.createStatement()) {
			drop(statement, "DROP USER HANA_LIFECYCLE_USER");
			drop(statement, "DROP USER HANA_RESTRICTED_USER");
			drop(statement, "DROP ROLE HANA_LIFECYCLE_ROLE");
			drop(statement, "DROP SCHEMA HANA_SECURITY_SCOPE CASCADE");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			try {
				var user = new com.sqlapp.data.schemas.User("HANA_LIFECYCLE_USER").setPassword("Lifecycle9xA");
				user.getSpecifics().put("PASSWORD_CHANGE_NEEDED", "FALSE");
				user.getSpecifics().put("PASSWORD_LIFETIME_CHECK_ENABLED", "FALSE");
				for (var op : registry.createSql(user, SqlType.CREATE))
					statement.execute(op.getSqlText());
				var userReader = dialect.getCatalogReader().getUserReader();
				userReader.setObjectName(user.getName());
				var readUser = userReader.getAll(connection).stream().filter(u -> u.getName().equals(user.getName()))
						.findFirst().orElseThrow();
				assertTrue(readUser.getPassword() == null);
				assertEquals("FALSE", readUser.getSpecifics().get("PASSWORD_LIFETIME_CHECK_ENABLED"));
				assertThrows(IllegalArgumentException.class, () -> registry.createSql(readUser, SqlType.CREATE));
				statement.execute("CREATE SCHEMA HANA_SECURITY_SCOPE");
				statement.execute("CREATE COLUMN TABLE HANA_SECURITY_SCOPE.T(A INTEGER, B INTEGER)");
				statement.execute("INSERT INTO HANA_SECURITY_SCOPE.T VALUES(1,2)");
				statement.execute("CREATE VIEW HANA_SECURITY_SCOPE.V AS SELECT A FROM HANA_SECURITY_SCOPE.T");
				for (String sql : new String[] { "GRANT SELECT(A) ON HANA_SECURITY_SCOPE.T TO HANA_LIFECYCLE_USER",
						"GRANT UPDATE(A) ON HANA_SECURITY_SCOPE.T TO HANA_LIFECYCLE_USER",
						"GRANT SELECT ON HANA_SECURITY_SCOPE.T(A) TO HANA_LIFECYCLE_USER" })
					assertEquals(257, assertThrows(SQLException.class, () -> statement.execute(sql)).getErrorCode());
				var viewGrant = new com.sqlapp.data.schemas.ObjectPrivilege().setSchemaName("HANA_SECURITY_SCOPE")
						.setObjectName("V").setGranteeName(user.getName()).setPrivilege("SELECT");
				for (var op : registry.createSql(viewGrant, SqlType.GRANT))
					statement.execute(op.getSqlText());
				try (var login = createUserConnection(user.getName(), "Lifecycle9xA");
						var select = login.createStatement()) {
					try (var rows = select.executeQuery("SELECT A FROM HANA_SECURITY_SCOPE.V")) {
						assertTrue(rows.next());
						assertEquals(1, rows.getInt(1));
					}
					assertThrows(SQLException.class, () -> select.executeQuery("SELECT B FROM HANA_SECURITY_SCOPE.T"));
				}
				var target = readUser.clone();
				target.getSpecifics().put("USER_DEACTIVATED", "TRUE");
				for (var op : registry.createSql(readUser.diff(target), SqlType.ALTER))
					statement.execute(op.getSqlText());
				assertThrows(SQLException.class, () -> {
					try (var ignored = createUserConnection(user.getName(), "Lifecycle9xA")) {
					}
				});
				var activated = target.clone().setPassword("LifecycleFresh9xA");
				activated.getSpecifics().put("USER_DEACTIVATED", "FALSE");
				activated.getSpecifics().put("PASSWORD_LIFETIME_CHECK_ENABLED", "TRUE");
				for (var op : registry.createSql(target.diff(activated), SqlType.ALTER))
					statement.execute(op.getSqlText());
				try (var login = createUserConnection(user.getName(), "LifecycleFresh9xA")) {
					assertFalse(login.isClosed());
				}
				var role = new com.sqlapp.data.schemas.Role("HANA_LIFECYCLE_ROLE");
				for (var op : registry.createSql(role, SqlType.CREATE))
					statement.execute(op.getSqlText());
				var userGrant = new com.sqlapp.data.schemas.UserPrivilege().setGranteeName(user.getName())
						.setPrivilege("CATALOG READ").setAdmin(true);
				var roleGrant = new com.sqlapp.data.schemas.RolePrivilege().setGranteeName(role.getName())
						.setPrivilege("DATA ADMIN").setAdmin(true);
				for (var op : registry.createSql(userGrant, SqlType.GRANT))
					statement.execute(op.getSqlText());
				for (var op : registry.createSql(roleGrant, SqlType.GRANT))
					statement.execute(op.getSqlText());
				var schemaGrant = new com.sqlapp.data.schemas.ObjectPrivilege().setSchemaName("HANA_SECURITY_SCOPE")
						.setGranteeName(role.getName()).setPrivilege("CREATE ANY").setGrantable(true);
				schemaGrant.getSpecifics().put("OBJECT_TYPE", "SCHEMA");
				for (var op : registry.createSql(schemaGrant, SqlType.GRANT))
					statement.execute(op.getSqlText());
				var objectPrivileges = dialect.getCatalogReader().getObjectPrivilegeReader();
				objectPrivileges.setSchemaName("HANA_SECURITY_SCOPE");
				var readSchemaGrant = objectPrivileges.getAll(connection).stream()
						.filter(p -> "CREATE ANY".equals(p.getPrivilege()) && role.getName().equals(p.getGranteeName()))
						.findFirst().orElseThrow();
				assertTrue(readSchemaGrant.isGrantable());
				for (var op : registry.createSql(readSchemaGrant, SqlType.REVOKE))
					statement.execute(op.getSqlText());
				var userPrivileges = dialect.getCatalogReader().getUserPrivilegeReader();
				userPrivileges.setUserName(user.getName());
				var rolePrivileges = dialect.getCatalogReader().getRolePrivilegeReader();
				rolePrivileges.setRoleName(role.getName());
				assertTrue(userPrivileges.getAll(connection).stream()
						.anyMatch(p -> p.getPrivilege().equals("CATALOG READ") && p.isAdmin()));
				assertTrue(rolePrivileges.getAll(connection).stream()
						.anyMatch(p -> p.getPrivilege().equals("DATA ADMIN") && p.isAdmin()));
				for (var op : registry.createSql(userGrant, SqlType.REVOKE))
					statement.execute(op.getSqlText());
				for (var op : registry.createSql(roleGrant, SqlType.REVOKE))
					statement.execute(op.getSqlText());
				assertTrue(userPrivileges.getAll(connection).isEmpty());
				assertTrue(rolePrivileges.getAll(connection).isEmpty());
				var restricted = new com.sqlapp.data.schemas.User("HANA_RESTRICTED_USER").setPassword("Restricted9xA");
				restricted.getSpecifics().put("IS_RESTRICTED", "TRUE");
				for (var op : registry.createSql(restricted, SqlType.CREATE))
					statement.execute(op.getSqlText());
				for (var op : registry.createSql(restricted, SqlType.DROP))
					statement.execute(op.getSqlText());
				for (var op : registry.createSql(role, SqlType.DROP))
					statement.execute(op.getSqlText());
				for (var op : registry.createSql(user, SqlType.DROP))
					statement.execute(op.getSqlText());
			} finally {
				drop(statement, "DROP USER HANA_LIFECYCLE_USER");
				drop(statement, "DROP USER HANA_RESTRICTED_USER");
				drop(statement, "DROP ROLE HANA_LIFECYCLE_ROLE");
				drop(statement, "DROP SCHEMA HANA_SECURITY_SCOPE CASCADE");
			}
		}
	}

	private Connection createUserConnection(String name, String password) throws SQLException {
		return DriverManager.getConnection("jdbc:sap://" + HANA.getHost() + ":" + HANA.getMappedPort(39041) + "/", name,
				password);
	}

	private static final String PASSWORD = "HxeTest9xA";
	private static final String PASSWORD_JSON = "{\"master_password\":\"" + PASSWORD + "\"}";
	private static final Path PASSWORD_DIRECTORY = createPasswordDirectory();
	private static final GenericContainer<?> HANA = ReusableTestcontainers
			.configure(new GenericContainer<>(DockerImageName.parse("saplabs/hanaexpress:2.00.088.00.20251110.1"))
					.withFileSystemBind(PASSWORD_DIRECTORY.toString(), "/hana/mounts", BindMode.READ_WRITE)
					.withCommand("--passwords-url", "file:///hana/mounts/password.json", "--agree-to-sap-license",
							"--dont-check-system")
					.withExposedPorts(39041)
					.withCreateContainerCmdModifier(
							command -> command.withHostName("hxehost").getHostConfig().withShmSize(1L << 30))
					.waitingFor(
							Wait.forLogMessage(".*Startup finished.*", 1).withStartupTimeout(Duration.ofMinutes(30))));

	@BeforeAll
	static void startContainer() {
		// Resolve providers before the expensive HANA startup so classpath
		// failures are reported immediately.
		DialectResolver.getInstance();
		ReusableTestcontainers.start(HANA);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(HANA);
		try {
			Files.deleteIfExists(PASSWORD_DIRECTORY.resolve("password.json"));
			Files.deleteIfExists(PASSWORD_DIRECTORY);
		} catch (Exception e) {
			// HANA normally removes password.json itself; cleanup is best effort.
		}
	}

	@Test
	void testReadsRepresentativeSchemaObjectsFromHana2() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			try {
				createObjects(statement);
				createSecurityObjects(statement);
				var dialect = DialectResolver.getInstance().getDialect(connection);
				assertInstanceOf(SapHana.class, dialect);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("METADATA_TEST");
				var schema = reader.getAllFull(connection).stream().filter(s -> "METADATA_TEST".equals(s.getName()))
						.findFirst().orElseThrow();
				var parent = schema.getTables().get("METADATA_PARENT");
				assertNotNull(parent);
				assertEquals(TableDataStoreType.Column, parent.getTableDataStoreType());
				assertEquals("TRUE", parent.getSpecifics().get("IS_COLUMN_TABLE"));
				assertEquals("FALSE", parent.getSpecifics().get("IS_INSERT_ONLY"));
				assertEquals("FALSE", parent.getSpecifics().get("IS_TEMPORARY"));
				assertNotNull(parent.getSpecifics().get("AUTO_MERGE_ON"));
				assertEquals("Metadata parent table", parent.getRemarks());
				assertEquals("Display name", parent.getColumns().get("NAME").getRemarks());
				var parentPrimaryKey = assertInstanceOf(UniqueConstraint.class,
						parent.getConstraints().get("PK_METADATA_PARENT"));
				assertTrue(parentPrimaryKey.isPrimaryKey());
				assertEquals("ID", parentPrimaryKey.getColumns().get(0).getName());
				assertEquals("REGION", parentPrimaryKey.getColumns().get(1).getName());
				var rowStore = schema.getTables().get("METADATA_ROW_STORE");
				assertNotNull(rowStore);
				assertEquals(TableDataStoreType.Row, rowStore.getTableDataStoreType());
				assertEquals("FALSE", rowStore.getSpecifics().get("IS_COLUMN_TABLE"));
				var child = schema.getTables().get("METADATA_CHILD");
				assertNotNull(child);
				assertEquals(IdentityGenerationType.ByDefault,
						child.getColumns().get("ID").getIdentityGenerationType());
				assertTrue(child.getColumns().get("CODE").getDefaultValue().contains("unknown"));
				var foreignKey = assertInstanceOf(ForeignKeyConstraint.class,
						child.getConstraints().get("FK_METADATA_CHILD_PARENT"));
				assertEquals(2, foreignKey.getColumns().size());
				assertEquals("PARENT_ID", foreignKey.getColumns().get(0).getName());
				assertEquals("PARENT_REGION", foreignKey.getColumns().get(1).getName());
				assertEquals("ID", foreignKey.getRelatedColumns().get(0).getName());
				assertEquals("REGION", foreignKey.getRelatedColumns().get(1).getName());
				var childUniqueKey = assertInstanceOf(UniqueConstraint.class,
						child.getConstraints().get("UK_METADATA_CHILD_CODE"));
				assertTrue(!childUniqueKey.isPrimaryKey());
				assertEquals("CODE", childUniqueKey.getColumns().get(0).getName());
				var checkConstraint = assertInstanceOf(CheckConstraint.class,
						child.getConstraints().get("CK_METADATA_CHILD_AMOUNT"));
				assertTrue(checkConstraint.getExpression().contains("AMOUNT"));
				assertTrue(checkConstraint.getExpression().contains(">= 0"));
				var childIndex = child.getIndexes().get("IDX_METADATA_CHILD_PARENT");
				assertNotNull(childIndex);
				assertEquals(IndexType.InvertedValue, childIndex.getIndexType());
				assertEquals("PARENT_ID", childIndex.getColumns().get(0).getName());
				assertEquals("PARENT_REGION", childIndex.getColumns().get(1).getName());
				var sequence = schema.getSequences().get("METADATA_SEQ");
				assertNotNull(sequence);
				assertEquals(50L, sequence.getStartValue().longValue());
				assertEquals(10L, sequence.getIncrementBy().longValue());
				assertEquals(10L, sequence.getMinValue().longValue());
				assertEquals(1000L, sequence.getMaxValue().longValue());
				assertTrue(sequence.isCycle());
				com.sqlapp.data.db.dialect.test.Db2HanaSequenceAuditAssertions.verify(connection, false);
				var view = schema.getViews().get("METADATA_VIEW");
				assertNotNull(view);
				assertTrue(view.getDefinition().toString().contains("METADATA_CHILD"));
				assertEquals(3, view.getColumns().size());
				assertEquals(DataType.BIGINT, view.getColumns().get("ID").getDataType());
				assertEquals(DataType.DECIMAL, view.getColumns().get("AMOUNT").getDataType());
				var synonym = schema.getSynonyms().get("METADATA_PARENT_SYNONYM");
				assertNotNull(synonym);
				assertEquals("METADATA_TEST", synonym.getSchemaName());
				assertEquals("METADATA_TEST", synonym.getObjectSchemaName());
				assertEquals("METADATA_PARENT", synonym.getObjectName());
				var partitioned = schema.getTables().get("METADATA_PARTITIONED");
				assertNotNull(partitioned);
				assertEquals(PartitioningType.Range, partitioned.getPartitioning().getPartitioningType());
				assertEquals("BUCKET", partitioned.getPartitioning().getPartitioningColumns().get(0).getName());
				assertEquals(3, partitioned.getPartitioning().getPartitions().size());
				assertTrue(partitioned.getPartitioning().getPartitions().stream()
						.allMatch(partition -> partition.getSpecifics().get("LOAD_UNIT") != null));
				assertTrue(partitioned.getPartitioning().getPartitions().stream()
						.allMatch(partition -> partition.getSpecifics().get("STORAGE_TYPE") != null));
				var multiPartitioned = schema.getTables().get("METADATA_MULTI_PARTITIONED");
				assertNotNull(multiPartitioned);
				assertEquals(PartitioningType.Hash, multiPartitioned.getPartitioning().getPartitioningType());
				assertEquals(PartitioningType.Range, multiPartitioned.getPartitioning().getSubPartitioningType());
				assertEquals("ID", multiPartitioned.getPartitioning().getPartitioningColumns().get(0).getName());
				assertEquals("BUCKET", multiPartitioned.getPartitioning().getSubPartitioningColumns().get(0).getName());
				assertEquals(2, multiPartitioned.getPartitioning().getPartitions().size());
				assertEquals(6, multiPartitioned.getPartitioning().getPartitions().stream()
						.mapToInt(partition -> partition.getSubPartitions().size()).sum());
				var documents = schema.getTables().get("METADATA_DOCUMENTS");
				assertNotNull(documents);
				var fulltextIndex = documents.getIndexes().get("IDX_METADATA_DOCUMENTS_TEXT");
				assertNotNull(fulltextIndex);
				assertEquals(IndexType.FullText, fulltextIndex.getIndexType());
				assertEquals(1, fulltextIndex.getColumns().size());
				assertNotNull(fulltextIndex.getColumns().get(0).getName());
				assertEquals("FALSE", fulltextIndex.getSpecifics().get("FAST_PREPROCESS"));
				assertEquals("FALSE", fulltextIndex.getSpecifics().get("FUZZY_SEARCH_INDEX"));
				assertEquals("TRUE", fulltextIndex.getSpecifics().get("SEARCH_ONLY"));
				assertEquals("TRUE", fulltextIndex.getSpecifics().get("IS_EXPLICIT"));
				var procedure = schema.getProcedures().get("METADATA_PROCEDURE");
				assertNotNull(procedure);
				assertNotNull(procedure.getStatement());
				assertEquals(2, procedure.getArguments().size());
				var procedureInput = procedure.getArguments().get("P_ID");
				assertNotNull(procedureInput);
				assertEquals(ParameterDirection.Input, procedureInput.getDirection());
				assertEquals(DataType.BIGINT, procedureInput.getDataType());
				var procedureOutput = procedure.getArguments().get("P_NAME");
				assertNotNull(procedureOutput);
				assertEquals(ParameterDirection.Output, procedureOutput.getDirection());
				assertEquals(DataType.NVARCHAR, procedureOutput.getDataType());
				var function = schema.getFunctions().get("METADATA_FUNCTION");
				assertNotNull(function);
				assertNotNull(function.getDefinition());
				assertEquals(2, function.getArguments().size());
				var functionInput = function.getArguments().get("P_AMOUNT");
				assertNotNull(functionInput);
				assertEquals(ParameterDirection.Input, functionInput.getDirection());
				assertEquals(DataType.DECIMAL, functionInput.getDataType());
				var functionResult = function.getArguments().get("RESULT");
				assertNotNull(functionResult);
				assertEquals(ParameterDirection.Output, functionResult.getDirection());
				assertEquals(DataType.DECIMAL, functionResult.getDataType());
				var trigger = schema.getTriggers().get("METADATA_TRIGGER");
				assertNotNull(trigger);
				assertEquals("AFTER", trigger.getActionTiming());
				assertTrue(trigger.getEventManipulation().contains("INSERT"));
				assertEquals("METADATA_TEST", trigger.getTableSchemaName());
				assertEquals("METADATA_CHILD", trigger.getTableName());
				assertNotNull(trigger.getDefinition());
				// Replay the catalog definitions through the public registry, rather
				// than merely checking that the metadata text is nonempty.
				statement.execute("DROP TRIGGER METADATA_TEST.METADATA_TRIGGER");
				statement.execute("DROP VIEW METADATA_TEST.METADATA_VIEW");
				statement.execute("DROP PROCEDURE METADATA_TEST.METADATA_PROCEDURE");
				statement.execute("DROP FUNCTION METADATA_TEST.METADATA_FUNCTION");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (Object object : new Object[] { view, procedure, function, trigger }) {
					var operations = registry.createSql(object, SqlType.CREATE);
					assertTrue(!operations.isEmpty(), object.getClass().getSimpleName());
					for (var operation : operations)
						statement.execute(operation.getSqlText());
				}
				try (var result = statement.executeQuery("SELECT METADATA_TEST.METADATA_FUNCTION(3) FROM DUMMY")) {
					assertTrue(result.next());
					assertEquals(6, result.getInt(1));
				}
				var catalogReader = dialect.getCatalogReader();
				var roleReader = catalogReader.getRoleReader();
				roleReader.setObjectName("METADATA_ROLE");
				assertNotNull(roleReader.getAll(connection).stream()
						.filter(role -> "METADATA_ROLE".equals(role.getName())).findFirst().orElseThrow());
				var userReader = catalogReader.getUserReader();
				userReader.setObjectName("METADATA_USER");
				assertNotNull(userReader.getAll(connection).stream()
						.filter(user -> "METADATA_USER".equals(user.getName())).findFirst().orElseThrow());
				var roleMemberReader = catalogReader.getRoleMemberReader();
				roleMemberReader.setGrantee("METADATA_USER");
				assertTrue(roleMemberReader.getAll(connection).stream()
						.anyMatch(member -> "METADATA_ROLE".equals(member.getMemberRoleName()) && member.isAdmin()));
				var objectPrivilegeReader = catalogReader.getObjectPrivilegeReader();
				objectPrivilegeReader.setSchemaName("METADATA_TEST");
				objectPrivilegeReader.setObjectName("METADATA_PARENT");
				assertTrue(objectPrivilegeReader.getAll(connection).stream()
						.anyMatch(privilege -> "METADATA_ROLE".equals(privilege.getGranteeName())
								&& "SELECT".equals(privilege.getPrivilege()) && privilege.isGrantable()));
			} finally {
				cleanupSecurityObjects(statement);
			}
		}
	}

	private Connection createConnection() throws SQLException {
		String url = "jdbc:sap://localhost:" + HANA.getMappedPort(39041) + "/?databaseName=HXE";
		return DriverManager.getConnection(url, "SYSTEM", PASSWORD);
	}

	@Test
	void testUniformTwoLevelPartitionModelsRecreate() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_MULTIPART_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_MULTIPART_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_MULTIPART_TEST.HR (A INTEGER, B INTEGER) PARTITION BY HASH(A) PARTITIONS 2, RANGE(B) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_MULTIPART_TEST.HH (A INTEGER, B INTEGER) PARTITION BY HASH(A) PARTITIONS 2, HASH(B) PARTITIONS 2");
				statement.execute(
						"CREATE COLUMN TABLE HANA_MULTIPART_TEST.RR (A INTEGER, B INTEGER) PARTITION BY RANGE(A) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS), RANGE(B) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_MULTIPART_TEST.BR (A INTEGER, B INTEGER) PARTITION BY ROUNDROBIN PARTITIONS 2, RANGE(B) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_MULTIPART_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var table : restored.getTables()) {
					assertNotNull(table.getPartitioning().getSubPartitioningType());
					statement.execute("DROP TABLE HANA_MULTIPART_TEST." + table.getName());
					for (var operation : registry.createSql(table, SqlType.CREATE))
						statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO HANA_MULTIPART_TEST." + table.getName() + " VALUES (1,5)");
					statement.execute("INSERT INTO HANA_MULTIPART_TEST." + table.getName() + " VALUES (15,15)");
				}
				var reread = reader.getAllFull(connection).getFirst();
				assertEquals(PartitioningType.Hash,
						reread.getTables().get("HH").getPartitioning().getSubPartitioningType());
				assertEquals(2, reread.getTables().get("HR").getPartitioning().getPartitions().getFirst()
						.getSubPartitions().size());
			} finally {
				statement.execute("DROP SCHEMA HANA_MULTIPART_TEST CASCADE");
			}
		}
	}

	@Test
	void testStorageAndPartitionModelsRecreate() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_STORAGE_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_STORAGE_TEST");
			try {
				statement.execute("CREATE ROW TABLE HANA_STORAGE_TEST.R (ID INTEGER)");
				statement.execute("CREATE COLUMN TABLE HANA_STORAGE_TEST.P (ID INTEGER COLUMN LOADABLE) PAGE LOADABLE");
				statement.execute(
						"CREATE COLUMN TABLE HANA_STORAGE_TEST.D (ID INTEGER, D DATE) PARTITION BY RANGE(D) (PARTITION '2024-01-01' <= VALUES < '2025-01-01', PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_STORAGE_TEST.H (ID INTEGER) PARTITION BY HASH(ID) PARTITIONS 2");
				statement.execute(
						"CREATE COLUMN TABLE HANA_STORAGE_TEST.G (ID INTEGER) PARTITION BY RANGE(ID) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_STORAGE_TEST.B (ID INTEGER) PARTITION BY ROUNDROBIN PARTITIONS 2");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_STORAGE_TEST");
				var schema = reader.getAllFull(connection).stream().filter(s -> "HANA_STORAGE_TEST".equals(s.getName()))
						.findFirst().orElseThrow();
				assertEquals(TableDataStoreType.Row, schema.getTables().get("R").getTableDataStoreType());
				assertEquals("PAGE", schema.getTables().get("P").getSpecifics().get("LOAD_UNIT"));
				assertEquals("COLUMN",
						schema.getTables().get("P").getColumns().get("ID").getSpecifics().get("LOAD_UNIT"));
				assertEquals(PartitioningType.Hash,
						schema.getTables().get("H").getPartitioning().getPartitioningType());
				assertEquals(2, schema.getTables().get("H").getPartitioning().getPartitionSize());
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var table : restored.getTables()) {
					statement.execute("DROP TABLE HANA_STORAGE_TEST." + table.getName());
					for (var operation : registry.createSql(table, SqlType.CREATE))
						statement.execute(operation.getSqlText());
					statement.execute("INSERT INTO HANA_STORAGE_TEST." + table.getName()
							+ (table.getName().equals("D") ? " VALUES (5, '2024-05-01')" : " VALUES (5)"));
					statement.execute("INSERT INTO HANA_STORAGE_TEST." + table.getName()
							+ (table.getName().equals("D") ? " VALUES (15, '2026-05-01')" : " VALUES (15)"));
				}
				var reread = reader.getAllFull(connection).getFirst();
				assertEquals("PAGE", reread.getTables().get("P").getSpecifics().get("LOAD_UNIT"));
				assertEquals("COLUMN",
						reread.getTables().get("P").getColumns().get("ID").getSpecifics().get("LOAD_UNIT"));
				assertEquals(PartitioningType.Range,
						reread.getTables().get("G").getPartitioning().getPartitioningType());
				assertEquals(2, reread.getTables().get("G").getPartitioning().getPartitions().size());
				assertEquals(PartitioningType.RoundRobin,
						reread.getTables().get("B").getPartitioning().getPartitioningType());
			} finally {
				statement.execute("DROP SCHEMA HANA_STORAGE_TEST CASCADE");
			}
		}
	}

	@Test
	void testDynamicRangeAndInsertPoliciesRecreate() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_POLICY_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_POLICY_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.T (A INTEGER NOT NULL) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 INSERT OFF, PARTITION OTHERS DYNAMIC THRESHOLD 1000000))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.M (A INTEGER, B INTEGER NOT NULL) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 INSERT OFF) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 10 INSERT ON, PARTITION OTHERS INSERT OFF), (PARTITION OTHERS) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 10 INSERT OFF, PARTITION OTHERS INSERT ON))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.H (A INTEGER, B INTEGER NOT NULL) PARTITION BY HASH(A) PARTITIONS 2 ((PARTITION 1) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 10 INSERT OFF, PARTITION OTHERS DYNAMIC THRESHOLD 1000000), (PARTITION 2) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 10 INSERT ON, PARTITION OTHERS DYNAMIC THRESHOLD 1000001))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.I (A INTEGER NOT NULL) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10, PARTITION OTHERS DYNAMIC INTERVAL 10))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.Y (A DATE NOT NULL) PARTITION BY RANGE(YEAR(A)) ((PARTITION VALUE = 2024, PARTITION OTHERS DYNAMIC INTERVAL 2 YEAR))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.MI (A DATE NOT NULL) PARTITION BY RANGE(MONTH(A)) ((PARTITION VALUE = 202401, PARTITION OTHERS DYNAMIC INTERVAL 2 MONTH))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.HI (A TIMESTAMP NOT NULL) PARTITION BY RANGE(HOUR(A)) ((PARTITION VALUE = '2024-01-01 00', PARTITION OTHERS DYNAMIC INTERVAL 2 HOUR))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.P (A INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 PERSISTENT MEMORY OFF, PARTITION OTHERS PERSISTENT MEMORY ON))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_POLICY_TEST.G (A INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 GROUP NAME 'a''b' GROUP TYPE 'T' GROUP SUBTYPE 'S', PARTITION OTHERS))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_POLICY_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				var other = schema.getTables().get("T").getPartitioning().getPartitions().getLast();
				assertEquals("TRUE", other.getSpecifics().get("IS_DYNAMIC").toUpperCase(java.util.Locale.ROOT));
				assertEquals("1000000", other.getStatistics().get("DYNAMIC_RANGE_THRESHOLD"));
				var hashParents = schema.getTables().get("H").getPartitioning().getPartitions();
				assertEquals("1000000", hashParents.getFirst().getSubPartitions().getLast().getStatistics()
						.get("DYNAMIC_RANGE_THRESHOLD"));
				assertEquals("1000001", hashParents.getLast().getSubPartitions().getLast().getStatistics()
						.get("DYNAMIC_RANGE_THRESHOLD"));
				assertEquals("2 YEAR", schema.getTables().get("Y").getPartitioning().getPartitions().getLast()
						.getSpecifics().get("DYNAMIC_RANGE_INTERVAL"));
				assertEquals("S", schema.getTables().get("G").getPartitioning().getPartitions().getFirst()
						.getSpecifics().get("SUB_TYPE"));
				assertEquals("a'b", schema.getTables().get("G").getPartitioning().getPartitions().getFirst()
						.getSpecifics().get("GROUP_NAME"));
				assertEquals("FALSE", schema.getTables().get("P").getPartitioning().getPartitions().getFirst()
						.getSpecifics().get("PERSISTENT_MEMORY"));
				assertEquals("TRUE", schema.getTables().get("P").getPartitioning().getPartitions().getLast()
						.getSpecifics().get("PERSISTENT_MEMORY"));
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var table : restored.getTables()) {
					statement.execute("DROP TABLE HANA_POLICY_TEST." + table.getName());
					for (var operation : registry.createSql(table, SqlType.CREATE))
						statement.execute(operation.getSqlText());
				}
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO HANA_POLICY_TEST.T VALUES (5)"));
				statement.execute("INSERT INTO HANA_POLICY_TEST.T VALUES (15)");
				statement.execute("INSERT INTO HANA_POLICY_TEST.M VALUES (5, 5)");
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO HANA_POLICY_TEST.M VALUES (5, 15)"));
				assertThrows(java.sql.SQLException.class,
						() -> statement.execute("INSERT INTO HANA_POLICY_TEST.M VALUES (15, 5)"));
				statement.execute("INSERT INTO HANA_POLICY_TEST.M VALUES (15, 15)");
				statement.execute("INSERT INTO HANA_POLICY_TEST.H VALUES (5, 15)");
				statement.execute("INSERT INTO HANA_POLICY_TEST.I VALUES (15)");
				statement.execute("INSERT INTO HANA_POLICY_TEST.Y VALUES ('2026-05-01')");
				statement.execute("INSERT INTO HANA_POLICY_TEST.MI VALUES ('2026-05-01')");
				statement.execute("INSERT INTO HANA_POLICY_TEST.HI VALUES ('2026-05-01 12:00:00')");
				var reread = reader.getAllFull(connection).getFirst();
				for (var table : schema.getTables())
					assertEquals(table.getPartitioning(), reread.getTables().get(table.getName()).getPartitioning(),
							table.getName());
				for (String name : new String[] { "I", "Y" }) {
					statement.execute("ALTER TABLE HANA_POLICY_TEST." + name + " ADD PARTITION FROM OTHERS");
					try (var rows = statement.executeQuery("SELECT COUNT(*) FROM HANA_POLICY_TEST." + name)) {
						assertTrue(rows.next());
						assertEquals(1, rows.getInt(1));
					}
				}
				var grown = reader.getAllFull(connection).getFirst();
				assertEquals(3, grown.getTables().get("I").getPartitioning().getPartitions().size());
				assertEquals(3, grown.getTables().get("Y").getPartitioning().getPartitions().size());
			} finally {
				statement.execute("DROP SCHEMA HANA_POLICY_TEST CASCADE");
			}
		}
	}

	@Test
	void testDynamicAgingAndNumaPreferencesRecreate() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_PLACEMENT_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_PLACEMENT_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.A (A INTEGER NOT NULL) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10, PARTITION OTHERS DYNAMIC INTERVAL 10 DISTANCE 20 PAGE LOADABLE))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.T (A INTEGER NOT NULL) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10, PARTITION OTHERS DYNAMIC THRESHOLD 1000000 DISTANCE 20 PAGE LOADABLE))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.Y (A DATE NOT NULL) PARTITION BY RANGE(YEAR(A)) ((PARTITION VALUE = 2024, PARTITION OTHERS DYNAMIC INTERVAL 2 YEAR DISTANCE 3 YEAR PAGE LOADABLE))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.N (A INTEGER) PARTITION BY RANGE(A) (PARTITION 0 <= VALUES < 10 NUMA NODE ('0'), PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.PN (A INTEGER) PARTITION BY RANGE(A) (PARTITION 0 <= VALUES < 10 PERSISTENT MEMORY OFF NUMA NODE ('0'), PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.NR (A INTEGER) PARTITION BY RANGE(A) (PARTITION 0 <= VALUES < 10 NUMA NODE ('0' TO '1','3'), PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.HN (A INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 INSERT OFF NUMA NODE ('0'), PARTITION OTHERS))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.H (A INTEGER, B INTEGER NOT NULL) PARTITION BY HASH(A) PARTITIONS 2 ((PARTITION 1) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS DYNAMIC INTERVAL 10 DISTANCE 20 PAGE LOADABLE), (PARTITION 2) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 10, PARTITION OTHERS DYNAMIC INTERVAL 10 DISTANCE 30 PAGE LOADABLE))");
				assertEquals(257, assertThrows(SQLException.class, () -> statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.BAD_MV (A INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 NOT MOVABLE, PARTITION OTHERS))"))
						.getErrorCode());
				assertEquals(257, assertThrows(SQLException.class, () -> statement.execute(
						"CREATE COLUMN TABLE HANA_PLACEMENT_TEST.BAD_DR (A INTEGER NOT NULL) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10, PARTITION OTHERS DYNAMIC BIDIRECTIONAL INTERVAL 10))"))
						.getErrorCode());
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_PLACEMENT_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				assertEquals(8, schema.getTables().size());
				assertEquals("0TO1,3", schema.getTables().get("NR").getPartitioning().getPartitions().getFirst()
						.getSpecifics().get("NUMA_NODE_INDEXES"));
				assertEquals("3 YEAR", schema.getTables().get("Y").getPartitioning().getPartitions().getLast()
						.getSpecifics().get("DYNAMIC_DISTANCE_INTERVAL"));
				assertEquals("PAGE", schema.getTables().get("Y").getPartitioning().getPartitions().getLast()
						.getSpecifics().get("DYNAMIC_DISTANCE_LOAD_UNIT"));
				var parents = schema.getTables().get("H").getPartitioning().getPartitions();
				assertEquals("20", parents.getFirst().getSubPartitions().getLast().getSpecifics()
						.get("DYNAMIC_DISTANCE_INTERVAL"));
				assertEquals("30",
						parents.getLast().getSubPartitions().getLast().getSpecifics().get("DYNAMIC_DISTANCE_INTERVAL"));
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var table : restored.getTables()) {
					statement.execute("DROP TABLE HANA_PLACEMENT_TEST." + table.getName());
					for (var operation : registry.createSql(table, SqlType.CREATE))
						statement.execute(operation.getSqlText());
				}
				var reread = reader.getAllFull(connection).getFirst();
				for (var table : schema.getTables())
					assertEquals(table.getPartitioning(), reread.getTables().get(table.getName()).getPartitioning(),
							table.getName());
				statement.execute("INSERT INTO HANA_PLACEMENT_TEST.A VALUES (1)");
				statement.execute("INSERT INTO HANA_PLACEMENT_TEST.A VALUES (105)");
				statement.execute("ALTER TABLE HANA_PLACEMENT_TEST.A ADD PARTITION FROM OTHERS");
				statement.execute("ALTER TABLE HANA_PLACEMENT_TEST.A REFRESH DYNAMIC DISTANCE");
				var aged = reader.getAllFull(connection).getFirst().getTables().get("A").getPartitioning();
				assertEquals("PAGE", aged.getPartitions().getFirst().getSpecifics().get("LOAD_UNIT"));
				assertTrue(aged.getPartitions().size() >= 3);
				try (var rows = statement.executeQuery("SELECT COUNT(*) FROM HANA_PLACEMENT_TEST.A")) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
				}
			} finally {
				statement.execute("DROP SCHEMA HANA_PLACEMENT_TEST CASCADE");
			}
		}
	}

	@Test
	void testHeterogeneousRangesAndPartitionLoadUnitsRecreate() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_NSE_RANGE_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_NSE_RANGE_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_NSE_RANGE_TEST.T (A INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 PAGE LOADABLE, PARTITION OTHERS COLUMN LOADABLE))");
				statement.execute(
						"CREATE COLUMN TABLE HANA_NSE_RANGE_TEST.M (A INTEGER, B INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 100 PAGE LOADABLE, PARTITION OTHERS), (PARTITION OTHERS) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 200 COLUMN LOADABLE, PARTITION OTHERS))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_NSE_RANGE_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				assertEquals("PAGE", schema.getTables().get("T").getPartitioning().getPartitions().getFirst()
						.getSpecifics().get("LOAD_UNIT"));
				assertEquals("100", schema.getTables().get("M").getPartitioning().getPartitions().getFirst()
						.getSubPartitions().getFirst().getHighValue());
				assertEquals("200", schema.getTables().get("M").getPartitioning().getPartitions().get(1)
						.getSubPartitions().getFirst().getHighValue());
				assertEquals("10",
						schema.getTables().get("M").getPartitioning().getPartitions().getFirst().getHighValue());
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				assertEquals("10",
						restored.getTables().get("M").getPartitioning().getPartitions().getFirst().getHighValue());
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var table : restored.getTables()) {
					statement.execute("DROP TABLE HANA_NSE_RANGE_TEST." + table.getName());
					for (var operation : registry.createSql(table, SqlType.CREATE))
						statement.execute(operation.getSqlText());
				}
				statement.execute("INSERT INTO HANA_NSE_RANGE_TEST.M VALUES (1, 50)");
				statement.execute("INSERT INTO HANA_NSE_RANGE_TEST.M VALUES (20, 150)");
				var reread = reader.getAllFull(connection).getFirst();
				assertEquals("PAGE", reread.getTables().get("T").getPartitioning().getPartitions().getFirst()
						.getSpecifics().get("LOAD_UNIT"));
				assertEquals("PAGE", reread.getTables().get("M").getPartitioning().getPartitions().getFirst()
						.getSubPartitions().getFirst().getSpecifics().get("LOAD_UNIT"));
				assertEquals("200", reread.getTables().get("M").getPartitioning().getPartitions().get(1)
						.getSubPartitions().getFirst().getHighValue());
			} finally {
				statement.execute("DROP SCHEMA HANA_NSE_RANGE_TEST CASCADE");
			}
		}
	}

	@Test
	void testRangeHashModelsRecreateWithDifferentBucketCounts() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_RANGE_HASH_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_RANGE_HASH_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_RANGE_HASH_TEST.T (A INTEGER, B INTEGER, C INTEGER) PARTITION BY RANGE(A) ((PARTITION 0 <= VALUES < 10 PAGE LOADABLE) SUBPARTITION BY HASH(B,C) PARTITIONS 2, (PARTITION 10 <= VALUES < 20 COLUMN LOADABLE) SUBPARTITION BY HASH(B,C) PARTITIONS 3, (PARTITION OTHERS))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_RANGE_HASH_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				var p = schema.getTables().get("T").getPartitioning();
				assertEquals(PartitioningType.Hash, p.getSubPartitioningType());
				assertEquals(2, p.getSubPartitioningColumns().size());
				assertEquals(2, p.getPartitions().getFirst().getSubPartitions().size());
				assertEquals(3, p.getPartitions().get(1).getSubPartitions().size());
				assertTrue(p.getPartitions().get(2).getSubPartitions().isEmpty());
				assertEquals("PAGE", p.getPartitions().getFirst().getSpecifics().get("LOAD_UNIT"));
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				statement.execute("DROP TABLE HANA_RANGE_HASH_TEST.T");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(restored.getTables().get("T"), SqlType.CREATE))
					statement.execute(operation.getSqlText());
				for (int value : new int[] { 1, 15, 30 })
					statement.execute("INSERT INTO HANA_RANGE_HASH_TEST.T VALUES (" + value + ", 2, 3)");
				var reread = reader.getAllFull(connection).getFirst().getTables().get("T").getPartitioning();
				assertEquals(2, reread.getPartitions().getFirst().getSubPartitions().size());
				assertEquals(3, reread.getPartitions().get(1).getSubPartitions().size());
				assertTrue(reread.getPartitions().get(2).getSubPartitions().isEmpty());
				assertEquals("PAGE", reread.getPartitions().getFirst().getSpecifics().get("LOAD_UNIT"));
			} finally {
				statement.execute("DROP SCHEMA HANA_RANGE_HASH_TEST CASCADE");
			}
		}
	}

	@Test
	void testHeterogeneousHashFirstModelsRecreate() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_HASH_FIRST_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_HASH_FIRST_TEST");
			try {
				statement.execute(
						"CREATE COLUMN TABLE HANA_HASH_FIRST_TEST.HR (A INTEGER, B INTEGER, C INTEGER) PARTITION BY HASH(A,C) PARTITIONS 2 ((PARTITION 1) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 100 PAGE LOADABLE, PARTITION OTHERS), (PARTITION 2) SUBPARTITION BY RANGE(B) (PARTITION 0 <= VALUES < 200, PARTITION OTHERS))");
				var unsupported = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,
						() -> statement.execute(
								"CREATE COLUMN TABLE HANA_HASH_FIRST_TEST.HH (A INTEGER, B INTEGER, C INTEGER) PARTITION BY HASH(A) PARTITIONS 2 ((PARTITION 1) SUBPARTITION BY HASH(B,C) PARTITIONS 2, (PARTITION 2) SUBPARTITION BY HASH(B,C) PARTITIONS 3)"));
				assertEquals(257, unsupported.getErrorCode());
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_HASH_FIRST_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				assertEquals(2, schema.getTables().get("HR").getPartitioning().getPartitioningColumns().size());
				assertEquals("100", schema.getTables().get("HR").getPartitioning().getPartitions().getFirst()
						.getSubPartitions().getFirst().getHighValue());
				assertEquals("200", schema.getTables().get("HR").getPartitioning().getPartitions().get(1)
						.getSubPartitions().getFirst().getHighValue());
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var table : restored.getTables()) {
					statement.execute("DROP TABLE HANA_HASH_FIRST_TEST." + table.getName());
					for (var operation : registry.createSql(table, SqlType.CREATE))
						statement.execute(operation.getSqlText());
					for (int value = 1; value <= 20; value++)
						statement.execute("INSERT INTO HANA_HASH_FIRST_TEST." + table.getName() + " VALUES (" + value
								+ ", " + (value * 15) + ", 3)");
				}
				var reread = reader.getAllFull(connection).getFirst();
				assertEquals("100", reread.getTables().get("HR").getPartitioning().getPartitions().getFirst()
						.getSubPartitions().getFirst().getHighValue());
				assertEquals("200", reread.getTables().get("HR").getPartitioning().getPartitions().get(1)
						.getSubPartitions().getFirst().getHighValue());
				assertEquals("PAGE", reread.getTables().get("HR").getPartitioning().getPartitions().getFirst()
						.getSubPartitions().getFirst().getSpecifics().get("LOAD_UNIT"));
			} finally {
				statement.execute("DROP SCHEMA HANA_HASH_FIRST_TEST CASCADE");
			}
		}
	}

	@Test
	void testDatePartitionExpressionsRecreateThroughXml() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_EXPR_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_EXPR_TEST");
			try {
				var unsupported = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class,
						() -> statement.execute(
								"CREATE COLUMN TABLE HANA_EXPR_TEST.BAD (\"Odd D\" TIMESTAMP) PARTITION BY RANGE(YEAR(\"Odd D\")) (PARTITION 2024 <= VALUES < 2025, PARTITION OTHERS)"));
				assertEquals(2048, unsupported.getErrorCode());
				String columns = " (ID INTEGER, D TIMESTAMP, D2 TIMESTAMP) ";
				statement.execute("CREATE COLUMN TABLE HANA_EXPR_TEST.Y" + columns
						+ "PARTITION BY RANGE(YEAR(D)) (PARTITION 2024 <= VALUES < 2025, PARTITION OTHERS)");
				statement.execute("CREATE COLUMN TABLE HANA_EXPR_TEST.M" + columns
						+ "PARTITION BY RANGE(MONTH(D)) (PARTITION 202401 <= VALUES < 202501, PARTITION OTHERS)");
				statement.execute("CREATE COLUMN TABLE HANA_EXPR_TEST.H" + columns
						+ "PARTITION BY RANGE(HOUR(D)) (PARTITION '2024-01-01 00' <= VALUES < '2024-01-02 00', PARTITION OTHERS)");
				statement.execute(
						"CREATE COLUMN TABLE HANA_EXPR_TEST.K" + columns + "PARTITION BY HASH(YEAR(D)) PARTITIONS 2");
				statement.execute("CREATE COLUMN TABLE HANA_EXPR_TEST.HR" + columns
						+ "PARTITION BY HASH(ID) PARTITIONS 2 ((PARTITION 1) SUBPARTITION BY RANGE(YEAR(D)) (PARTITION 2024 <= VALUES < 2025 PAGE LOADABLE, PARTITION OTHERS), (PARTITION 2) SUBPARTITION BY RANGE(YEAR(D)) (PARTITION 2023 <= VALUES < 2026, PARTITION OTHERS))");
				statement.execute("CREATE COLUMN TABLE HANA_EXPR_TEST.RR" + columns
						+ "PARTITION BY RANGE(YEAR(D)) ((PARTITION 2024 <= VALUES < 2025) SUBPARTITION BY RANGE(MONTH(D2)) (PARTITION 202401 <= VALUES < 202501, PARTITION OTHERS), (PARTITION OTHERS) SUBPARTITION BY RANGE(MONTH(D2)) (PARTITION 202301 <= VALUES < 202601, PARTITION OTHERS))");
				var literal = org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> statement.execute(
						"CREATE COLUMN TABLE HANA_EXPR_TEST.L (ID INTEGER, \"YEAR(D)\" INTEGER, D2 TIMESTAMP) PARTITION BY RANGE(\"YEAR(D)\") (PARTITION 0 <= VALUES < 10, PARTITION OTHERS)"));
				assertEquals(260, literal.getErrorCode());
				statement.execute(
						"CREATE COLUMN TABLE HANA_EXPR_TEST.Q (ID INTEGER, \"A\"\"B\" TIMESTAMP, D2 TIMESTAMP) PARTITION BY RANGE(YEAR(\"A\"\"B\")) (PARTITION 2024 <= VALUES < 2025, PARTITION OTHERS)");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_EXPR_TEST");
				var schema = reader.getAllFull(connection).getFirst();
				assertEquals(7, schema.getTables().size());
				assertTrue(schema.getTables().get("Y").getPartitioning().getPartitioningColumns().getFirst().getName()
						.toUpperCase(java.util.Locale.ROOT).contains("YEAR("));
				var restored = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var table : restored.getTables()) {
					statement.execute("DROP TABLE HANA_EXPR_TEST." + table.getName());
					for (var operation : registry.createSql(table, SqlType.CREATE)) {
						try {
							statement.execute(operation.getSqlText());
						} catch (SQLException e) {
							throw new SQLException(operation.getSqlText(), e);
						}
					}
					for (String date : new String[] { "2024-01-01 12:00:00", "2026-05-10 01:00:00" }) {
						String values = "1, '" + date + "', '" + date + "'";
						statement.execute("INSERT INTO HANA_EXPR_TEST." + table.getName() + " VALUES (" + values + ")");
					}
				}
				var reread = reader.getAllFull(connection).getFirst();
				for (var table : schema.getTables()) {
					var actual = reread.getTables().get(table.getName());
					assertEquals(table.getPartitioning(), actual.getPartitioning(), table.getName());
				}
			} finally {
				statement.execute("DROP SCHEMA HANA_EXPR_TEST CASCADE");
			}
		}
	}

	@Test
	void testSequenceAlterOptionsAndDiffDoNotRestartUnchangedStart() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SEQUENCE HANA_ADV_SEQ");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var registry = dialect.createSqlFactoryRegistry();
			var sequence = new com.sqlapp.data.schemas.Sequence("HANA_ADV_SEQ").setStartValue(10).setIncrementBy(2)
					.setMinValue(1).setMaxValue(100).setCacheSize(5);
			for (var operation : registry.createSql(sequence, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			try {
				try (var rs = statement.executeQuery("SELECT HANA_ADV_SEQ.NEXTVAL FROM DUMMY")) {
					assertTrue(rs.next());
					assertEquals(10, rs.getInt(1));
				}
				var target = sequence.clone().setIncrementBy(3).setCache(false);
				for (var operation : registry.createSql(sequence.diff(target), SqlType.ALTER))
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery("SELECT HANA_ADV_SEQ.NEXTVAL FROM DUMMY")) {
					assertTrue(rs.next());
					assertTrue(rs.getInt(1) > 10);
				}
				target.setStartValue(98).setCycle(true).setCacheSize(0);
				for (var operation : registry.createSql(target, SqlType.ALTER))
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery("SELECT HANA_ADV_SEQ.NEXTVAL FROM DUMMY")) {
					assertTrue(rs.next());
					assertEquals(98, rs.getInt(1));
				}
				try (var rs = statement.executeQuery("SELECT HANA_ADV_SEQ.NEXTVAL FROM DUMMY")) {
					assertTrue(rs.next());
					assertEquals(1, rs.getInt(1));
				}
			} finally {
				statement.execute("DROP SEQUENCE HANA_ADV_SEQ");
			}
		}
	}

	@Test
	void testPlatformFulltextOptionsAndMetadataReplay() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_TEXT_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_TEXT_TEST");
			try {
				statement.execute("CREATE COLUMN TABLE HANA_TEXT_TEST.DOCS (ID INTEGER, BODY NCLOB, LANG NVARCHAR(5))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				var table = new Table("DOCS").setSchemaName("HANA_TEXT_TEST");
				table.getColumns().add("BODY", c -> c.setDataType(DataType.NCLOB));
				var index = new Index("FT_DOCS", table.getColumns().getFirst()).setIndexType(IndexType.FullText);
				table.getIndexes().add(index);
				index.getSpecifics().put("FAST_PREPROCESS", true);
				index.getSpecifics().put("FUZZY_SEARCH_INDEX", true);
				index.getSpecifics().put("SYNC_MODE", "SYNC");
				for (var operation : registry.createSql(index, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO HANA_TEXT_TEST.DOCS VALUES(1, 'hello world', 'EN')");
				try (var rs = statement
						.executeQuery("SELECT COUNT(*) FROM HANA_TEXT_TEST.DOCS WHERE CONTAINS(BODY, 'hello')")) {
					assertTrue(rs.next());
					assertEquals(1, rs.getInt(1));
				}
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_TEXT_TEST");
				var readTable = reader.getAllFull(connection).getFirst().getTables().get("DOCS");
				var readIndex = readTable.getIndexes().get("FT_DOCS");
				assertNotNull(readIndex);
				assertEquals("BODY", readIndex.getColumns().getFirst().getName());
				for (var operation : registry.createSql(readIndex, SqlType.DROP))
					statement.execute(operation.getSqlText());
				for (var operation : registry.createSql(readIndex, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				for (var operation : registry.createSql(readIndex, SqlType.DROP))
					statement.execute(operation.getSqlText());
				index.getSpecifics().put("FAST_PREPROCESS", false);
				index.getSpecifics().put("LANGUAGE_COLUMN", "LANG");
				index.getSpecifics().put("SYNC_MODE", "ASYNC");
				index.getSpecifics().put("FLUSH_EVERY_MINUTES", 1);
				index.getSpecifics().put("FLUSH_AFTER_DOCUMENTS", 2);
				for (var operation : registry.createSql(index, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				var asyncIndex = reader.getAllFull(connection).getFirst().getTables().get("DOCS").getIndexes()
						.get("FT_DOCS");
				assertEquals("ASYNC", asyncIndex.getSpecifics().get("SYNC_MODE"));
				for (var operation : registry.createSql(asyncIndex, SqlType.DROP))
					statement.execute(operation.getSqlText());
				for (var operation : registry.createSql(asyncIndex, SqlType.CREATE))
					statement.execute(operation.getSqlText());
			} finally {
				statement.execute("DROP SCHEMA HANA_TEXT_TEST CASCADE");
			}
		}
	}

	@Test
	void testRoutineSecurityAndDefaultSchemaClauses() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_SECURITY_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_SECURITY_TEST");
			try {
				statement.execute("CREATE COLUMN TABLE HANA_SECURITY_TEST.T (ID INTEGER)");
				statement.execute("INSERT INTO HANA_SECURITY_TEST.T VALUES (42)");
				var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (String security : java.util.List.of("DEFINER", "INVOKER")) {
					var procedure = new com.sqlapp.data.schemas.Procedure("P_" + security)
							.setSchemaName("HANA_SECURITY_TEST");
					procedure.setStatement("LANGUAGE SQLSCRIPT SQL SECURITY " + security
							+ " DEFAULT SCHEMA HANA_SECURITY_TEST READS SQL DATA AS BEGIN SELECT ID FROM T; END");
					for (var operation : registry.createSql(procedure, SqlType.CREATE))
						statement.execute(operation.getSqlText());
					assertTrue(statement.execute("CALL HANA_SECURITY_TEST.P_" + security + "()"));
					try (var rs = statement.getResultSet()) {
						assertTrue(rs.next());
						assertEquals(42, rs.getInt(1));
					}
				}
			} finally {
				statement.execute("DROP SCHEMA HANA_SECURITY_TEST CASCADE");
			}
		}
	}

	@Test
	void testRoleInheritanceAndObjectPrivilegeReplay() throws Exception {
		String base = "HANA_BASE_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10)
				.toUpperCase(java.util.Locale.ROOT);
		String child = base + "_CHILD";
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_ROLE_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_ROLE_TEST");
			statement.execute("CREATE ROLE " + base);
			statement.execute("CREATE ROLE " + child);
			try {
				statement.execute("CREATE COLUMN TABLE HANA_ROLE_TEST.T (ID INTEGER)");
				statement.execute("GRANT SELECT, UPDATE ON HANA_ROLE_TEST.T TO " + base + " WITH GRANT OPTION");
				statement.execute("GRANT SELECT ON SCHEMA HANA_ROLE_TEST TO " + base + " WITH GRANT OPTION");
				statement.execute("GRANT " + base + " TO " + child + " WITH ADMIN OPTION");
				var catalog = DialectResolver.getInstance().getDialect(connection).getCatalogReader();
				var grants = catalog.getObjectPrivilegeReader().getAllFull(connection).stream()
						.filter(p -> base.equals(p.getGranteeName())).toList();
				assertEquals(3, grants.size());
				var membership = catalog.getRoleMemberReader().getAllFull(connection).stream()
						.filter(m -> child.equals(m.getGranteeName())).findFirst().orElseThrow();
				assertTrue(membership.isAdmin());
				var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var grant : grants) {
					for (var operation : registry.createSql(grant, SqlType.REVOKE))
						statement.execute(operation.getSqlText());
					for (var operation : registry.createSql(grant, SqlType.GRANT))
						statement.execute(operation.getSqlText());
				}
				for (var operation : registry.createSql(membership, SqlType.REVOKE))
					statement.execute(operation.getSqlText());
				for (var operation : registry.createSql(membership, SqlType.GRANT))
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery(
						"SELECT COUNT(DISTINCT PRIVILEGE) FROM EFFECTIVE_PRIVILEGES WHERE PRINCIPAL_NAME='" + child
								+ "' AND SCHEMA_NAME='HANA_ROLE_TEST' AND OBJECT_NAME='T' AND PRIVILEGE IN ('SELECT','UPDATE')")) {
					assertTrue(rs.next());
					assertEquals(2, rs.getInt(1));
				}
			} finally {
				drop(statement, "DROP ROLE " + child);
				drop(statement, "DROP ROLE " + base);
				statement.execute("DROP SCHEMA HANA_ROLE_TEST CASCADE");
			}
		}
	}

	@Test
	void testRoutineExecutePrivilegesRoundTrip() throws Exception {
		String role = "HANA_TEST_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
				.toUpperCase(java.util.Locale.ROOT);
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_PRIVILEGE_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_PRIVILEGE_TEST");
			statement.execute("CREATE ROLE " + role);
			try {
				statement.execute(
						"CREATE PROCEDURE HANA_PRIVILEGE_TEST.P() LANGUAGE SQLSCRIPT AS BEGIN SELECT 1 FROM DUMMY; END");
				statement.execute(
						"CREATE FUNCTION HANA_PRIVILEGE_TEST.F() RETURNS RESULT INTEGER LANGUAGE SQLSCRIPT AS BEGIN RESULT = 1; END");
				statement.execute("GRANT EXECUTE ON HANA_PRIVILEGE_TEST.P TO " + role + " WITH GRANT OPTION");
				statement.execute("GRANT EXECUTE ON HANA_PRIVILEGE_TEST.F TO " + role);
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getRoutinePrivilegeReader();
				var grants = reader.getAllFull(connection).stream().filter(p -> role.equals(p.getGranteeName()))
						.toList();
				assertEquals(2, grants.size());
				assertTrue(grants.stream().anyMatch(p -> p.getObjectName().equals("P") && p.isGrantable()));
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var grant : grants) {
					var restored = (com.sqlapp.data.schemas.RoutinePrivilege) com.sqlapp.data.schemas.SchemaUtils
							.readXml(new java.io.StringReader(grant.asXml()));
					for (var operation : registry.createSql(restored, SqlType.REVOKE))
						statement.execute(operation.getSqlText());
					for (var operation : registry.createSql(restored, SqlType.GRANT))
						statement.execute(operation.getSqlText());
				}
				assertEquals(2L,
						reader.getAllFull(connection).stream().filter(p -> role.equals(p.getGranteeName())).count());
				var objects = dialect.getCatalogReader().getObjectPrivilegeReader().getAllFull(connection);
				assertFalse(objects.stream()
						.anyMatch(p -> role.equals(p.getGranteeName()) && "EXECUTE".equals(p.getPrivilege())));
			} finally {
				drop(statement, "DROP ROLE " + role);
				drop(statement, "DROP SCHEMA HANA_PRIVILEGE_TEST CASCADE");
			}
		}
	}

	@Test
	void testTableTypesRoundTripThroughSchemaModel() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_TYPE_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_TYPE_TEST");
			try {
				statement.execute(
						"CREATE TYPE HANA_TYPE_TEST.\"Table Type\" AS TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20))");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var reader = dialect.getCatalogReader().getSchemaReader();
				reader.setSchemaName("HANA_TYPE_TEST");
				var schema = reader.getAllFull(connection).stream().filter(s -> s.getName().equals("HANA_TYPE_TEST"))
						.findFirst().orElseThrow();
				org.junit.jupiter.api.Assertions.assertNull(schema.getTables().get("Table Type"));
				var type = schema.getTypes().get("Table Type");
				assertNotNull(type);
				assertEquals(2, type.getColumns().size());
				var restoredSchema = (com.sqlapp.data.schemas.Schema) com.sqlapp.data.schemas.SchemaUtils
						.readXml(new java.io.StringReader(schema.asXml()));
				var restored = restoredSchema.getTypes().get("Table Type");
				statement.execute("DROP TYPE HANA_TYPE_TEST.\"Table Type\"");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(restored, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				assertTrue(statement.execute(
						"DO BEGIN DECLARE ROWS HANA_TYPE_TEST.\"Table Type\"; ROWS = SELECT 9 AS \"A B\", 'type' AS LABEL FROM DUMMY; SELECT * FROM :ROWS; END"));
				try (var rs = statement.getResultSet()) {
					assertTrue(rs.next());
					assertEquals(9, rs.getInt(1));
				}
			} finally {
				drop(statement, "DROP SCHEMA HANA_TYPE_TEST CASCADE");
			}
		}
	}

	@Test
	void testTableRoutineModelsRecreateWithoutFullDefinitions() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_ROUTINE_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_ROUTINE_TEST");
			try {
				statement.execute(
						"CREATE FUNCTION HANA_ROUTINE_TEST.SCALAR_FN(IN N INTEGER) RETURNS RESULT INTEGER LANGUAGE SQLSCRIPT AS BEGIN RESULT = :N + 1; END");
				statement.execute(
						"CREATE FUNCTION HANA_ROUTINE_TEST.TABLE_FN (IN SRC TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20))) "
								+ "RETURNS TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20)) LANGUAGE SQLSCRIPT AS BEGIN RETURN SELECT * FROM :SRC; END");
				statement.execute(
						"CREATE PROCEDURE HANA_ROUTINE_TEST.TABLE_PROC (IN SRC TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20)), "
								+ "OUT DEST TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20))) LANGUAGE SQLSCRIPT AS BEGIN DEST = SELECT * FROM :SRC; END");
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var schemaReader = dialect.getCatalogReader().getSchemaReader();
				schemaReader.setSchemaName("HANA_ROUTINE_TEST");
				var schema = schemaReader.getAllFull(connection).stream()
						.filter(s -> s.getName().equals("HANA_ROUTINE_TEST")).findFirst().orElseThrow();
				var function = schema.getFunctions().get("TABLE_FN");
				assertNotNull(function.getReturning().getTable());
				assertEquals(2, function.getReturning().getTable().getColumns().size());
				assertEquals("A B", function.getReturning().getTable().getColumns().get(0).getName());
				assertTrue(function.getArguments().get("SRC").getDataTypeName().startsWith("TABLE("));
				var procedure = schema.getProcedures().get("TABLE_PROC");
				var scalar = schema.getFunctions().get("SCALAR_FN");
				assertEquals(DataType.INT, scalar.getReturning().getDataType());
				scalar.setDefinition((String) null);
				scalar.setStatement("BEGIN RESULT = :N + 1; END");
				assertEquals(2, procedure.getArguments().size());
				assertTrue(procedure.getArguments().get("DEST").getDataTypeName().contains("NVARCHAR(20)"));
				function.setDefinition((String) null);
				function.setStatement("BEGIN RETURN SELECT * FROM :SRC; END");
				procedure.setDefinition((String) null);
				procedure.setStatement("BEGIN DEST = SELECT * FROM :SRC; END");
				statement.execute("DROP FUNCTION HANA_ROUTINE_TEST.TABLE_FN");
				statement.execute("DROP PROCEDURE HANA_ROUTINE_TEST.TABLE_PROC");
				statement.execute("DROP FUNCTION HANA_ROUTINE_TEST.SCALAR_FN");
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				for (var operation : registry.createSql(scalar, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery("SELECT HANA_ROUTINE_TEST.SCALAR_FN(10) FROM DUMMY")) {
					assertTrue(rs.next());
					assertEquals(11, rs.getInt(1));
				}
				for (var operation : registry.createSql(function, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				for (var operation : registry.createSql(procedure, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				assertTrue(statement.execute("DO BEGIN DECLARE SRC TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20)); "
						+ "SRC = SELECT 7 AS \"A B\", 'ok' AS LABEL FROM DUMMY; SELECT * FROM HANA_ROUTINE_TEST.TABLE_FN(:SRC); END"));
				try (var rs = statement.getResultSet()) {
					assertTrue(rs.next());
					assertEquals(7, rs.getInt(1));
					assertEquals("ok", rs.getString(2));
				}
				assertTrue(statement.execute("DO BEGIN DECLARE SRC TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20)); "
						+ "DECLARE DEST TABLE (\"A B\" INTEGER, LABEL NVARCHAR(20)); SRC = SELECT 8 AS \"A B\", 'out' AS LABEL FROM DUMMY; "
						+ "CALL HANA_ROUTINE_TEST.TABLE_PROC(:SRC, DEST); SELECT * FROM :DEST; END"));
				try (var rs = statement.getResultSet()) {
					assertTrue(rs.next());
					assertEquals(8, rs.getInt(1));
				}
			} finally {
				statement.execute("DROP SCHEMA HANA_ROUTINE_TEST CASCADE");
			}
		}
	}

	@Test
	void testNativeIdentityAndGeneratedColumnAlterBoundaries() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP TABLE HANA_ALTER_BOUNDARY");
			statement.execute("CREATE COLUMN TABLE HANA_ALTER_BOUNDARY (ID BIGINT GENERATED ALWAYS AS IDENTITY, "
					+ "N INTEGER, TOTAL INTEGER GENERATED ALWAYS AS (N * 2))");
			try {
				org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> statement.execute(
						"ALTER TABLE HANA_ALTER_BOUNDARY ALTER (ID BIGINT GENERATED ALWAYS AS IDENTITY (INCREMENT BY 2))"));
				org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> statement
						.execute("ALTER TABLE HANA_ALTER_BOUNDARY ALTER (TOTAL INTEGER GENERATED ALWAYS AS (N * 3))"));
			} finally {
				statement.execute("DROP TABLE HANA_ALTER_BOUNDARY");
			}
		}
	}

	@Test
	void testEnhancedDdlRoundTripsOnHana2() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			drop(statement, "DROP SCHEMA HANA_DDL_TEST CASCADE");
			statement.execute("CREATE SCHEMA HANA_DDL_TEST");
			try {
				var dialect = DialectResolver.getInstance().getDialect(connection);
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(true);
				Table table = new Table("ITEMS").setSchemaName("HANA_DDL_TEST").setDialect(dialect);
				table.getColumns().add("ID",
						c -> c.setDataType(DataType.BIGINT).setIdentity(true)
								.setIdentityGenerationType(IdentityGenerationType.ByDefault).setIdentityStartValue(10)
								.setIdentityStep(2).setIdentityCacheSize(20));
				table.getColumns().add("QTY", c -> c.setDataType(DataType.INT));
				table.getColumns().add("PRICE", c -> c.setDataType(DataType.INT));
				table.getColumns().add("TOTAL", c -> c.setDataType(DataType.INT).setFormula("QTY * PRICE"));
				table.getColumns().add("TINYVAL", c -> c.setDataType(DataType.UTINYINT));
				for (var operation : registry.createSql(table, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				statement.execute("INSERT INTO HANA_DDL_TEST.ITEMS (QTY, PRICE, TINYVAL) VALUES (2, 3, 255)");
				try (var rs = statement.executeQuery("SELECT ID, TOTAL, TINYVAL FROM HANA_DDL_TEST.ITEMS")) {
					assertTrue(rs.next());
					assertEquals(10L, rs.getLong(1));
					assertEquals(6, rs.getInt(2));
					assertEquals(255, rs.getInt(3));
				}
				var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
				reader.setSchemaName("HANA_DDL_TEST");
				reader.setObjectName("ITEMS");
				var read = reader.getAllFull(connection).getFirst();
				assertNotNull(read.getColumns().get("TOTAL").getFormula());
				assertEquals(DataType.UTINYINT, read.getColumns().get("TINYVAL").getDataType());
				assertEquals(IdentityGenerationType.ByDefault, read.getColumns().get("ID").getIdentityGenerationType());

				Table added = table.clone();
				added.getColumns().add("LABEL", c -> c.setDataType(DataType.NVARCHAR).setLength(20));
				for (var operation : registry.createSql(table.diff(added), SqlType.ALTER))
					statement.execute(operation.getSqlText());
				Table altered = added.clone();
				altered.getColumns().get("LABEL").setLength(40).setDefaultValue("'new'").setNullable(false);
				statement.execute("UPDATE HANA_DDL_TEST.ITEMS SET LABEL = 'old'");
				for (var operation : registry.createSql(added.diff(altered), SqlType.ALTER))
					statement.execute(operation.getSqlText());
				Table renamed = altered.clone();
				renamed.getColumns().get("LABEL").setName("NEW_LABEL");
				for (var operation : registry.createSql(altered.diff(renamed), SqlType.ALTER))
					statement.execute(operation.getSqlText());
				Table removed = renamed.clone();
				removed.getColumns().remove("NEW_LABEL");
				for (var operation : registry.createSql(renamed.diff(removed), SqlType.ALTER))
					statement.execute(operation.getSqlText());
				Index index = new Index("IX_ITEMS_PRICE", removed.getColumns().get("PRICE"),
						removed.getColumns().get("QTY")).setIndexType(IndexType.InvertedValue);
				removed.getIndexes().add(index);
				for (var operation : registry.createSql(index, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				Synonym synonym = new Synonym("ITEM_ALIAS").setSchemaName("HANA_DDL_TEST")
						.setObjectSchemaName("HANA_DDL_TEST").setObjectName("ITEMS");
				for (var operation : registry.createSql(synonym, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				try (var rs = statement.executeQuery("SELECT TOTAL FROM HANA_DDL_TEST.ITEM_ALIAS")) {
					assertTrue(rs.next());
					assertEquals(6, rs.getInt(1));
				}
			} finally {
				statement.execute("DROP SCHEMA HANA_DDL_TEST CASCADE");
			}
		}
	}

	private void createObjects(final Statement statement) throws SQLException {
		drop(statement, "DROP SCHEMA METADATA_TEST CASCADE");
		statement.execute("CREATE SCHEMA METADATA_TEST");
		statement.execute(
				"CREATE SEQUENCE METADATA_TEST.METADATA_SEQ START WITH 50 INCREMENT BY 10 MINVALUE 10 MAXVALUE 1000 CYCLE");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_PARTITIONED (
				 ID BIGINT NOT NULL, BUCKET INTEGER NOT NULL, PAYLOAD NVARCHAR(100))
				 PARTITION BY RANGE (BUCKET) (
				  PARTITION 0 <= VALUES < 100,
				  PARTITION 100 <= VALUES < 200,
				  PARTITION OTHERS)
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_MULTI_PARTITIONED (
				 ID BIGINT NOT NULL, BUCKET INTEGER NOT NULL,
				 PAYLOAD NVARCHAR(100))
				 PARTITION BY HASH (ID) PARTITIONS 2,
				 RANGE (BUCKET) (
				  PARTITION 0 <= VALUES < 100,
				  PARTITION 100 <= VALUES < 200,
				  PARTITION OTHERS)
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_DOCUMENTS (
				 ID BIGINT NOT NULL PRIMARY KEY, CONTENT NCLOB)
				""");
		statement.execute("""
				CREATE FULLTEXT INDEX IDX_METADATA_DOCUMENTS_TEXT
				 ON METADATA_TEST.METADATA_DOCUMENTS(CONTENT)
				 FAST PREPROCESS OFF
				 FUZZY SEARCH INDEX OFF
				 SEARCH ONLY ON
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_PARENT (
				 ID BIGINT NOT NULL, REGION NVARCHAR(2) NOT NULL,
				 NAME NVARCHAR(100),
				 CONSTRAINT PK_METADATA_PARENT PRIMARY KEY (ID, REGION))
				""");
		statement.execute("COMMENT ON TABLE METADATA_TEST.METADATA_PARENT IS 'Metadata parent table'");
		statement.execute("COMMENT ON COLUMN METADATA_TEST.METADATA_PARENT.NAME IS 'Display name'");
		statement.execute("""
				CREATE ROW TABLE METADATA_TEST.METADATA_ROW_STORE (
				 ID BIGINT NOT NULL PRIMARY KEY, VALUE NVARCHAR(100))
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_CHILD (
				 ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
				 PARENT_ID BIGINT NOT NULL, PARENT_REGION NVARCHAR(2) NOT NULL,
				 CODE NVARCHAR(40) DEFAULT 'unknown' NOT NULL,
				 AMOUNT DECIMAL(18,2) NOT NULL,
				 CONSTRAINT UK_METADATA_CHILD_CODE UNIQUE (CODE),
				 CONSTRAINT CK_METADATA_CHILD_AMOUNT CHECK (AMOUNT >= 0),
				 CONSTRAINT FK_METADATA_CHILD_PARENT
				 FOREIGN KEY (PARENT_ID, PARENT_REGION)
				 REFERENCES METADATA_TEST.METADATA_PARENT(ID, REGION))
				""");
		statement.execute("""
				CREATE INDEX IDX_METADATA_CHILD_PARENT
				 ON METADATA_TEST.METADATA_CHILD(PARENT_ID, PARENT_REGION)
				""");
		statement.execute("""
				CREATE VIEW METADATA_TEST.METADATA_VIEW AS
				 SELECT ID, CODE, AMOUNT FROM METADATA_TEST.METADATA_CHILD
				""");
		statement.execute("""
				CREATE SYNONYM METADATA_TEST.METADATA_PARENT_SYNONYM
				 FOR METADATA_TEST.METADATA_PARENT
				""");
		statement.execute("""
				CREATE PROCEDURE METADATA_TEST.METADATA_PROCEDURE (
				 IN P_ID BIGINT, OUT P_NAME NVARCHAR(100))
				 LANGUAGE SQLSCRIPT AS
				 BEGIN
				   SELECT NAME INTO P_NAME FROM METADATA_TEST.METADATA_PARENT
				    WHERE ID = P_ID;
				 END
				""");
		statement.execute("""
				CREATE FUNCTION METADATA_TEST.METADATA_FUNCTION (
				 IN P_AMOUNT DECIMAL(18,2))
				 RETURNS RESULT DECIMAL(18,2)
				 LANGUAGE SQLSCRIPT AS
				 BEGIN
				   RESULT := :P_AMOUNT * 2;
				 END
				""");
		statement.execute("""
				CREATE COLUMN TABLE METADATA_TEST.METADATA_AUDIT (
				 CHILD_ID BIGINT NOT NULL)
				""");
		statement.execute("""
				CREATE TRIGGER METADATA_TEST.METADATA_TRIGGER
				 AFTER INSERT ON METADATA_TEST.METADATA_CHILD
				 REFERENCING NEW ROW NEW_ROW
				 FOR EACH ROW
				 BEGIN
				   INSERT INTO METADATA_TEST.METADATA_AUDIT
				    VALUES (:NEW_ROW.ID);
				 END
				""");
	}

	private void drop(final Statement statement, final String sql) {
		try {
			statement.execute(sql);
		} catch (SQLException e) {
			// The disposable test database may not contain the schema yet.
		}
	}

	private void createSecurityObjects(final Statement statement) throws SQLException {
		drop(statement, "DROP USER METADATA_USER CASCADE");
		drop(statement, "DROP ROLE METADATA_ROLE");
		statement.execute("CREATE USER METADATA_USER PASSWORD HxeTest9xB NO FORCE_FIRST_PASSWORD_CHANGE");
		statement.execute("CREATE ROLE METADATA_ROLE");
		statement.execute("GRANT METADATA_ROLE TO METADATA_USER WITH ADMIN OPTION");
		statement.execute("GRANT SELECT ON METADATA_TEST.METADATA_PARENT TO METADATA_ROLE WITH GRANT OPTION");
	}

	private void cleanupSecurityObjects(final Statement statement) {
		drop(statement, "DROP USER METADATA_USER CASCADE");
		drop(statement, "DROP ROLE METADATA_ROLE");
	}

	private static Path createPasswordDirectory() {
		try {
			Path directory = Files.createTempDirectory("sqlapp-hana-metadata-");
			Files.writeString(directory.resolve("password.json"), PASSWORD_JSON, StandardCharsets.UTF_8);
			return directory;
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}

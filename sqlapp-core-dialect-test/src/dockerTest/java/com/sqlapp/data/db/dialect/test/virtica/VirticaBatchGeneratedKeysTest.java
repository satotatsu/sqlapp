/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.virtica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SqlSecurity;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Vertica 25.1 JDBC generated-key behavior probe. */
class VirticaBatchGeneratedKeysTest {
	private static final GenericContainer<?> VERTICA = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("ratiopbc/vertica-ce:v25.1.0-0"))
					.withExposedPorts(5433)
					.waitingFor(Wait.forLogMessage(".*Vertica is now running.*\\n", 1)
							.withStartupTimeout(Duration.ofMinutes(3))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(VERTICA);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(VERTICA);
	}

	@Test
	void metadataReaderLoadsTablesConstraintsViewAndSequence() throws Exception {
		try (Connection connection = createConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DROP PROCEDURE IF EXISTS metadata_procedure(INT, VARCHAR)");
			statement.execute("CREATE PROCEDURE metadata_procedure(IN input_value INT, message_value VARCHAR) "
					+ "LANGUAGE PLvSQL SECURITY INVOKER AS $$ BEGIN "
					+ "RAISE NOTICE 'value = %, message = %', input_value, message_value; END; $$");
			statement.execute("DROP FUNCTION IF EXISTS metadata_zero_if_null(INT)");
			statement.execute("DROP FUNCTION IF EXISTS metadata_zero_if_null(NUMERIC)");
			statement.execute("CREATE FUNCTION metadata_zero_if_null(input_value INT) RETURN INT "
					+ "AS BEGIN RETURN (CASE WHEN input_value IS NULL THEN 0 ELSE input_value END); END");
			statement.execute("CREATE FUNCTION metadata_zero_if_null(decimal_value NUMERIC) RETURN NUMERIC "
					+ "AS BEGIN RETURN (CASE WHEN decimal_value IS NULL THEN 0 ELSE decimal_value END); END");
			statement.execute("DROP USER IF EXISTS metadata_user");
			statement.execute("DROP ROLE IF EXISTS metadata_role");
			statement.execute("CREATE ROLE metadata_role");
			statement.execute("CREATE USER metadata_user");
			statement.execute("GRANT metadata_role TO metadata_user");
			statement.execute("DROP VIEW IF EXISTS metadata_view");
			statement.execute("DROP TABLE IF EXISTS metadata_child");
			statement.execute("DROP TABLE IF EXISTS metadata_table");
			statement.execute("DROP SEQUENCE IF EXISTS metadata_sequence");
			statement.execute("CREATE SEQUENCE metadata_sequence START 100 INCREMENT 5 "
					+ "MINVALUE 10 MAXVALUE 1000 CYCLE CACHE 7");
			statement.execute("CREATE TABLE metadata_table (id BIGINT NOT NULL, identity_id IDENTITY(100, 5, 7) NOT NULL, "
					+ "code VARCHAR(30) DEFAULT 'unknown', CONSTRAINT pk_metadata_table PRIMARY KEY (id) ENABLED, "
					+ "CONSTRAINT uk_metadata_table_code UNIQUE (code) ENABLED, "
					+ "CONSTRAINT ck_metadata_table_code CHECK (code <> '')) PARTITION BY id");
			statement.execute("CREATE TABLE metadata_child (id BIGINT NOT NULL, parent_id BIGINT NOT NULL, "
					+ "CONSTRAINT pk_metadata_child PRIMARY KEY (id) ENABLED, "
					+ "CONSTRAINT fk_metadata_child_parent FOREIGN KEY (parent_id) "
					+ "REFERENCES metadata_table(id))");
			statement.execute("GRANT SELECT, UPDATE ON metadata_table TO metadata_user WITH GRANT OPTION");
			statement.execute("CREATE VIEW metadata_view AS SELECT id, code FROM metadata_table");
			statement.execute("COMMENT ON TABLE metadata_table IS 'metadata table comment'");
			statement.execute("COMMENT ON COLUMN metadata_table.code IS 'metadata code comment'");
			statement.execute("COMMENT ON SEQUENCE metadata_sequence IS 'metadata sequence comment'");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var settingReader = dialect.getCatalogReader().getSettingReader();
			settingReader.setObjectName("EnableDataCollector");
			var settings = settingReader.getAllFull(connection);
			assertEquals(1, settings.size());
			assertNotNull(settings.get(0).getValue());
			assertNotNull(settings.get(0).getDefaultValue());
			var roleReader = dialect.getCatalogReader().getRoleReader();
			roleReader.setObjectName("metadata_role");
			var roles = roleReader.getAllFull(connection);
			assertEquals(1, roles.size());
			assertEquals("metadata_role", roles.get(0).getName());
			assertNotNull(roles.get(0).getId());
			var userReader = dialect.getCatalogReader().getUserReader();
			userReader.setObjectName("metadata_user");
			var users = userReader.getAllFull(connection);
			assertEquals(1, users.size());
			assertEquals("metadata_user", users.get(0).getName());
			assertNotNull(users.get(0).getId());
			assertFalse(users.get(0).isAdmin());
			assertTrue(users.get(0).getSpecifics().get("ALL_ROLES")
					.toLowerCase().contains("metadata_role"));
			var roleMemberReader = dialect.getCatalogReader().getRoleMemberReader();
			roleMemberReader.setGrantee("metadata_user");
			var roleMembers = roleMemberReader.getAllFull(connection);
			assertTrue(roleMembers.stream().anyMatch(member ->
					"metadata_role".equals(member.getMemberRoleName())
							&& "metadata_user".equals(member.getGranteeName())));
			var privilegeReader = dialect.getCatalogReader().getObjectPrivilegeReader();
			privilegeReader.setObjectName("metadata_table");
			var privileges = privilegeReader.getAllFull(connection);
			assertTrue(privileges.stream().anyMatch(privilege ->
					"metadata_user".equals(privilege.getGranteeName())
							&& "SELECT".equals(privilege.getPrivilege())
							&& privilege.isGrantable()));
			assertTrue(privileges.stream().anyMatch(privilege ->
					"metadata_user".equals(privilege.getGranteeName())
							&& "UPDATE".equals(privilege.getPrivilege())
							&& privilege.isGrantable()));
			var schema = dialect.getCatalogReader().getSchemaReader().getAllFull(connection)
					.stream().filter(s -> s.getTables().stream().anyMatch(
							t -> "metadata_table".equalsIgnoreCase(t.getName())))
					.findFirst().orElseThrow();
			var table = schema.getTables().stream()
					.filter(t -> "metadata_table".equalsIgnoreCase(t.getName()))
					.findFirst().orElseThrow();
			assertTrue(table.getColumns().get("id") != null);
			assertTrue(table.getColumns().get("identity_id").isIdentity());
			assertEquals(5L, table.getColumns().get("identity_id").getIdentityStep().longValue());
			assertEquals(7, table.getColumns().get("identity_id").getIdentityCacheSize().intValue());
			assertNotNull(table.getColumns().get("identity_id").getSequenceName());
			assertEquals("'unknown'", table.getColumns().get("code").getDefaultValue());
			assertEquals("metadata table comment", table.getRemarks());
			assertEquals("metadata code comment", table.getColumns().get("code").getRemarks());
			var primary = table.getConstraints().get("pk_metadata_table");
			assertNotNull(primary);
			assertEquals("id", ((UniqueConstraint) primary).getColumns().get(0).getName());
			var unique = table.getConstraints().get("uk_metadata_table_code");
			assertNotNull(unique);
			assertEquals("code", ((UniqueConstraint) unique)
					.getColumns().get(0).getName());
			var check = table.getConstraints().stream()
					.filter(CheckConstraint.class::isInstance)
					.map(CheckConstraint.class::cast).findFirst().orElseThrow();
			assertEquals("ck_metadata_table_code", check.getName());
			assertTrue(check.isEnable());
			assertTrue(table.getSpecifics().get("PARTITION_EXPRESSION")
					.toLowerCase().contains("id"));
			var child = schema.getTables().stream()
					.filter(t -> "metadata_child".equalsIgnoreCase(t.getName()))
					.findFirst().orElseThrow();
			var foreignKey = child.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			var view = schema.getViews().stream().filter(
					v -> "metadata_view".equalsIgnoreCase(v.getName()))
					.findFirst().orElseThrow();
			assertTrue(view.getStatement().toString().toLowerCase()
					.contains("metadata_table"));
			var sequence = schema.getSequences().stream().filter(
					s -> "metadata_sequence".equalsIgnoreCase(s.getName()))
					.findFirst().orElseThrow();
			assertEquals(10L, sequence.getMinValue().longValue());
			assertEquals(1000L, sequence.getMaxValue().longValue());
			assertEquals(5L, sequence.getIncrementBy().longValue());
			assertEquals(7L, sequence.getCacheSize().longValue());
			assertTrue(sequence.isCycle());
			assertEquals("metadata sequence comment", sequence.getRemarks());
			var functions = schema.getFunctions().stream().filter(
					f -> "metadata_zero_if_null".equalsIgnoreCase(f.getName()))
					.toList();
			assertEquals(2, functions.size());
			var function = functions.stream().filter(f -> f.getArguments().get(0)
					.getDataType() == DataType.BIGINT).findFirst().orElseThrow();
			assertEquals(1, function.getArguments().size());
			assertEquals("input_value", function.getArguments().get(0).getName());
			assertEquals(DataType.BIGINT, function.getArguments().get(0).getDataType());
			assertEquals(DataType.BIGINT, function.getReturning().getDataType());
			assertTrue(function.getDefinition().stream().anyMatch(
					line -> line.toLowerCase().contains("input_value")));
			var procedure = schema.getProcedures().stream().filter(
					p -> "metadata_procedure".equalsIgnoreCase(p.getName()))
					.findFirst().orElseThrow();
			assertEquals(2, procedure.getArguments().size());
			assertEquals("input_value", procedure.getArguments().get(0).getName());
			assertEquals(DataType.BIGINT, procedure.getArguments().get(0).getDataType());
			assertEquals("message_value", procedure.getArguments().get(1).getName());
			assertEquals(DataType.VARCHAR, procedure.getArguments().get(1).getDataType());
			assertTrue(procedure.getLanguage().toLowerCase().contains("pl/vsql"));
			assertEquals(SqlSecurity.Invoker, procedure.getSqlSecurity());
			assertEquals("dbadmin", procedure.getSpecifics().get("OWNER"));
		}
	}

	@Test
	void jdbcGeneratedKeysAreUnsupported() throws Exception {
		try (Connection connection = createConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_key_probe");
			statement.execute("CREATE TABLE sqlapp_key_probe (id IDENTITY, txt VARCHAR(30))");
			assertThrows(java.sql.SQLFeatureNotSupportedException.class, () -> connection.prepareStatement(
					"INSERT INTO sqlapp_key_probe(txt) VALUES (?)", Statement.RETURN_GENERATED_KEYS));
		}
	}

	@Test
	void explicitSequenceRemainsAlignedAndPreparedStatementsAreReused() throws Exception {
		try (Connection connection = createConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE IF EXISTS sqlapp_child");
			statement.execute("DROP TABLE IF EXISTS sqlapp_parent");
			statement.execute("DROP SEQUENCE IF EXISTS sqlapp_parent_seq");
			statement.execute("CREATE SEQUENCE sqlapp_parent_seq START 1 INCREMENT 1 CACHE 1");
			statement.execute("CREATE TABLE sqlapp_parent (id BIGINT NOT NULL, txt VARCHAR(30))");
			statement.execute("CREATE TABLE sqlapp_child (id BIGINT NOT NULL, parent_id BIGINT NOT NULL, txt VARCHAR(30))");
			connection.setAutoCommit(false);

			Table[] tables = createSchemaModel(connection);
			Table parent = tables[0];
			Table child = tables[1];
			Set<PreparedStatement> statements = Collections.newSetFromMap(new IdentityHashMap<>());
			AtomicInteger executions = new AtomicInteger();
			try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, parent, child)) {
				session.setRootBatchSize(2);
				session.setTableOperationMode(TableOperationMode.INSERT);
				session.setPreparedStatementBeforeExecuteHandler(prepared -> {
					statements.add(prepared);
					executions.incrementAndGet();
				});
				for (int i = 1; i <= 6; i++) {
					Row parentRow = session.newRow(parent);
					parentRow.put("txt", "parent-" + i);
					Row childRow = session.newRow(child);
					childRow.put("id", (long) i);
					childRow.put("txt", "child-" + i);
				}
			}

			assertEquals(2, statements.size());
			assertEquals(6, executions.get());
			try (ResultSet resultSet = statement.executeQuery("""
					SELECT p.id, p.txt, c.parent_id, c.txt
					FROM sqlapp_parent p JOIN sqlapp_child c ON c.parent_id=p.id
					ORDER BY p.id
					""")) {
				for (int i = 1; i <= 6; i++) {
					assertTrue(resultSet.next());
					assertEquals((long) i, resultSet.getLong(1));
					assertEquals(resultSet.getLong(1), resultSet.getLong(3));
					assertEquals("parent-" + i, resultSet.getString(2));
					assertEquals("child-" + i, resultSet.getString(4));
				}
				assertFalse(resultSet.next());
			}
		}
	}

	private Connection createConnection() throws Exception {
		String url = "jdbc:vertica://localhost:" + VERTICA.getMappedPort(5433) + "/VMart";
		return DriverManager.getConnection(url, "dbadmin", "");
	}

	private Table[] createSchemaModel(final Connection connection) throws Exception {
		Dialect dialect = DialectResolver.getInstance().getDialect(connection);
		String databaseProductName = connection.getMetaData().getDatabaseProductName();
		assertTrue(dialect.supportsSequencePreallocation(),
				() -> "Unexpected dialect for " + databaseProductName
						+ ": " + dialect.getClass().getName());
		Table parent = new Table("sqlapp_parent");
		parent.setDialect(dialect);
		parent.getColumns().add(new Column("id").setDataType(DataType.BIGINT)
				.setSequenceName("sqlapp_parent_seq"));
		parent.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(30));
		parent.setPrimaryKey(parent.getColumns().get("id"));
		Table child = new Table("sqlapp_child");
		child.setDialect(dialect);
		child.getColumns().add(new Column("id").setDataType(DataType.BIGINT));
		child.getColumns().add(new Column("parent_id").setDataType(DataType.BIGINT));
		child.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(30));
		child.setPrimaryKey(child.getColumns().get("id"));
		child.getConstraints().addForeignKeyConstraint("fk_sqlapp_child_parent",
				child.getColumns().get("parent_id"), parent.getColumns().get("id"));
		return new Table[] { parent, child };
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-dialect-test.
 */
package com.sqlapp.data.db.dialect.test.sybase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession;
import com.sqlapp.jdbc.sql.JdbcTreeDataSession.TableOperationMode;

/** Sybase ASE 16.0 SP03 identity behavior with the jTDS driver. */
class SybaseJdbcTreeDataSessionTest {
	private static final GenericContainer<?> ASE = ReusableTestcontainers.configure(
			new GenericContainer<>(DockerImageName.parse("blieusong/ase-server:latest"))
					.withCreateContainerCmdModifier(command -> command.withHostName("ase-server")
							.withEntrypoint("/home/sybase/bin/entrypoint.sh")
							.withWorkingDir("/home/sybase"))
					.withExposedPorts(5000)
					.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(4))));

	@BeforeAll
	static void startContainer() {
		ReusableTestcontainers.start(ASE);
	}

	@AfterAll
	static void stopContainer() {
		ReusableTestcontainers.stop(ASE);
	}

	@Test
	void metadataReaderLoadsIdentityPrimaryKeyIndexAndForeignKey() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				"jdbc:jtds:sybase://localhost:" + ASE.getMappedPort(5000)
						+ "/master", "sa", "sybase");
				Statement statement = connection.createStatement()) {
			try {
				statement.execute("DROP PROCEDURE metadata_procedure");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the procedure yet.
			}
			try {
				statement.execute("DROP FUNCTION metadata_function");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the function yet.
			}
			try {
				statement.execute("sp_dropserver 'metadata_remote'");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the remote server yet.
			}
			statement.execute("sp_addserver 'metadata_remote', 'ASEnterprise', 'metadata_network'");
			try {
				statement.execute("DROP TRIGGER metadata_trigger");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the trigger yet.
			}
			try {
				statement.execute("DROP VIEW metadata_view");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the view yet.
			}
			try {
				statement.execute("DROP TABLE metadata_table");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the table yet.
			}
			try {
				statement.execute("sp_droptype metadata_code");
			} catch (SQLException ignored) {
				// The isolated test container may not contain the type yet.
			}
			statement.execute("sp_addtype metadata_code, 'varchar(30)', 'not null'");
			statement.execute("CREATE TABLE metadata_table (id INT IDENTITY NOT NULL, "
					+ "parent_id INT NULL, code metadata_code DEFAULT 'unknown', "
					+ "normalized_code COMPUTE upper(code), "
					+ "CONSTRAINT pk_metadata_table PRIMARY KEY (id), "
					+ "CONSTRAINT uk_metadata_table_code UNIQUE (code), "
					+ "CONSTRAINT ck_metadata_table_code CHECK (code <> ''), "
					+ "CONSTRAINT fk_metadata_table_parent FOREIGN KEY (parent_id) "
					+ "REFERENCES metadata_table(id))");
			statement.execute("CREATE INDEX idx_metadata_table_code ON metadata_table(code)");
			statement.execute("sp_chgattribute 'metadata_table.idx_metadata_table_code', "
					+ "'fillfactor', 70");
			statement.execute("CREATE INDEX idx_metadata_table_code_desc ON metadata_table(code DESC)");
			statement.execute("CREATE TRIGGER metadata_trigger ON metadata_table FOR INSERT, UPDATE AS "
					+ "SELECT id FROM inserted");
			statement.execute("CREATE VIEW metadata_view AS SELECT id, code FROM metadata_table");
			statement.execute("CREATE PROCEDURE metadata_procedure @p_id INT AS "
					+ "SELECT code FROM metadata_table WHERE id = @p_id");
			statement.execute("CREATE FUNCTION metadata_function(@p_id INT) RETURNS INT AS "
					+ "BEGIN RETURN @p_id * 2 END");
			String functionObjectType;
			try (var resultSet = statement.executeQuery("SELECT o.type, COUNT(c.colid) "
					+ "FROM sysobjects o LEFT JOIN syscomments c ON o.id = c.id "
					+ "WHERE o.name = 'metadata_function' GROUP BY o.type")) {
				assertTrue(resultSet.next());
				functionObjectType = resultSet.getString(1).trim();
				int functionTextRows = resultSet.getInt(2);
				assertEquals("SF", functionObjectType);
				assertTrue(functionTextRows > 0, "type=" + functionObjectType);
			}
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var settings = dialect.getCatalogReader().getSettingReader().getAllFull(connection);
			assertTrue(settings.size() > 0);
			var connectionSetting = settings.stream()
					.filter(setting -> "number of user connections".equals(setting.getName()))
					.findFirst().orElseThrow();
			assertNotNull(connectionSetting.getId());
			assertNotNull(connectionSetting.getValue());
			assertNotNull(connectionSetting.getDisplayValue());
			var tableSpaces = dialect.getCatalogReader().getTableSpaceReader()
					.getAllFull(connection);
			var defaultSegment = tableSpaces.stream()
					.filter(tableSpace -> "default".equals(tableSpace.getName()))
					.findFirst().orElseThrow();
			assertTrue(defaultSegment.getTableSpaceFiles().size() > 0);
			assertNotNull(defaultSegment.getTableSpaceFiles().get(0).getFilePath());
			var roleReader = dialect.getCatalogReader().getRoleReader();
			roleReader.setObjectName("sa_role");
			var roles = roleReader.getAllFull(connection);
			assertEquals(1, roles.size());
			assertEquals("sa_role", roles.get(0).getName());
			assertNotNull(roles.get(0).getId());
			var userReader = dialect.getCatalogReader().getUserReader();
			userReader.setObjectName("dbo");
			var users = userReader.getAllFull(connection);
			assertEquals(1, users.size());
			assertEquals("dbo", users.get(0).getName());
			assertEquals("sa", users.get(0).getLoginUserName());
			assertNotNull(users.get(0).getId());
			var dbLinkReader = dialect.getCatalogReader().getPublicDbLinkReader();
			dbLinkReader.setObjectName("metadata_remote");
			var dbLinks = dbLinkReader.getAllFull(connection);
			assertEquals(1, dbLinks.size());
			assertEquals("metadata_remote", dbLinks.get(0).getName());
			assertEquals("metadata_network", dbLinks.get(0).getDataSource());
			var roleMemberReader = dialect.getCatalogReader().getRoleMemberReader();
			roleMemberReader.setGrantee("sa");
			var roleMembers = roleMemberReader.getAllFull(connection);
			assertTrue(roleMembers.stream().anyMatch(member -> "sa_role".equals(member.getMemberRoleName())));
			var schema = SchemaUtils.getSchema(connection, "dbo", "metadata_table", "metadata_view",
					"metadata_procedure", "metadata_function", "metadata_code", "metadata_trigger")
					.orElseThrow();
			var domain = schema.getDomains().get("metadata_code");
			assertNotNull(domain);
			assertEquals(DataType.VARCHAR, domain.getDataType());
			assertEquals(30L, domain.getLength().longValue());
			assertTrue(domain.isNotNull());
			var table = schema.getTables().get("metadata_table");
			assertNotNull(table);
			assertEquals(Boolean.TRUE,
					table.getSpecifics().get("has_clustered_index", Boolean.class));
			assertTrue(table.getColumns().get("id").isIdentity());
			assertTrue(table.getColumns().get("code").getDefaultValue()
					.contains("unknown"));
			assertTrue(table.getColumns().get("normalized_code").getFormula()
					.toLowerCase().contains("upper"));
			assertEquals("id", table.getConstraints().getPrimaryKeyConstraint()
					.getColumns().get(0).getName());
			var unique = assertInstanceOf(UniqueConstraint.class,
					table.getConstraints().get("uk_metadata_table_code"));
			assertEquals("code", unique.getColumns().get(0).getName());
			var check = assertInstanceOf(CheckConstraint.class,
					table.getConstraints().get("ck_metadata_table_code"));
			assertTrue(check.getExpression().replaceAll("\\s+", "")
					.contains("code<>''"), check::getExpression);
			var codeIndex = table.getIndexes().get("idx_metadata_table_code");
			assertNotNull(codeIndex);
			assertEquals("code", codeIndex.getColumns().get(0).getName());
			assertEquals("70",
					codeIndex.getSpecifics().get("fill_factor").toString());
			var descendingIndex = table.getIndexes().get("idx_metadata_table_code_desc");
			assertEquals("code", descendingIndex.getColumns().get(0).getName());
			assertEquals(Order.Desc, descendingIndex.getColumns().get(0).getOrder());
			var foreignKey = table.getConstraints().stream()
					.filter(ForeignKeyConstraint.class::isInstance)
					.map(ForeignKeyConstraint.class::cast)
					.findFirst().orElseThrow();
			assertEquals("parent_id", foreignKey.getColumns().get(0).getName());
			assertEquals("id", foreignKey.getRelatedColumns().get(0).getName());
			var view = schema.getViews().get("metadata_view");
			assertNotNull(view);
			String viewStatement = String.join(" ", view.getStatement())
					.toLowerCase().replaceAll("\\s+", " ");
			assertTrue(viewStatement.contains("select id, code from metadata_table"), viewStatement);
			assertEquals(2, view.getColumns().size());
			assertEquals("id", view.getColumns().get(0).getName());
			assertEquals("code", view.getColumns().get(1).getName());
			var trigger = schema.getTriggers().get("metadata_trigger");
			assertNotNull(trigger);
			assertEquals("metadata_table", trigger.getTableName());
			assertEquals("AFTER", trigger.getActionTiming());
			assertEquals("ROW", trigger.getActionOrientation());
			assertTrue(trigger.getEventManipulation().contains("INSERT"));
			assertTrue(trigger.getEventManipulation().contains("UPDATE"));
			String triggerDefinition = String.join(" ", trigger.getDefinition()) + " "
					+ String.join(" ", trigger.getStatement());
			triggerDefinition = triggerDefinition
					.toLowerCase().replaceAll("\\s+", " ");
			assertTrue(triggerDefinition.contains("create trigger"), triggerDefinition);
			var procedure = schema.getProcedures().get("metadata_procedure");
			assertNotNull(procedure);
			String procedureDefinition = String.join(" ", procedure.getDefinition())
					.toLowerCase().replaceAll("\\s+", " ");
			assertTrue(procedureDefinition.contains("create procedure"), procedureDefinition);
			assertTrue(procedureDefinition.contains("metadata_procedure"), procedureDefinition);
			assertTrue(procedureDefinition.contains("@p_id int"), procedureDefinition);
			assertTrue(procedureDefinition.contains("select code from metadata_table"), procedureDefinition);
			var function = schema.getFunctions().stream()
					.filter(current -> "metadata_function".equalsIgnoreCase(current.getName()))
					.findFirst().orElseThrow(() -> new AssertionError(
							"type=" + functionObjectType + ", functions=" + schema.getFunctions()));
			String functionDefinition = String.join(" ", function.getDefinition())
					.toLowerCase().replaceAll("\\s+", " ");
			assertTrue(functionDefinition.contains("create function"), functionDefinition);
			assertTrue(functionDefinition.contains("metadata_function"), functionDefinition);
			assertTrue(functionDefinition.contains("@p_id int"), functionDefinition);
			assertTrue(functionDefinition.contains("returns int"), functionDefinition);
			assertTrue(functionDefinition.contains("return @p_id * 2"), functionDefinition);
		}
	}

	@Test
	void generatedIdentityFailsBeforePreparingPerRowStatements() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
				"jdbc:jtds:sybase://localhost:" + ASE.getMappedPort(5000) + "/master", "sa", "sybase")) {
			connection.setAutoCommit(false);
			Table table = new Table("sqlapp_identity_probe");
			table.setDialect(DialectResolver.getInstance().getDialect(connection));
			table.getColumns().add(new Column("id").setDataType(DataType.INT).setIdentity(true));
			table.getColumns().add(new Column("txt").setDataType(DataType.VARCHAR).setLength(30));
			SQLException exception = assertThrows(SQLException.class, () -> {
				try (JdbcTreeDataSession session = new JdbcTreeDataSession(connection, table)) {
					session.setTableOperationMode(TableOperationMode.INSERT);
					Row row = session.newRow(table);
					row.put("txt", "row-1");
				}
			});
			assertTrue(exception.getMessage().contains("provide explicit key values"), exception::getMessage);
		}
	}
}

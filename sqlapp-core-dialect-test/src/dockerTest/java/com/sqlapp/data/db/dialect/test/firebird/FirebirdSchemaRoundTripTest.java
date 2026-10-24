/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.firebird;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.Transferable;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * Destructive tests only inside automatically removed, dedicated containers.
 */
class FirebirdSchemaRoundTripTest {
	@TestFactory
	Stream<DynamicTest> roundTripsSupportedEngines() {
		return Stream.of("2.5", "3.0", "4.0", "5.0")
				.map(version -> DynamicTest.dynamicTest("Firebird " + version, () -> verify(version)));
	}

	private void verify(String version) throws Exception {
		String tag = version.equals("2.5") ? "v2.5.9-ss" : "v" + version;
		try (var container = new GenericContainer<>(DockerImageName.parse("jacobalberty/firebird:" + tag))
				.withEnv("ISC_PASSWORD", "masterkey").withEnv("FIREBIRD_DATABASE", "test.fdb").withExposedPorts(3050)
				.waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofMinutes(2))) {
			if (version.equals("2.5")) {
				// The 2.5 entrypoint opens a temporary server, creates the DB, then restarts
				// it.
				// Its final command runs only after that temporary server has stopped.
				String start = "#!/bin/sh\nset -e\ntouch /tmp/sqlapp-firebird-initialized\nexec /usr/local/firebird/bin/fbguard\n";
				container
						.withCopyToContainer(
								Transferable.of(start.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0755),
								"/tmp/sqlapp-firebird-start")
						.withCommand("/tmp/sqlapp-firebird-start")
						.waitingFor(new WaitAllStrategy()
								.withStrategy(Wait.forSuccessfulCommand("test -f /tmp/sqlapp-firebird-initialized"))
								.withStrategy(Wait.forListeningPort()));
			}
			container.start();
			String url = "jdbc:firebirdsql://" + container.getHost() + ":" + container.getMappedPort(3050)
					+ "//firebird/data/test.fdb?encoding=UTF8"
					// Jaybird 6 disables the Firebird 2.5 wire protocol by default.
					+ (version.equals("2.5") ? "&enableProtocol=12" : "");
			try (Connection connection = DriverManager.getConnection(url, "SYSDBA", "masterkey");
					Statement statement = connection.createStatement()) {
				int major = connection.getMetaData().getDatabaseMajorVersion();
				assertEquals(Integer.parseInt(version.substring(0, 1)), major);
				Dialect dialect = DialectResolver.getInstance().getDialect(connection);
				assertEquals("Firebird" + version.replace(".", ""), dialect.getClass().getSimpleName());
				columns(connection, statement, dialect, major);
				temporaryTables(connection, statement, dialect, major, url);
				constraints(connection, statement, dialect);
				advancedColumns(connection, statement, dialect, major);
				indexes(connection, statement, dialect, major);
				triggers(connection, statement, dialect, major);
				routines(connection, statement, dialect, major);
				alterSignatures(connection, statement, dialect, major);
				parameterComments(connection, statement, dialect, major);
				objectRecreation(connection, statement, dialect, major);
				textRoutines(connection, statement, dialect, major);
				externalRoutines(connection, statement, dialect, major);
				privileges(connection, statement, dialect, major);
				systemPrivileges(connection, statement, dialect, major);
				ddlPrivileges(connection, statement, dialect, major);
				objectSecurity(connection, statement, dialect, major);
				packages(connection, statement, dialect, major);
			}
		}
	}

	private void constraints(Connection connection, Statement statement, Dialect dialect) throws Exception {
		statement.execute("CREATE TABLE FB_PARENT (A INTEGER NOT NULL, B INTEGER NOT NULL, C INTEGER, "
				+ "CONSTRAINT PK_FB_PARENT PRIMARY KEY (A,B) USING DESCENDING INDEX IX_FB_PARENT_PK, "
				+ "CONSTRAINT UQ_FB_PARENT UNIQUE (C) USING DESCENDING INDEX IX_FB_PARENT_UQ, "
				+ "CONSTRAINT CK_FB_POS CHECK (A>0), CONSTRAINT CK_FB_CONSTANT CHECK (1=1))");
		statement.execute("CREATE TABLE FB_CHILD (X INTEGER, Y INTEGER, CONSTRAINT FK_FB_PARENT FOREIGN KEY (X,Y) "
				+ "REFERENCES FB_PARENT (A,B) ON UPDATE CASCADE ON DELETE SET NULL USING DESCENDING INDEX IX_FB_CHILD_FK)");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("FB_PARENT");
		Table parent = reader.getAllFull(connection).get(0);
		assertEquals(2, parent.getConstraints().getUniqueConstraints().size());
		assertEquals(2, parent.getConstraints().getCheckConstraints().size());
		var checks = reader.getCheckConstraintReader();
		checks.setObjectName("FB_PARENT");
		checks.setConstraintName("CK_FB_CONSTANT");
		assertEquals(1, checks.getAll(connection).size());
		assertEquals("CK_FB_CONSTANT", checks.getAll(connection).get(0).getName());
		checks.setObjectName(null);
		checks.setConstraintName(null);
		reader.setObjectName("FB_CHILD");
		Table child = reader.getAllFull(connection).get(0);
		var fk = child.getConstraints().getForeignKeyConstraints().get(0);
		assertEquals(List.of("X", "Y"), fk.getColumns().stream().map(c -> c.getName()).toList());
		assertEquals(List.of("A", "B"), fk.getRelatedColumns().stream().map(c -> c.getName()).toList());
		assertEquals(CascadeRule.Cascade, fk.getUpdateRule());
		assertEquals(CascadeRule.SetNull, fk.getDeleteRule());
		assertEquals("IX_FB_CHILD_FK", fk.getSpecifics().get("firebird.indexName"));
		statement.execute("DROP TABLE FB_CHILD");
		statement.execute("DROP TABLE FB_PARENT");
		execute(statement, dialect, parent, SqlType.CREATE);
		execute(statement, dialect, child, SqlType.CREATE);
		reader.setObjectName("FB_PARENT");
		assertEquals(2, reader.getAllFull(connection).get(0).getConstraints().getCheckConstraints().size());
		try (var rows = statement.executeQuery(
				"SELECT RDB$INDEX_TYPE FROM RDB$INDICES WHERE RDB$INDEX_NAME IN ('IX_FB_PARENT_PK','IX_FB_PARENT_UQ','IX_FB_CHILD_FK')")) {
			int count = 0;
			while (rows.next()) {
				assertEquals(1, rows.getInt(1));
				count++;
			}
			assertEquals(3, count);
		}
		statement.execute("INSERT INTO FB_PARENT (A,B,C) VALUES (1,2,3)");
		statement.execute("INSERT INTO FB_CHILD (X,Y) VALUES (1,2)");
		statement.execute("UPDATE FB_PARENT SET A=4 WHERE A=1");
		try (var rows = statement.executeQuery("SELECT X,Y FROM FB_CHILD")) {
			assertTrue(rows.next());
			assertEquals(4, rows.getInt(1));
			assertEquals(2, rows.getInt(2));
		}
		statement.execute("DELETE FROM FB_PARENT WHERE A=4");
		try (var rows = statement.executeQuery("SELECT X,Y FROM FB_CHILD")) {
			assertTrue(rows.next());
			assertNull(rows.getObject(1));
			assertNull(rows.getObject(2));
		}
		assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO FB_PARENT (A,B) VALUES (-1,2)"));
	}

	private void temporaryTables(Connection connection, Statement statement, Dialect dialect, int major, String url)
			throws Exception {
		for (String mode : List.of("PRESERVE", "DELETE")) {
			String name = "FB_GTT_" + mode;
			statement.execute("CREATE GLOBAL TEMPORARY TABLE " + name + " (ID INTEGER) ON COMMIT " + mode + " ROWS");
			if (major >= 4)
				statement.execute("ALTER TABLE " + name + " ALTER SQL SECURITY INVOKER");
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setObjectName(name);
			Table original = reader.getAllFull(connection).get(0);
			assertEquals(Table.TableType.Temporary, original.getTableType());
			assertEquals(mode + " ROWS", original.getSpecifics().get("firebird.onCommit"));
			statement.execute("DROP TABLE " + name);
			execute(statement, dialect, original, SqlType.CREATE);
			Table restored = reader.getAllFull(connection).get(0);
			assertEquals(original.getTableType(), restored.getTableType());
			assertEquals(original.getSpecifics().get("firebird.onCommit"),
					restored.getSpecifics().get("firebird.onCommit"));
			assertEquals(original.getSpecifics().get("firebird.sqlSecurity"),
					restored.getSpecifics().get("firebird.sqlSecurity"));
			statement.execute("INSERT INTO " + name + " (ID) VALUES (1)");
			try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + name)) {
				assertTrue(rows.next());
				assertEquals(mode.equals("PRESERVE") ? 1 : 0, rows.getInt(1));
			}
		}
		try (var other = DriverManager.getConnection(url, "SYSDBA", "masterkey");
				var otherStatement = other.createStatement()) {
			for (String name : List.of("FB_GTT_PRESERVE", "FB_GTT_DELETE"))
				try (var rows = otherStatement.executeQuery("SELECT COUNT(*) FROM " + name)) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1), "Temporary rows must be isolated between connections");
				}
		}
	}

	private void columns(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		statement.execute("CREATE DOMAIN FB_REQUIRED AS VARCHAR(12) CHARACTER SET UTF8 DEFAULT 'fallback' NOT NULL");
		statement.execute("CREATE TABLE FB_TYPES (ID INTEGER NOT NULL, LABEL FB_REQUIRED, OPTIONAL_VALUE INTEGER, "
				+ "AMOUNT NUMERIC(5,2), BIG_AMOUNT DECIMAL(18,4)"
				+ (major >= 3 ? ", ENABLED BOOLEAN, AUTO_ID BIGINT GENERATED BY DEFAULT AS IDENTITY" : "")
				+ (major >= 4
						? ", WIDE_AMOUNT DECIMAL(38,4), EXACT_INTEGER INT128, D16 DECFLOAT(16), D34 DECFLOAT(34), "
								+ "ZONED_TIME TIME WITH TIME ZONE, ZONED_TIMESTAMP TIMESTAMP WITH TIME ZONE"
						: "")
				+ ")");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("FB_TYPES");
		Table original = reader.getAllFull(connection).get(0);
		assertTrue(original.getColumns().get("ID").isNotNull());
		assertTrue(original.getColumns().get("LABEL").isNotNull());
		assertFalse(original.getColumns().get("OPTIONAL_VALUE").isNotNull());
		assertEquals(12L, original.getColumns().get("LABEL").getLength());
		assertEquals(48L, original.getColumns().get("LABEL").getOctetLength());
		assertEquals("'fallback'", original.getColumns().get("LABEL").getDefaultValue());
		statement.execute("DROP TABLE FB_TYPES");
		execute(statement, dialect, original, SqlType.CREATE);
		Table recreated = reader.getAllFull(connection).get(0);
		for (var column : original.getColumns()) {
			var actual = recreated.getColumns().get(column.getName());
			assertEquals(column.getDataType(), actual.getDataType(), column.getName());
			assertEquals(column.getLength(), actual.getLength(), column.getName());
			assertEquals(column.getScale(), actual.getScale(), column.getName());
			assertEquals(column.isNotNull(), actual.isNotNull(), column.getName());
			assertEquals(column.isIdentity(), actual.isIdentity(), column.getName());
		}
		statement.execute("INSERT INTO FB_TYPES (ID) VALUES (1)");
		try (var rows = statement.executeQuery("SELECT LABEL FROM FB_TYPES WHERE ID=1")) {
			assertTrue(rows.next());
			assertEquals("fallback", rows.getString(1));
		}
	}

	private void indexes(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		statement.execute("CREATE TABLE FB_INDEXED (ID INTEGER, QTY INTEGER, LABEL VARCHAR(20))");
		statement.execute("CREATE DESCENDING INDEX FB_EXPR ON FB_INDEXED COMPUTED BY (UPPER(LABEL))");
		statement.execute("CREATE DESCENDING INDEX FB_DESC ON FB_INDEXED (ID, QTY)");
		statement.execute("ALTER INDEX FB_DESC INACTIVE");
		if (major >= 5)
			statement.execute("CREATE INDEX FB_PARTIAL ON FB_INDEXED (ID) WHERE QTY IS NOT NULL");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("FB_INDEXED");
		Table table = reader.getAllFull(connection).get(0);
		assertEquals(major >= 5 ? 3 : 2, table.getIndexes().size());
		assertEquals(IndexType.Function, table.getIndexes().get("FB_EXPR").getIndexType());
		for (Index index : table.getIndexes()) {
			statement.execute("DROP INDEX " + dialect.quote(index.getName()));
			execute(statement, dialect, index, SqlType.CREATE);
		}
		Table recreated = reader.getAllFull(connection).get(0);
		for (Index index : table.getIndexes()) {
			Index actual = recreated.getIndexes().get(index.getName());
			assertEquals(index.getIndexType(), actual.getIndexType());
			assertEquals(index.isEnable(), actual.isEnable());
			assertEquals(index.getWhere(), actual.getWhere());
			assertEquals(index.getColumns().get(0).getOrder(), actual.getColumns().get(0).getOrder());
		}
	}

	private void advancedColumns(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		statement.execute("CREATE TABLE FB_ADVANCED (ID INTEGER, TOTAL INTEGER COMPUTED BY (ID * 2), "
				+ "MATRIX INTEGER[-2:-1,3:5], NEGATIVE_RANGE INTEGER[-3:-1], "
				+ "LATIN_VALUE VARCHAR(10) CHARACTER SET ISO8859_1)");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("FB_ADVANCED");
		Table original = reader.getAllFull(connection).get(0);
		assertEquals(5, original.getColumns().size(), "array dimensions must not duplicate columns");
		assertEquals(2, original.getColumns().get("MATRIX").getArrayDimension());
		assertEquals("-2:-1,3:5", original.getColumns().get("MATRIX").getSpecifics().get("firebird.array.bounds"));
		assertEquals("ISO8859_1", original.getColumns().get("LATIN_VALUE").getCharacterSet());
		assertEquals(10L, original.getColumns().get("LATIN_VALUE").getOctetLength());
		statement.execute("DROP TABLE FB_ADVANCED");
		execute(statement, dialect, original, SqlType.CREATE);
		var recreated = reader.getAllFull(connection).get(0);
		assertEquals(5, recreated.getColumns().size());
		assertEquals("-2:-1,3:5", recreated.getColumns().get("MATRIX").getSpecifics().get("firebird.array.bounds"));
		assertEquals("-3:-1", recreated.getColumns().get("NEGATIVE_RANGE").getSpecifics().get("firebird.array.bounds"));
		assertEquals("ISO8859_1", recreated.getColumns().get("LATIN_VALUE").getCharacterSet());
		statement.execute("INSERT INTO FB_ADVANCED (ID) VALUES (4)");
		try (var rows = statement.executeQuery("SELECT TOTAL FROM FB_ADVANCED")) {
			assertTrue(rows.next());
			assertEquals(8, rows.getInt(1));
		}
		if (major < 3)
			return;
		statement.execute(
				"CREATE TABLE FB_IDENTITY_OPTIONS (ID BIGINT GENERATED " + (major >= 4 ? "ALWAYS" : "BY DEFAULT")
						+ " AS IDENTITY (START WITH 100" + (major >= 4 ? " INCREMENT BY 5" : "") + "), LABEL INTEGER)");
		reader.setObjectName("FB_IDENTITY_OPTIONS");
		Table identity = reader.getAllFull(connection).get(0);
		Column id = identity.getColumns().get("ID");
		assertEquals(100L, id.getIdentityStartValue());
		assertEquals(major >= 4 ? 5L : 1L, id.getIdentityStep());
		assertEquals(major >= 4 ? IdentityGenerationType.Always : IdentityGenerationType.ByDefault,
				id.getIdentityGenerationType());
		statement.execute("DROP TABLE FB_IDENTITY_OPTIONS");
		execute(statement, dialect, identity, SqlType.CREATE);
		statement.execute("INSERT INTO FB_IDENTITY_OPTIONS (LABEL) VALUES (1)");
		statement.execute("INSERT INTO FB_IDENTITY_OPTIONS (LABEL) VALUES (2)");
		try (var rows = statement.executeQuery("SELECT ID FROM FB_IDENTITY_OPTIONS ORDER BY LABEL")) {
			assertTrue(rows.next());
			assertEquals(major >= 4 ? 100L : 101L, rows.getLong(1));
			assertTrue(rows.next());
			assertEquals(major >= 4 ? 105L : 102L, rows.getLong(1));
		}
	}

	private void triggers(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		statement.execute("CREATE TRIGGER FB_DML FOR FB_INDEXED ACTIVE BEFORE INSERT OR UPDATE POSITION 11 "
				+ "AS BEGIN NEW.QTY = 42; END");
		statement.execute("CREATE TRIGGER FB_CONNECT ACTIVE ON CONNECT POSITION 7 AS BEGIN END");
		if (major >= 3)
			statement.execute("CREATE TRIGGER FB_DDL ACTIVE BEFORE CREATE TABLE POSITION 5 AS BEGIN END");
		var reader = dialect.getCatalogReader().getSchemaReader().getTriggerReader();
		for (String name : major >= 3 ? List.of("FB_DML", "FB_CONNECT", "FB_DDL") : List.of("FB_DML", "FB_CONNECT")) {
			reader.setObjectName(name);
			Trigger original = reader.getAll(connection).get(0);
			statement.execute("DROP TRIGGER " + name);
			execute(statement, dialect, original, SqlType.CREATE);
			Trigger actual = reader.getAll(connection).get(0);
			assertEquals(original.getActionTiming(), actual.getActionTiming());
			assertEquals(original.getEventManipulation(), actual.getEventManipulation());
			assertEquals(original.getSpecifics().get("POSITION"), actual.getSpecifics().get("POSITION"));
		}
		statement.execute("INSERT INTO FB_INDEXED (ID) VALUES (1)");
		try (var rows = statement.executeQuery("SELECT QTY FROM FB_INDEXED WHERE ID=1")) {
			assertTrue(rows.next());
			assertEquals(42, rows.getInt(1));
		}
	}

	private void packages(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		if (major < 3)
			return;
		statement.execute("CREATE PACKAGE FB_RECREATE_PACKAGE " + (major >= 4 ? "SQL SECURITY DEFINER " : "")
				+ "AS BEGIN FUNCTION VALUE_OF() RETURNS INTEGER; END");
		statement.execute(
				"CREATE PACKAGE BODY FB_RECREATE_PACKAGE AS BEGIN FUNCTION VALUE_OF() RETURNS INTEGER AS BEGIN RETURN 7; END END");
		var schema = dialect.getCatalogReader().getSchemaReader();
		var headers = schema.getPackageReader();
		headers.setObjectName("FB_RECREATE_PACKAGE");
		var bodies = schema.getPackageBodyReader();
		bodies.setObjectName("FB_RECREATE_PACKAGE");
		var header = headers.getAll(connection).get(0);
		var body = bodies.getAll(connection).get(0);
		assertEquals(major >= 4 ? "DEFINER" : null, header.getSpecifics().get("firebird.sqlSecurity"));
		statement.execute("DROP PACKAGE BODY FB_RECREATE_PACKAGE");
		assertTrue(bodies.getAll(connection).isEmpty(),
				"A header without a body must not produce an empty PackageBody");
		statement.execute("DROP PACKAGE FB_RECREATE_PACKAGE");
		execute(statement, dialect, header, SqlType.CREATE);
		execute(statement, dialect, body, SqlType.CREATE);
		assertEquals(header.getStatement(), headers.getAll(connection).get(0).getStatement());
		assertEquals(body.getStatement(), bodies.getAll(connection).get(0).getStatement());
		assertEquals(header.getSpecifics().get("firebird.sqlSecurity"),
				headers.getAll(connection).get(0).getSpecifics().get("firebird.sqlSecurity"));
		try (var rows = statement.executeQuery("SELECT FB_RECREATE_PACKAGE.VALUE_OF() FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(7, rows.getInt(1));
		}
		headers.getReaderOptions().setReadStatement(false);
		bodies.getReaderOptions().setReadStatement(false);
		assertTrue(headers.getAll(connection).get(0).getStatement().isEmpty());
		assertTrue(bodies.getAll(connection).get(0).getStatement().isEmpty());
	}

	private void objectSecurity(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		if (major >= 3) {
			statement.execute("CREATE TRIGGER FB_UDR_TRIGGER FOR FB_INDEXED INACTIVE AFTER INSERT "
					+ "EXTERNAL NAME 'udrcpp_example!replicate!unused' ENGINE UDR");
			var triggers = dialect.getCatalogReader().getSchemaReader().getTriggerReader();
			triggers.setObjectName("FB_UDR_TRIGGER");
			Trigger trigger = triggers.getAll(connection).get(0);
			assertEquals("UDR", trigger.getSpecifics().get("firebird.external.engine"));
			assertEquals("udrcpp_example!replicate!unused", trigger.getSpecifics().get("firebird.external.entrypoint"));
			statement.execute("DROP TRIGGER FB_UDR_TRIGGER");
			execute(statement, dialect, trigger, SqlType.CREATE);
			Trigger restored = triggers.getAll(connection).get(0);
			assertFalse(restored.isEnable());
			assertEquals(trigger.getSpecifics().get("firebird.external.entrypoint"),
					restored.getSpecifics().get("firebird.external.entrypoint"));
		}
		if (major < 4)
			return;
		statement.execute("CREATE TABLE FB_SECURITY (ID INTEGER) SQL SECURITY DEFINER");
		var tables = dialect.getCatalogReader().getSchemaReader().getTableReader();
		tables.setObjectName("FB_SECURITY");
		Table table = tables.getAllFull(connection).get(0);
		assertEquals("DEFINER", table.getSpecifics().get("firebird.sqlSecurity"));
		statement.execute("DROP TABLE FB_SECURITY");
		execute(statement, dialect, table, SqlType.CREATE);
		assertEquals("DEFINER", tables.getAllFull(connection).get(0).getSpecifics().get("firebird.sqlSecurity"));
		statement.execute(
				"CREATE TRIGGER FB_SEC_EXPLICIT FOR FB_SECURITY BEFORE INSERT SQL SECURITY INVOKER AS BEGIN NEW.ID=99; END");
		statement.execute("CREATE TRIGGER FB_SEC_INHERITED FOR FB_SECURITY AFTER INSERT AS BEGIN END");
		var triggers = dialect.getCatalogReader().getSchemaReader().getTriggerReader();
		for (String name : List.of("FB_SEC_EXPLICIT", "FB_SEC_INHERITED")) {
			triggers.setObjectName(name);
			Trigger trigger = triggers.getAll(connection).get(0);
			assertEquals(name.equals("FB_SEC_EXPLICIT") ? "INVOKER" : null,
					trigger.getSpecifics().get("firebird.sqlSecurity"));
			statement.execute("DROP TRIGGER " + name);
			execute(statement, dialect, trigger, SqlType.CREATE);
			assertEquals(trigger.getSpecifics().get("firebird.sqlSecurity"),
					triggers.getAll(connection).get(0).getSpecifics().get("firebird.sqlSecurity"));
		}
		statement.execute("INSERT INTO FB_SECURITY (ID) VALUES (1)");
		try (var rows = statement.executeQuery("SELECT ID FROM FB_SECURITY")) {
			assertTrue(rows.next());
			assertEquals(99, rows.getInt(1));
		}
	}

	private void textRoutines(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		statement.execute(
				"CREATE PROCEDURE FB_TEXT_PROC (P_VALUE VARCHAR(12) CHARACTER SET UTF8 NOT NULL COLLATE UNICODE DEFAULT 'hello') "
						+ "RETURNS (P_RESULT VARCHAR(12) CHARACTER SET UTF8 NOT NULL COLLATE UNICODE) AS BEGIN P_RESULT=P_VALUE; SUSPEND; END");
		var procedures = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		procedures.setObjectName("FB_TEXT_PROC");
		Procedure procedure = procedures.getAllFull(connection).get(0);
		for (var argument : procedure.getArguments()) {
			assertEquals(12L, argument.getLength());
			assertEquals("UTF8", argument.getCharacterSet());
			assertEquals("UNICODE", argument.getCollation());
			assertTrue(argument.isNotNull());
		}
		statement.execute("DROP PROCEDURE FB_TEXT_PROC");
		execute(statement, dialect, procedure, SqlType.CREATE);
		try (var rows = statement.executeQuery("SELECT P_RESULT FROM FB_TEXT_PROC")) {
			assertTrue(rows.next());
			assertEquals("hello", rows.getString(1));
		}
		assertEquals(12L, procedures.getAllFull(connection).get(0).getArguments().get("P_VALUE").getLength());
		if (major < 3)
			return;
		statement.execute(
				"CREATE FUNCTION FB_TEXT_FUNC (P_VALUE VARCHAR(12) CHARACTER SET UTF8 NOT NULL COLLATE UNICODE DEFAULT 'hello') "
						+ "RETURNS VARCHAR(12) CHARACTER SET UTF8 NOT NULL COLLATE UNICODE AS BEGIN RETURN P_VALUE; END");
		var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
		functions.setObjectName("FB_TEXT_FUNC");
		Function function = functions.getAllFull(connection).get(0);
		assertEquals(12L, function.getArguments().get("P_VALUE").getLength());
		assertEquals(12L, function.getReturning().getLength());
		assertEquals("UTF8", function.getReturning().getCharacterSet());
		assertEquals("UNICODE", function.getReturning().getCollation());
		assertEquals("true", function.getReturning().getSpecifics().get("firebird.notNull"));
		statement.execute("DROP FUNCTION FB_TEXT_FUNC");
		execute(statement, dialect, function, SqlType.CREATE);
		Function restored = functions.getAllFull(connection).get(0);
		assertEquals(12L, restored.getReturning().getLength());
		assertEquals("UTF8", restored.getReturning().getCharacterSet());
		assertEquals("UNICODE", restored.getReturning().getCollation());
		assertEquals("true", restored.getReturning().getSpecifics().get("firebird.notNull"));
		try (var rows = statement.executeQuery("SELECT FB_TEXT_FUNC() FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals("hello", rows.getString(1));
		}
	}

	private void alterSignatures(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		statement.execute("CREATE ROLE FB_SIGNATURE_READER");
		statement.execute(
				"CREATE PROCEDURE FB_SIGNATURE_PROC (P_VALUE INTEGER DEFAULT 3) RETURNS (P_RESULT INTEGER) AS BEGIN P_RESULT=P_VALUE; SUSPEND; END");
		statement.execute("GRANT EXECUTE ON PROCEDURE FB_SIGNATURE_PROC TO ROLE FB_SIGNATURE_READER");
		var procedures = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		procedures.setObjectName("FB_SIGNATURE_PROC");
		Procedure original = procedures.getAllFull(connection).get(0);
		Procedure target = original.clone();
		target.getArguments().get("P_VALUE").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDataTypeName("BIGINT").setDefaultValue("7");
		target.getArguments().get("P_RESULT").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDataTypeName("BIGINT");
		target.getArguments().add(new NamedArgument("P_ADD").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDefaultValue("2"));
		target.setStatement("BEGIN P_RESULT=P_VALUE+P_ADD; SUSPEND; END");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER)) {
			assertTrue(operation.getSqlText().startsWith("ALTER PROCEDURE"));
			statement.execute(operation.getSqlText());
		}
		Procedure restored = procedures.getAllFull(connection).get(0);
		assertEquals(3, restored.getArguments().size());
		assertEquals(com.sqlapp.data.db.datatype.DataType.BIGINT,
				restored.getArguments().get("P_RESULT").getDataType());
		assertEquals("7", restored.getArguments().get("P_VALUE").getDefaultValue());
		try (var rows = statement.executeQuery("SELECT P_RESULT FROM FB_SIGNATURE_PROC")) {
			assertTrue(rows.next());
			assertEquals(9L, rows.getLong(1));
		}
		var grants = dialect.getCatalogReader().getRoutinePrivilegeReader();
		grants.setObjectName("FB_SIGNATURE_PROC");
		assertTrue(grants.getAll(connection).stream()
				.anyMatch(grant -> "FB_SIGNATURE_READER".equals(grant.getGranteeName())));
		if (major < 3)
			return;
		statement.execute(
				"CREATE FUNCTION FB_SIGNATURE_FUNC (P_VALUE INTEGER DEFAULT 3) RETURNS INTEGER AS BEGIN RETURN P_VALUE; END");
		statement.execute("GRANT EXECUTE ON FUNCTION FB_SIGNATURE_FUNC TO ROLE FB_SIGNATURE_READER");
		var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
		functions.setObjectName("FB_SIGNATURE_FUNC");
		Function function = functions.getAllFull(connection).get(0);
		Function changed = function.clone();
		changed.getArguments().get("P_VALUE").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDataTypeName("BIGINT").setDefaultValue("7");
		changed.getArguments().add(new NamedArgument("P_ADD").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDefaultValue("2"));
		changed.getReturning().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT).setDataTypeName("BIGINT");
		changed.setStatement("BEGIN RETURN P_VALUE+P_ADD; END");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(function.diff(changed), SqlType.ALTER)) {
			assertTrue(operation.getSqlText().startsWith("ALTER FUNCTION"));
			statement.execute(operation.getSqlText());
		}
		assertEquals(com.sqlapp.data.db.datatype.DataType.BIGINT,
				functions.getAllFull(connection).get(0).getReturning().getDataType());
		try (var rows = statement.executeQuery("SELECT FB_SIGNATURE_FUNC() FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(9L, rows.getLong(1));
		}
		grants.setObjectName("FB_SIGNATURE_FUNC");
		assertTrue(grants.getAll(connection).stream()
				.anyMatch(grant -> "FB_SIGNATURE_READER".equals(grant.getGranteeName())));
	}

	private void parameterComments(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		statement.execute(
				"CREATE PROCEDURE \"FB comment proc\" (\"in value\" INTEGER) RETURNS (\"out value\" INTEGER) AS BEGIN \"out value\"=\"in value\"; SUSPEND; END");
		statement.execute("COMMENT ON PARAMETER \"FB comment proc\".\"in value\" IS 'input 日本語'");
		statement.execute("COMMENT ON PARAMETER \"FB comment proc\".\"out value\" IS 'output 日本語'");
		var procedures = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		procedures.setObjectName("FB comment proc");
		Procedure procedure = procedures.getAllFull(connection).get(0);
		assertEquals("input 日本語", procedure.getArguments().get("in value").getRemarks());
		assertEquals("output 日本語", procedure.getArguments().get("out value").getRemarks());
		parameterComments(statement, dialect, procedure, () -> procedures.getAllFull(connection).get(0), "PROCEDURE");
		if (major < 3)
			return;
		statement.execute(
				"CREATE FUNCTION \"FB comment func\" (\"in value\" INTEGER) RETURNS INTEGER AS BEGIN RETURN \"in value\"; END");
		statement.execute("COMMENT ON FUNCTION PARAMETER \"FB comment func\".\"in value\" IS 'input 日本語'");
		var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
		functions.setObjectName("FB comment func");
		Function function = functions.getAllFull(connection).get(0);
		assertEquals("input 日本語", function.getArguments().get("in value").getRemarks());
		parameterComments(statement, dialect, function, () -> functions.getAllFull(connection).get(0), "FUNCTION");
	}

	private <T extends ArgumentRoutine<T>> void parameterComments(Statement statement, Dialect dialect, T original,
			java.util.function.Supplier<T> reread, String kind) throws Exception {
		String name = "\"" + original.getName() + "\"";
		statement.execute("DROP " + kind + " " + name);
		execute(statement, dialect, original, SqlType.CREATE);
		T restored = reread.get();
		for (int i = 0; i < original.getArguments().size(); i++)
			assertEquals(original.getArguments().get(i).getRemarks(), restored.getArguments().get(i).getRemarks());
		statement.execute("GRANT EXECUTE ON " + kind + " " + name + " TO ROLE FB_SIGNATURE_READER");
		T changed = restored.clone();
		for (var argument : changed.getArguments())
			argument.setRemarks("changed ' 日本語\nsecond line");
		var comments = dialect.createSqlFactoryRegistry().createSql(restored.diff(changed), SqlType.ALTER);
		assertEquals(changed.getArguments().size(), comments.size());
		for (var operation : comments) {
			assertTrue(operation.getSqlText().startsWith("COMMENT ON"));
			statement.execute(operation.getSqlText());
		}
		T commented = reread.get();
		assertEquals("changed ' 日本語\nsecond line", commented.getArguments().get(0).getRemarks());
		T cleared = commented.clone();
		cleared.getArguments().get(0).setRemarks(null);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(commented.diff(cleared), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		T beforeSignature = reread.get();
		assertNull(beforeSignature.getArguments().get(0).getRemarks());
		T signature = beforeSignature.clone();
		signature.getArguments().get(0).setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDataTypeName("BIGINT");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(beforeSignature.diff(signature),
				SqlType.ALTER))
			statement.execute(operation.getSqlText());
		T afterSignature = reread.get();
		for (int i = 0; i < signature.getArguments().size(); i++)
			assertEquals(signature.getArguments().get(i).getRemarks(),
					afterSignature.getArguments().get(i).getRemarks());
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='"
				+ original.getName() + "' AND RDB$USER='FB_SIGNATURE_READER' AND RDB$PRIVILEGE='X'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
	}

	private void objectRecreation(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		var schema = dialect.getCatalogReader().getSchemaReader();
		var domains = schema.getDomainReader();
		statement.execute(
				"CREATE DOMAIN \"FB text domain\" AS VARCHAR(12) CHARACTER SET UTF8 DEFAULT '初期' NOT NULL CHECK (CHAR_LENGTH(VALUE)>0) COLLATE UNICODE_CI");
		domains.setObjectName("FB text domain");
		Domain text = domains.getAll(connection).get(0);
		assertEquals(12L, text.getLength());
		assertEquals("UTF8", text.getCharacterSet());
		assertEquals("UNICODE_CI", text.getCollation());
		assertTrue(text.isNotNull());
		recreateCommented(statement, dialect, text, () -> domains.getAll(connection).get(0),
				"DROP DOMAIN \"FB text domain\"");
		Domain restoredText = domains.getAll(connection).get(0);
		assertEquals(text.getDefaultValue(), restoredText.getDefaultValue());
		assertEquals(text.getCheck(), restoredText.getCheck());
		assertEquals(text.getCollation(), restoredText.getCollation());
		statement.execute("CREATE DOMAIN FB_ARRAY_DOMAIN AS INTEGER[-2:2,3:4]");
		domains.setObjectName("FB_ARRAY_DOMAIN");
		Domain array = domains.getAll(connection).get(0);
		assertEquals(2, array.getArrayDimension());
		assertEquals("-2:2,3:4", array.getSpecifics().get("firebird.array.bounds"));
		recreateCommented(statement, dialect, array, () -> domains.getAll(connection).get(0),
				"DROP DOMAIN FB_ARRAY_DOMAIN");
		assertEquals("-2:2,3:4", domains.getAll(connection).get(0).getSpecifics().get("firebird.array.bounds"));
		if (major >= 4) {
			statement.execute("CREATE DOMAIN FB_DECIMAL_DOMAIN AS DECIMAL(38,5)");
			domains.setObjectName("FB_DECIMAL_DOMAIN");
			Domain decimal = domains.getAll(connection).get(0);
			assertEquals(38L, decimal.getLength());
			recreateCommented(statement, dialect, decimal, () -> domains.getAll(connection).get(0),
					"DROP DOMAIN FB_DECIMAL_DOMAIN");
			assertEquals(38L, domains.getAll(connection).get(0).getLength());
		}
		statement.execute("CREATE TABLE FB_OBJECT_TABLE (ID INTEGER, TEXT_VALUE VARCHAR(12))");
		statement.execute("COMMENT ON COLUMN FB_OBJECT_TABLE.TEXT_VALUE IS 'column 日本語'");
		var tables = schema.getTableReader();
		tables.setObjectName("FB_OBJECT_TABLE");
		recreateCommented(statement, dialect, tables.getAllFull(connection).get(0),
				() -> tables.getAllFull(connection).get(0), "DROP TABLE FB_OBJECT_TABLE");
		assertEquals("column 日本語", tables.getAllFull(connection).get(0).getColumns().get("TEXT_VALUE").getRemarks());
		statement.execute(
				"CREATE VIEW \"FB aliased view\" (\"renamed id\", \"renamed text\") AS SELECT ID,TEXT_VALUE FROM FB_OBJECT_TABLE WHERE ID>0 WITH CHECK OPTION");
		statement.execute("COMMENT ON COLUMN \"FB aliased view\".\"renamed text\" IS 'view column 日本語'");
		var views = schema.getViewReader();
		views.setObjectName("FB aliased view");
		Table view = views.getAllFull(connection).get(0);
		recreateCommented(statement, dialect, view, () -> views.getAllFull(connection).get(0),
				"DROP VIEW \"FB aliased view\"");
		assertEquals("view column 日本語",
				views.getAllFull(connection).get(0).getColumns().get("renamed text").getRemarks());
		statement.execute("INSERT INTO \"FB aliased view\" (\"renamed id\") VALUES (1)");
		assertThrows(SQLException.class,
				() -> statement.execute("INSERT INTO \"FB aliased view\" (\"renamed id\") VALUES (-1)"));
		statement.execute("CREATE INDEX FB_OBJECT_INDEX ON FB_OBJECT_TABLE(ID)");
		java.util.function.Supplier<Index> index = () -> tables.getAllFull(connection).get(0).getIndexes()
				.get("FB_OBJECT_INDEX");
		recreateCommented(statement, dialect, index.get(), index, "DROP INDEX FB_OBJECT_INDEX");
		statement.execute("CREATE TRIGGER FB_OBJECT_TRIGGER FOR FB_OBJECT_TABLE INACTIVE BEFORE INSERT AS BEGIN END");
		var triggers = schema.getTriggerReader();
		triggers.setObjectName("FB_OBJECT_TRIGGER");
		recreateCommented(statement, dialect, triggers.getAll(connection).get(0),
				() -> triggers.getAll(connection).get(0), "DROP TRIGGER FB_OBJECT_TRIGGER");
		statement.execute("CREATE SEQUENCE FB_OBJECT_SEQUENCE");
		var sequences = schema.getSequenceReader();
		sequences.setObjectName("FB_OBJECT_SEQUENCE");
		recreateCommented(statement, dialect, sequences.getAll(connection).get(0),
				() -> sequences.getAll(connection).get(0), "DROP SEQUENCE FB_OBJECT_SEQUENCE");
		String quotedSequence = "\"FB sequence \"\"quoted\"";
		statement.execute("CREATE SEQUENCE " + quotedSequence);
		statement.execute("SET GENERATOR " + quotedSequence + " TO 41");
		sequences.setObjectName("FB sequence \"quoted");
		assertEquals(java.math.BigInteger.valueOf(41), sequences.getAllFull(connection).get(0).getLastValue());
		try (var rows = statement.executeQuery("SELECT GEN_ID(" + quotedSequence + ",0) FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(41L, rows.getLong(1), "metadata extraction must not advance sequence values");
		}
		statement.execute("CREATE PROCEDURE FB_OBJECT_PROC AS BEGIN END");
		var procedures = schema.getProcedureReader();
		procedures.setObjectName("FB_OBJECT_PROC");
		recreateCommented(statement, dialect, procedures.getAllFull(connection).get(0),
				() -> procedures.getAllFull(connection).get(0), "DROP PROCEDURE FB_OBJECT_PROC");
		if (major >= 3) {
			statement.execute("CREATE FUNCTION FB_OBJECT_FUNC RETURNS INTEGER AS BEGIN RETURN 1; END");
			var functions = schema.getFunctionReader();
			functions.setObjectName("FB_OBJECT_FUNC");
			recreateCommented(statement, dialect, functions.getAllFull(connection).get(0),
					() -> functions.getAllFull(connection).get(0), "DROP FUNCTION FB_OBJECT_FUNC");
			statement.execute("CREATE PACKAGE FB_OBJECT_PACKAGE AS BEGIN END");
			var packages = schema.getPackageReader();
			packages.setObjectName("FB_OBJECT_PACKAGE");
			recreateCommented(statement, dialect, packages.getAll(connection).get(0),
					() -> packages.getAll(connection).get(0), "DROP PACKAGE FB_OBJECT_PACKAGE");
		}
	}

	private <T extends AbstractNamedObject<?>> void recreateCommented(Statement statement, Dialect dialect, T object,
			java.util.function.Supplier<T> reread, String drop) throws Exception {
		String comment = "it's 日本語\nsecond line";
		object.setRemarks(comment);
		execute(statement, dialect, object, SqlType.SET_COMMENT);
		T original = reread.get();
		assertEquals(comment, original.getRemarks());
		statement.execute(drop);
		execute(statement, dialect, original, SqlType.CREATE);
		assertEquals(comment, reread.get().getRemarks());
		original.setRemarks(null);
		execute(statement, dialect, original, SqlType.SET_COMMENT);
		assertNull(reread.get().getRemarks());
	}

	private void routines(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		statement.execute("CREATE PROCEDURE FB_PROC (P_ID INTEGER DEFAULT 3) RETURNS (P_RESULT INTEGER) "
				+ "AS BEGIN P_RESULT = P_ID + 1; SUSPEND; END");
		var reader = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		reader.setObjectName("FB_PROC");
		Procedure original = reader.getAllFull(connection).get(0);
		assertEquals(2, original.getArguments().size());
		try (var source = statement.executeQuery("SELECT COALESCE(P.RDB$DEFAULT_SOURCE, F.RDB$DEFAULT_SOURCE) "
				+ "FROM RDB$PROCEDURE_PARAMETERS P JOIN RDB$FIELDS F ON F.RDB$FIELD_NAME=P.RDB$FIELD_SOURCE "
				+ "WHERE P.RDB$PROCEDURE_NAME='FB_PROC' AND P.RDB$PARAMETER_NAME='P_ID'")) {
			assertTrue(source.next());
			assertEquals("3", original.getArguments().get("P_ID").getDefaultValue(),
					"Native parameter default: " + source.getString(1));
		}
		statement.execute("DROP PROCEDURE FB_PROC");
		execute(statement, dialect, original, SqlType.CREATE);
		assertEquals(2, reader.getAllFull(connection).get(0).getArguments().size());
		try (var rows = statement.executeQuery("SELECT P_RESULT FROM FB_PROC")) {
			assertTrue(rows.next());
			assertEquals(4, rows.getInt(1));
		}
		statement.execute("CREATE ROLE FB_ALTER_READER");
		statement.execute("GRANT EXECUTE ON PROCEDURE FB_PROC TO ROLE FB_ALTER_READER");
		Procedure changed = reader.getAllFull(connection).get(0).clone();
		Procedure beforeAlter = changed.clone();
		changed.setStatement("BEGIN P_RESULT = P_ID + 10; SUSPEND; END");
		if (major >= 4)
			changed.setSqlSecurity(SqlSecurity.Definer);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(beforeAlter.diff(changed), SqlType.ALTER)) {
			assertFalse(operation.getSqlText().contains("DROP"));
			statement.execute(operation.getSqlText());
		}
		Procedure afterAlter = reader.getAllFull(connection).get(0);
		assertEquals(major >= 4 ? SqlSecurity.Definer : null, afterAlter.getSqlSecurity());
		try (var rows = statement.executeQuery("SELECT P_RESULT FROM FB_PROC")) {
			assertTrue(rows.next());
			assertEquals(13, rows.getInt(1));
		}
		var acl = dialect.getCatalogReader().getRoutinePrivilegeReader();
		acl.setObjectName("FB_PROC");
		assertTrue(acl.getAll(connection).stream().anyMatch(p -> "FB_ALTER_READER".equals(p.getGranteeName())));
		if (major < 3)
			return;
		statement.execute(
				"CREATE FUNCTION FB_FUNC (P_ID INTEGER DEFAULT 4) RETURNS INTEGER AS BEGIN RETURN P_ID + 2; END");
		statement.execute("CREATE PACKAGE FB_PACKAGE AS BEGIN FUNCTION FB_FUNC(P_ID INTEGER) RETURNS INTEGER; END");
		statement.execute("CREATE PACKAGE BODY FB_PACKAGE AS BEGIN FUNCTION FB_FUNC(P_ID INTEGER) RETURNS INTEGER "
				+ "AS BEGIN RETURN P_ID + 99; END END");
		var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
		functions.setObjectName("FB_FUNC");
		var all = functions.getAllFull(connection);
		assertEquals(1, all.size(), "package members must not appear as standalone functions");
		Function function = all.get(0);
		assertEquals(1, function.getArguments().size());
		statement.execute("DROP FUNCTION FB_FUNC");
		execute(statement, dialect, function, SqlType.CREATE);
		try (var rows = statement.executeQuery("SELECT FB_FUNC() FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(6, rows.getInt(1));
		}
		Function functionBefore = functions.getAllFull(connection).get(0);
		Function functionAfter = functionBefore.clone();
		functionAfter.setStatement("BEGIN RETURN P_ID + 4; END").setDeterministic(true);
		if (major >= 4)
			functionAfter.setSqlSecurity(SqlSecurity.Invoker);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(functionBefore.diff(functionAfter),
				SqlType.ALTER))
			statement.execute(operation.getSqlText());
		var refreshed = functions.getAllFull(connection).get(0);
		assertEquals(major >= 4 ? SqlSecurity.Invoker : null, refreshed.getSqlSecurity());
		assertEquals(Boolean.TRUE, refreshed.getDeterministic());
		try (var rows = statement.executeQuery("SELECT FB_FUNC() FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(8, rows.getInt(1));
		}
	}

	private void ddlPrivileges(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		if (major < 3)
			return;
		statement.execute("CREATE ROLE FB_DDL_ROLE");
		String[] types = { "TABLE", "VIEW", "PROCEDURE", "FUNCTION", "PACKAGE", "SEQUENCE", "DOMAIN", "EXCEPTION",
				"ROLE", "CHARACTER SET", "COLLATION", "FILTER" };
		for (String type : types)
			for (String action : new String[] { "CREATE", "ALTER ANY", "DROP ANY" })
				statement.execute("GRANT " + action + " " + type + " TO ROLE FB_DDL_ROLE WITH GRANT OPTION");
		statement.execute("GRANT ALTER DATABASE TO ROLE FB_DDL_ROLE WITH GRANT OPTION");
		statement.execute("GRANT DROP DATABASE TO ROLE FB_DDL_ROLE WITH GRANT OPTION");
		var reader = dialect.getCatalogReader().getUserPrivilegeReader();
		reader.setUserName("FB_DDL_ROLE");
		var original = reader.getAll(connection);
		StringBuilder nativeGrants = new StringBuilder();
		try (var rows = statement.executeQuery(
				"SELECT RDB$OBJECT_TYPE, RDB$PRIVILEGE, RDB$RELATION_NAME FROM RDB$USER_PRIVILEGES WHERE RDB$USER='FB_DDL_ROLE'")) {
			while (rows.next())
				nativeGrants.append(rows.getInt(1)).append(':').append(rows.getString(2).trim()).append(':')
						.append(rows.getString(3).trim()).append(';');
		}
		assertEquals(38, original.size(), nativeGrants.toString());
		assertTrue(original.stream().allMatch(UserPrivilege::isGrantable));
		for (UserPrivilege privilege : original)
			execute(statement, dialect, privilege, SqlType.REVOKE);
		assertTrue(reader.getAll(connection).isEmpty());
		for (UserPrivilege privilege : original)
			execute(statement, dialect, privilege, SqlType.GRANT);
		var restored = reader.getAll(connection);
		assertEquals(original.size(), restored.size());
		assertEquals(original.stream().map(UserPrivilege::getPrivilege).sorted().toList(),
				restored.stream().map(UserPrivilege::getPrivilege).sorted().toList());
		assertTrue(restored.stream().allMatch(UserPrivilege::isGrantable));
		for (UserPrivilege privilege : restored) {
			privilege.getSpecifics().put("firebird.revokeOption", "GRANT");
			execute(statement, dialect, privilege, SqlType.REVOKE);
		}
		var withoutGrantOptions = reader.getAll(connection);
		assertEquals(38, withoutGrantOptions.size());
		assertTrue(withoutGrantOptions.stream().noneMatch(UserPrivilege::isGrantable));
		// RDB$ADMIN already exists in the disposable container's security database.
		statement.execute("GRANT CREATE DATABASE TO USER RDB$ADMIN");
		statement.execute("GRANT CREATE DATABASE TO ROLE RDB$ADMIN");
		var creators = (com.sqlapp.data.db.dialect.firebird.metadata.Firebird30UserPrivilegeReader) reader;
		creators.setUserName("RDB$ADMIN");
		assertTrue(creators.getAll(connection).isEmpty(), "server-security grants require explicit opt-in");
		creators.setReadDatabaseCreators(true);
		try {
			var grants = creators.getAll(connection);
			assertEquals(2, grants.size());
			assertEquals(java.util.Set.of("USER", "ROLE"),
					grants.stream().map(p -> p.getSpecifics().get("firebird.granteeType"))
							.collect(java.util.stream.Collectors.toSet()));
			assertTrue(grants.stream().allMatch(p -> "CREATE DATABASE".equals(p.getPrivilege()) && !p.isGrantable()));
			for (UserPrivilege grant : grants)
				execute(statement, dialect, grant, SqlType.REVOKE);
			assertTrue(creators.getAll(connection).isEmpty());
			for (UserPrivilege grant : grants)
				execute(statement, dialect, grant, SqlType.GRANT);
			assertEquals(2, creators.getAll(connection).size());
		} finally {
			creators.setReadDatabaseCreators(false);
			creators.setUserName(null);
		}
	}

	private void systemPrivileges(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		if (major < 4)
			return;
		statement.execute("GRANT SELECT ON FB_INDEXED TO SYSTEM PRIVILEGE USER_MANAGEMENT");
		statement.execute("GRANT UPDATE (QTY) ON FB_INDEXED TO SYSTEM PRIVILEGE USER_MANAGEMENT");
		statement.execute("GRANT EXECUTE ON PROCEDURE FB_PROC TO SYSTEM PRIVILEGE USER_MANAGEMENT");
		var catalog = dialect.getCatalogReader();
		var objectReader = catalog.getObjectPrivilegeReader();
		objectReader.setObjectName("FB_INDEXED");
		var objectGrant = objectReader.getAll(connection).stream()
				.filter(p -> "SYSTEM PRIVILEGE".equals(p.getSpecifics().get("firebird.granteeType"))).findFirst()
				.orElseThrow();
		assertEquals("USER_MANAGEMENT", objectGrant.getGranteeName());
		execute(statement, dialect, objectGrant, SqlType.REVOKE);
		execute(statement, dialect, objectGrant, SqlType.GRANT);
		var columnReader = catalog.getColumnPrivilegeReader();
		columnReader.setTableName("FB_INDEXED");
		var columnGrant = columnReader.getAll(connection).stream()
				.filter(p -> "SYSTEM PRIVILEGE".equals(p.getSpecifics().get("firebird.granteeType"))).findFirst()
				.orElseThrow();
		assertEquals("USER_MANAGEMENT", columnGrant.getGranteeName());
		execute(statement, dialect, columnGrant, SqlType.REVOKE);
		execute(statement, dialect, columnGrant, SqlType.GRANT);
		var routineReader = catalog.getRoutinePrivilegeReader();
		routineReader.setObjectName("FB_PROC");
		var routineGrant = routineReader.getAll(connection).stream()
				.filter(p -> "SYSTEM PRIVILEGE".equals(p.getSpecifics().get("firebird.granteeType"))).findFirst()
				.orElseThrow();
		assertEquals("USER_MANAGEMENT", routineGrant.getGranteeName());
		execute(statement, dialect, routineGrant, SqlType.REVOKE);
		execute(statement, dialect, routineGrant, SqlType.GRANT);
		String key = com.sqlapp.data.db.dialect.firebird.util.FirebirdSystemPrivileges.ROLE_PRIVILEGES;
		String privileges = "USER_MANAGEMENT,ACCESS_SHUTDOWN_DATABASE,SELECT_ANY_OBJECT_IN_DATABASE,REPLICATE_INTO_DATABASE"
				+ (major >= 5 ? ",PROFILE_ANY_ATTACHMENT" : "");
		statement.execute("CREATE ROLE FB_SYSTEM_ROLE SET SYSTEM PRIVILEGES TO " + privileges);
		var reader = dialect.getCatalogReader().getRoleReader();
		reader.setObjectName("FB_SYSTEM_ROLE");
		Role original = reader.getAll(connection).get(0);
		assertEquals(privileges, original.getSpecifics().get(key));
		statement.execute("DROP ROLE FB_SYSTEM_ROLE");
		execute(statement, dialect, original, SqlType.CREATE);
		assertEquals(privileges, reader.getAll(connection).get(0).getSpecifics().get(key));
		statement.execute("GRANT FB_SYSTEM_ROLE TO USER FB_SYSTEM_USER WITH ADMIN OPTION");
		Role target = original.clone();
		target.getSpecifics().put(key, "MONITOR_ANY_ATTACHMENT");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Role afterAlter = reader.getAll(connection).get(0);
		assertEquals("MONITOR_ANY_ATTACHMENT", afterAlter.getSpecifics().get(key));
		Role cleared = afterAlter.clone();
		cleared.getSpecifics().remove(key);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(afterAlter.diff(cleared), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertNull(reader.getAll(connection).get(0).getSpecifics().get(key));
		var members = dialect.getCatalogReader().getRoleMemberReader();
		assertTrue(members.getAll(connection).stream()
				.anyMatch(member -> "FB_SYSTEM_ROLE".equals(member.getMemberRoleName())
						&& "FB_SYSTEM_USER".equals(member.getGranteeName()) && member.isAdmin()));
	}

	private void privileges(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		statement.execute("CREATE ROLE FB_READER");
		statement.execute("COMMENT ON ROLE FB_READER IS 'test reader'");
		statement.execute("GRANT SELECT ON FB_INDEXED TO ROLE FB_READER WITH GRANT OPTION");
		statement.execute("GRANT UPDATE (QTY) ON FB_INDEXED TO ROLE FB_READER");
		statement.execute("GRANT EXECUTE ON PROCEDURE FB_PROC TO ROLE FB_READER");
		statement.execute("GRANT FB_READER TO USER FB_TEST_USER WITH ADMIN OPTION");
		var catalog = dialect.getCatalogReader();
		var roles = catalog.getRoleReader();
		roles.setObjectName("FB_READER");
		Role role = roles.getAll(connection).get(0);
		assertEquals("test reader", role.getRemarks());
		var objects = catalog.getObjectPrivilegeReader();
		objects.setObjectName("FB_INDEXED");
		var objectGrants = objects.getAll(connection).stream().filter(p -> "FB_READER".equals(p.getGranteeName()))
				.toList();
		assertEquals(1, objectGrants.size());
		for (var privilege : objectGrants) {
			execute(statement, dialect, privilege, SqlType.REVOKE);
			execute(statement, dialect, privilege, SqlType.GRANT);
		}
		var optionOnly = objectGrants.get(0).clone();
		optionOnly.getSpecifics().put("firebird.revokeOption", "GRANT");
		execute(statement, dialect, optionOnly, SqlType.REVOKE);
		var retained = objects.getAll(connection).stream().filter(p -> "FB_READER".equals(p.getGranteeName())).toList();
		assertEquals(1, retained.size());
		assertFalse(retained.get(0).isGrantable());
		var columns = catalog.getColumnPrivilegeReader();
		columns.setTableName("FB_INDEXED");
		var columnGrants = columns.getAll(connection).stream().filter(p -> "FB_READER".equals(p.getGranteeName()))
				.toList();
		assertEquals(1, columnGrants.size());
		for (var privilege : columnGrants) {
			execute(statement, dialect, privilege, SqlType.REVOKE);
			execute(statement, dialect, privilege, SqlType.GRANT);
		}
		var routines = catalog.getRoutinePrivilegeReader();
		routines.setObjectName("FB_PROC");
		var routineGrants = routines.getAll(connection).stream().filter(p -> "FB_READER".equals(p.getGranteeName()))
				.toList();
		assertEquals(1, routineGrants.size());
		for (var privilege : routineGrants) {
			execute(statement, dialect, privilege, SqlType.REVOKE);
			execute(statement, dialect, privilege, SqlType.GRANT);
		}
		var memberships = catalog.getRoleMemberReader();
		memberships.setGrantee("FB_TEST_USER");
		var member = memberships.getAll(connection).get(0);
		assertTrue(member.isAdmin());
		execute(statement, dialect, member, SqlType.REVOKE);
		assertTrue(memberships.getAll(connection).isEmpty());
		execute(statement, dialect, member, SqlType.GRANT);
		assertTrue(memberships.getAll(connection).get(0).isAdmin());
		var adminOnly = member.clone();
		adminOnly.getSpecifics().put("firebird.revokeOption", "ADMIN");
		execute(statement, dialect, adminOnly, SqlType.REVOKE);
		assertEquals(1, memberships.getAll(connection).size());
		assertFalse(memberships.getAll(connection).get(0).isAdmin());
		if (major >= 4) {
			statement.execute("CREATE ROLE FB_NESTED");
			statement.execute("GRANT DEFAULT FB_READER TO ROLE FB_NESTED WITH ADMIN OPTION");
			memberships.setGrantee("FB_NESTED");
			var nested = memberships.getAll(connection).get(0);
			assertTrue(nested.isDefault());
			execute(statement, dialect, nested, SqlType.REVOKE);
			execute(statement, dialect, nested, SqlType.GRANT);
			assertTrue(memberships.getAll(connection).get(0).isDefault());
			var defaultOnly = nested.clone();
			defaultOnly.getSpecifics().put("firebird.revokeOption", "DEFAULT");
			execute(statement, dialect, defaultOnly, SqlType.REVOKE);
			assertEquals(1, memberships.getAll(connection).size());
			assertFalse(memberships.getAll(connection).get(0).isDefault());
			assertTrue(memberships.getAll(connection).get(0).isAdmin());
		}
	}

	private void externalRoutines(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		if (major < 4) {
			statement.execute(
					"DECLARE EXTERNAL FUNCTION FB_LEGACY_ABS DOUBLE PRECISION RETURNS DOUBLE PRECISION BY VALUE "
							+ "ENTRY_POINT 'IB_UDF_abs' MODULE_NAME 'ib_udf'");
			var reader = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
			reader.setObjectName("FB_LEGACY_ABS");
			Function function = reader.getAllFull(connection).get(0);
			assertEquals(1, function.getArguments().size());
			assertFalse(function.getDefinition().isEmpty(), "legacy UDF must retain complete executable DDL");
			statement.execute("DROP EXTERNAL FUNCTION FB_LEGACY_ABS");
			execute(statement, dialect, function, SqlType.CREATE);
			try (var rows = statement.executeQuery("SELECT FB_LEGACY_ABS(-3.5) FROM RDB$DATABASE")) {
				assertTrue(rows.next());
				assertEquals(3.5, rows.getDouble(1));
			}
			// Declaration-only fixture: no external library is loaded or invoked.
			statement.execute("DECLARE EXTERNAL FUNCTION FB_ARRAY_SUM INTEGER BY SCALAR_ARRAY RETURNS INTEGER BY VALUE "
					+ "ENTRY_POINT 'array_sum' MODULE_NAME 'array_fixture'");
			reader.setObjectName("FB_ARRAY_SUM");
			Function arrayFunction = reader.getAllFull(connection).get(0);
			assertEquals("4", arrayFunction.getArguments().get(0).getSpecifics().get("firebird.udf.mechanism"));
			assertTrue(String.join("\n", arrayFunction.getDefinition()).contains("BY SCALAR_ARRAY"),
					arrayFunction.getDefinition().toString());
			statement.execute("DROP EXTERNAL FUNCTION FB_ARRAY_SUM");
			execute(statement, dialect, arrayFunction, SqlType.CREATE);
			assertEquals("4", reader.getAllFull(connection).get(0).getArguments().get(0).getSpecifics()
					.get("firebird.udf.mechanism"));
		}
		if (major < 3)
			return;
		statement.execute("CREATE FUNCTION FB_UDR_SUM (A INTEGER, B INTEGER, C INTEGER) RETURNS INTEGER "
				+ "EXTERNAL NAME 'udrcpp_example!sum_args' ENGINE UDR");
		var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
		functions.setObjectName("FB_UDR_SUM");
		Function function = functions.getAllFull(connection).get(0);
		assertEquals("UDR", function.getSpecifics().get("firebird.external.engine"));
		statement.execute("DROP FUNCTION FB_UDR_SUM");
		execute(statement, dialect, function, SqlType.CREATE);
		try (var rows = statement.executeQuery("SELECT FB_UDR_SUM(1,2,3) FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(6, rows.getInt(1));
		}
		statement.execute("CREATE PROCEDURE FB_UDR_ROWS (START_N INTEGER NOT NULL, END_N INTEGER NOT NULL) "
				+ "RETURNS (N INTEGER NOT NULL) EXTERNAL NAME 'udrcpp_example!gen_rows' ENGINE UDR");
		var procedures = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		procedures.setObjectName("FB_UDR_ROWS");
		Procedure procedure = procedures.getAllFull(connection).get(0);
		statement.execute("DROP PROCEDURE FB_UDR_ROWS");
		execute(statement, dialect, procedure, SqlType.CREATE);
		try (var rows = statement.executeQuery("SELECT N FROM FB_UDR_ROWS(1,3) ORDER BY N")) {
			for (int i = 1; i <= 3; i++) {
				assertTrue(rows.next());
				assertEquals(i, rows.getInt(1));
			}
			assertFalse(rows.next());
		}
	}

	private void execute(Statement statement, Dialect dialect, DbObject<?> object, SqlType type) throws SQLException {
		for (var operation : dialect.createSqlFactoryRegistry().createSql(object, type)) {
			try {
				statement.execute(operation.getSqlText());
			} catch (SQLException failure) {
				throw new SQLException("Generated SQL failed: " + operation.getSqlText(), failure);
			}
		}
	}
}

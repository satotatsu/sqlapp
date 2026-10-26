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
			// Restrict external-file tests to the data directory of this disposable
			// container.
			String entrypoint = "#!/bin/sh\nset -e\nconfig=/usr/local/firebird/skel/etc/firebird.conf\n"
					+ "sed -i '/^[[:space:]]*ExternalFileAccess[[:space:]]*=/d' \"$config\"\n"
					+ "printf '\\nExternalFileAccess = Restrict /firebird/data\\n' >> \"$config\"\n"
					+ "exec /usr/local/firebird/docker-entrypoint.sh \"$@\"\n";
			container.withCopyToContainer(
					Transferable.of(entrypoint.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0755),
					"/tmp/sqlapp-firebird-entrypoint").withCreateContainerCmdModifier(
							command -> command.withEntrypoint("/tmp/sqlapp-firebird-entrypoint"));
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
			} else {
				container.withCommand("firebird");
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
				capabilityBoundaries(connection, statement, dialect);
				nativeSchemaObjects(connection, statement, dialect);
				nativeCollations(connection, statement, dialect);
				nativeSchemaAlter(connection, statement, dialect);
				routineTypeReferences(connection, statement, dialect, major);
				standaloneConstraintLifecycle(connection, statement, dialect);
				if (major >= 4)
					publicationPolicy(connection, statement, dialect);
				schemaDependencyOrder(connection, statement, dialect, major);
				schemaAlterRetargeting(connection, statement, dialect);
				circularForeignKeys(connection, statement, dialect);
				columns(connection, statement, dialect, major);
				temporaryTables(connection, statement, dialect, major, url);
				temporaryIndexCreation(connection, statement, dialect, major, url);
				temporaryForeignKeys(connection, statement, dialect);
				numericIdentity(connection, statement, dialect, major);
				deleteAllRows(connection, statement, dialect, url);
				constraints(connection, statement, dialect);
				advancedColumns(connection, statement, dialect, major);
				indexes(connection, statement, dialect, major);
				indexMaintenance(connection, statement, dialect);
				indexStatistics(connection, statement, dialect, major);
				triggers(connection, statement, dialect, major);
				routines(connection, statement, dialect, major);
				filteredRoutineArguments(connection, statement, dialect, major);
				alterSignatures(connection, statement, dialect, major);
				parameterComments(connection, statement, dialect, major);
				objectRecreation(connection, statement, dialect, major);
				alterObjects(connection, statement, dialect, major);
				domainReferencesAndExternalTables(connection, statement, dialect);
				tableColumnAlter(connection, statement, dialect, major);
				generatedColumnAlter(connection, statement, dialect, major);
				sequenceAlter(connection, statement, dialect, major);
				sequenceRecreation(connection, statement, dialect, major);
				textRoutines(connection, statement, dialect, major);
				externalRoutines(connection, statement, dialect, major);
				privileges(connection, statement, dialect, major);
				systemPrivileges(connection, statement, dialect, major);
				ddlPrivileges(connection, statement, dialect, major);
				objectSecurity(connection, statement, dialect, major);
				packages(connection, statement, dialect, major);
				packageAlter(connection, statement, dialect, major);
			}
		}
	}

	private void schemaDependencyOrder(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		Schema empty = readUserSchema(connection, dialect);
		statement.execute("CREATE DOMAIN Z_DEP_DOMAIN AS INTEGER");
		statement.execute("CREATE TABLE Z_DEP_SOURCE (ID Z_DEP_DOMAIN)");
		statement.execute("CREATE VIEW Z_DEP_VIEW AS SELECT ID FROM Z_DEP_SOURCE");
		statement.execute(
				"CREATE PROCEDURE Z_DEP_CALLEE RETURNS (V INTEGER) AS BEGIN SELECT MAX(ID) FROM Z_DEP_VIEW INTO :V; SUSPEND; END");
		statement.execute(
				"CREATE PROCEDURE A_DEP_CALLER RETURNS (V INTEGER) AS BEGIN SELECT V FROM Z_DEP_CALLEE INTO :V; SUSPEND; END");
		statement.execute("COMMENT ON PROCEDURE A_DEP_CALLER IS 'caller comment'");
		if (major >= 3) {
			statement.execute(
					"CREATE FUNCTION Z_DEP_DOUBLE (I INTEGER) RETURNS INTEGER DETERMINISTIC AS BEGIN RETURN I * 2; END");
			statement.execute("CREATE INDEX DEP_EXPR ON Z_DEP_SOURCE COMPUTED BY (Z_DEP_DOUBLE(ID))");
			statement.execute(
					"CREATE FUNCTION A_DEP_READ RETURNS INTEGER AS DECLARE V INTEGER; BEGIN SELECT MAX(ID) FROM Z_DEP_SOURCE INTO :V; RETURN V; END");
			statement.execute("CREATE TABLE A_DEP_COMPUTED (ID INTEGER, V COMPUTED BY (Z_DEP_DOUBLE(ID)))");
			statement.execute(
					"CREATE PACKAGE DEP_PACKAGE AS BEGIN FUNCTION F(I TYPE OF COLUMN Z_DEP_SOURCE.ID) RETURNS INTEGER; END");
			statement.execute(
					"CREATE PACKAGE BODY DEP_PACKAGE AS BEGIN FUNCTION F(I TYPE OF COLUMN Z_DEP_SOURCE.ID) RETURNS INTEGER AS BEGIN RETURN I + 1; END END");
			statement.execute("CREATE FUNCTION A_DEP_PACKAGE RETURNS INTEGER AS BEGIN RETURN DEP_PACKAGE.F(10); END");
		}
		var reader = dialect.getCatalogReader().getSchemaReader();
		boolean exclude = reader.getReaderOptions().isExcludeSystemObjects();
		Schema schema;
		try {
			reader.getReaderOptions().setExcludeSystemObjects(true);
			schema = reader.getAllFull(connection).get(0);
		} finally {
			reader.getReaderOptions().setExcludeSystemObjects(exclude);
		}
		assertFalse(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.get(schema).isEmpty());
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertEquals(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.get(schema),
				com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.get(restored));
		var create = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
		assertDependencyExecution(statement, major);
		dropDependencyFixture(statement, major);
		for (var sql : create) {
			try {
				statement.execute(sql.getSqlText());
			} catch (SQLException e) {
				throw new SQLException("Dependency fixture DDL: " + sql.getSqlText() + "\nDependencies: "
						+ com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.get(restored) + "\nPlan: "
						+ create.stream().map(operation -> operation.getSqlText()).toList(), e);
			}
		}
		assertDependencyExecution(statement, major);
		var alter = dialect.createSqlFactoryRegistry().getSqlFactory(restored, SqlType.ALTER);
		for (var sql : alter.createDiffSql(restored.diff(empty)))
			statement.execute(sql.getSqlText());
		assertTrue(readUserSchema(connection, dialect).getTables().isEmpty());
		for (var sql : alter.createDiffSql(empty.diff(restored)))
			statement.execute(sql.getSqlText());
		assertDependencyExecution(statement, major);
		for (var sql : alter.createDiffSql(restored.diff(empty)))
			statement.execute(sql.getSqlText());
		assertTrue(readUserSchema(connection, dialect).getProcedures().isEmpty());
	}

	private void circularForeignKeys(Connection connection, Statement statement, Dialect dialect) throws Exception {
		Schema empty = readUserSchema(connection, dialect);
		statement.execute("CREATE TABLE \"cycle left\" (ID INTEGER NOT NULL PRIMARY KEY, PARENT_ID INTEGER)");
		statement.execute("CREATE TABLE \"cycle right\" (ID INTEGER NOT NULL PRIMARY KEY, PARENT_ID INTEGER)");
		statement.execute(
				"ALTER TABLE \"cycle left\" ADD CONSTRAINT \"left fk\" FOREIGN KEY (PARENT_ID) REFERENCES \"cycle right\" (ID) ON DELETE SET NULL USING DESCENDING INDEX \"left fk index\"");
		statement.execute(
				"ALTER TABLE \"cycle right\" ADD CONSTRAINT \"right fk\" FOREIGN KEY (PARENT_ID) REFERENCES \"cycle left\" (ID) ON UPDATE CASCADE");
		statement.execute("COMMENT ON INDEX \"left fk index\" IS 'retained foreign key index comment'");
		Schema source = readUserSchema(connection, dialect);
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(source.asXml()));
		var alter = dialect.createSqlFactoryRegistry().getSqlFactory(source, SqlType.ALTER);
		for (var operation : alter.createDiffSql(source.diff(empty)))
			statement.execute(operation.getSqlText());
		assertTrue(readUserSchema(connection, dialect).getTables().isEmpty());
		for (var operation : dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE))
			statement.execute(operation.getSqlText());
		var reread = readUserSchema(connection, dialect);
		var left = reread.getTables().get("cycle left").getConstraints().getForeignKeyConstraints().get(0);
		var right = reread.getTables().get("cycle right").getConstraints().getForeignKeyConstraints().get(0);
		assertEquals(CascadeRule.SetNull, left.getDeleteRule());
		assertEquals("left fk index", left.getSpecifics().get("firebird.indexName"));
		assertEquals("true", left.getSpecifics().get("firebird.indexDescending"));
		assertEquals(CascadeRule.Cascade, right.getUpdateRule());
		try (var rows = statement
				.executeQuery("SELECT RDB$DESCRIPTION FROM RDB$INDICES WHERE RDB$INDEX_NAME='left fk index'")) {
			assertTrue(rows.next());
			assertEquals("retained foreign key index comment", rows.getString(1));
		}
		var beforeComment = reread.getTables().get("cycle left");
		var afterComment = beforeComment.clone();
		afterComment.getConstraints().getForeignKeyConstraints().get(0).getSpecifics().put("firebird.indexComment",
				"changed index comment");
		var commentPlan = dialect.createSqlFactoryRegistry().createSql(beforeComment.diff(afterComment), SqlType.ALTER);
		assertEquals(1, commentPlan.size());
		assertEquals(SqlType.SET_COMMENT, commentPlan.get(0).getSqlType());
		statement.execute(commentPlan.get(0).getSqlText());
		for (var operation : dialect.createSqlFactoryRegistry().createSql(afterComment.diff(beforeComment),
				SqlType.ALTER))
			statement.execute(operation.getSqlText());
		statement.execute("INSERT INTO \"cycle left\" VALUES (1, NULL)");
		statement.execute("INSERT INTO \"cycle right\" VALUES (2, 1)");
		statement.execute("UPDATE \"cycle left\" SET PARENT_ID=2 WHERE ID=1");
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"cycle left\" VALUES (3, 999)"));
		assertThrows(SQLException.class, () -> statement.execute("DELETE FROM \"cycle left\" WHERE ID=1"));
		statement.execute("UPDATE \"cycle left\" SET ID=4 WHERE ID=1");
		try (var rows = statement.executeQuery("SELECT PARENT_ID FROM \"cycle right\" WHERE ID=2")) {
			assertTrue(rows.next());
			assertEquals(4, rows.getInt(1));
		}
		statement.execute("DELETE FROM \"cycle right\" WHERE ID=2");
		try (var rows = statement.executeQuery("SELECT PARENT_ID FROM \"cycle left\" WHERE ID=4")) {
			assertTrue(rows.next());
			assertNull(rows.getObject(1));
		}
		for (var operation : alter.createDiffSql(restored.diff(empty)))
			statement.execute(operation.getSqlText());
		for (var operation : alter.createDiffSql(empty.diff(restored)))
			statement.execute(operation.getSqlText());
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"cycle left\" VALUES (3, 999)"));
		assertEquals("left fk index", readUserSchema(connection, dialect).getTables().get("cycle left").getConstraints()
				.getForeignKeyConstraints().get(0).getSpecifics().get("firebird.indexName"));
		for (var operation : alter.createDiffSql(restored.diff(empty)))
			statement.execute(operation.getSqlText());
	}

	private Schema readUserSchema(Connection connection, Dialect dialect) throws Exception {
		var reader = dialect.getCatalogReader().getSchemaReader();
		boolean exclude = reader.getReaderOptions().isExcludeSystemObjects();
		try {
			reader.getReaderOptions().setExcludeSystemObjects(true);
			return reader.getAllFull(connection).get(0);
		} finally {
			reader.getReaderOptions().setExcludeSystemObjects(exclude);
		}
	}

	private void schemaAlterRetargeting(Connection connection, Statement statement, Dialect dialect) throws Exception {
		statement.execute("CREATE TABLE DEP_OLD_SOURCE (ID INTEGER)");
		statement.execute("CREATE VIEW DEP_REWIRE AS SELECT ID FROM DEP_OLD_SOURCE");
		statement.execute(
				"CREATE PROCEDURE DEP_REWIRE_PROC RETURNS(V INTEGER) AS BEGIN SELECT MAX(ID) FROM DEP_REWIRE INTO :V; SUSPEND; END");
		statement.execute("CREATE ROLE DEP_DIFF_READER");
		statement.execute("GRANT SELECT ON DEP_REWIRE TO DEP_DIFF_READER");
		Schema before = readUserSchema(connection, dialect);
		statement.execute("CREATE TABLE DEP_NEW_SOURCE (ID INTEGER)");
		statement.execute("ALTER VIEW DEP_REWIRE AS SELECT ID FROM DEP_NEW_SOURCE");
		statement.execute("DROP TABLE DEP_OLD_SOURCE");
		Schema after = readUserSchema(connection, dialect);
		statement.execute("CREATE TABLE DEP_OLD_SOURCE (ID INTEGER)");
		statement.execute("ALTER VIEW DEP_REWIRE AS SELECT ID FROM DEP_OLD_SOURCE");
		statement.execute("DROP TABLE DEP_NEW_SOURCE");
		statement.execute("INSERT INTO DEP_OLD_SOURCE VALUES(10)");
		var plan = dialect.createSqlFactoryRegistry().getSqlFactory(before, SqlType.ALTER)
				.createDiffSql(before.diff(after));
		assertTrue(plan.stream().noneMatch(
				sql -> sql.getSqlText().startsWith("DROP VIEW") || sql.getSqlText().startsWith("DROP PROCEDURE")));
		for (var sql : plan)
			statement.execute(sql.getSqlText());
		statement.execute("INSERT INTO DEP_NEW_SOURCE VALUES(20)");
		try (var rows = statement.executeQuery("SELECT V FROM DEP_REWIRE_PROC")) {
			assertTrue(rows.next());
			assertEquals(20, rows.getInt(1));
		}
		try (var rows = statement.executeQuery(
				"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='DEP_REWIRE' AND RDB$USER='DEP_DIFF_READER' AND RDB$PRIVILEGE='S'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		statement.execute("DROP PROCEDURE DEP_REWIRE_PROC");
		statement.execute("DROP VIEW DEP_REWIRE");
		statement.execute("DROP TABLE DEP_NEW_SOURCE");
		statement.execute("DROP ROLE DEP_DIFF_READER");
	}

	private void assertDependencyExecution(Statement statement, int major) throws SQLException {
		statement.execute("INSERT INTO Z_DEP_SOURCE VALUES (10)");
		try (var rows = statement.executeQuery("SELECT V FROM A_DEP_CALLER")) {
			assertTrue(rows.next());
			assertEquals(10, rows.getInt(1));
		}
		if (major >= 3) {
			statement.execute("INSERT INTO A_DEP_COMPUTED(ID) VALUES (7)");
			try (var rows = statement.executeQuery("SELECT V, A_DEP_READ(), A_DEP_PACKAGE() FROM A_DEP_COMPUTED")) {
				assertTrue(rows.next());
				assertEquals(14, rows.getInt(1));
				assertEquals(10, rows.getInt(2));
				assertEquals(11, rows.getInt(3));
			}
		}
	}

	private void dropDependencyFixture(Statement statement, int major) throws SQLException {
		statement.execute("DROP PROCEDURE A_DEP_CALLER");
		statement.execute("DROP PROCEDURE Z_DEP_CALLEE");
		if (major >= 3) {
			statement.execute("DROP FUNCTION A_DEP_READ");
			statement.execute("DROP FUNCTION A_DEP_PACKAGE");
			statement.execute("DROP PACKAGE BODY DEP_PACKAGE");
			statement.execute("DROP PACKAGE DEP_PACKAGE");
			statement.execute("DROP TABLE A_DEP_COMPUTED");
		}
		statement.execute("DROP VIEW Z_DEP_VIEW");
		statement.execute("DROP TABLE Z_DEP_SOURCE");
		statement.execute("DROP DOMAIN Z_DEP_DOMAIN");
		if (major >= 3)
			statement.execute("DROP FUNCTION Z_DEP_DOUBLE");
	}

	private void publicationPolicy(Connection connection, Statement statement, Dialect dialect) throws Exception {
		var reader = new com.sqlapp.data.db.dialect.firebird.metadata.Firebird40PublicationReader(dialect);
		for (boolean auto : List.of(false, true))
			for (boolean active : List.of(false, true)) {
				statement.execute("ALTER DATABASE DISABLE PUBLICATION");
				statement.execute("ALTER DATABASE EXCLUDE ALL FROM PUBLICATION");
				statement.execute("CREATE TABLE \"publication included\" (ID INTEGER PRIMARY KEY)");
				statement.execute("CREATE TABLE \"publication excluded\" (ID INTEGER PRIMARY KEY)");
				if (auto) {
					statement.execute("ALTER DATABASE INCLUDE ALL TO PUBLICATION");
					statement.execute("ALTER TABLE \"publication excluded\" DISABLE PUBLICATION");
				} else
					statement.execute("ALTER TABLE \"publication included\" ENABLE PUBLICATION");
				if (active)
					statement.execute("ALTER DATABASE ENABLE PUBLICATION");
				var expected = new com.sqlapp.data.db.dialect.firebird.FirebirdPublication(active, auto,
						List.of("publication included"));
				assertEquals(expected, reader.read(connection));
				var schemaReader = dialect.getCatalogReader().getSchemaReader();
				boolean exclude = schemaReader.getReaderOptions().isExcludeSystemObjects();
				Schema schema;
				try {
					schemaReader.getReaderOptions().setExcludeSystemObjects(true);
					schema = schemaReader.getAllFull(connection).get(0);
				} finally {
					schemaReader.getReaderOptions().setExcludeSystemObjects(exclude);
				}
				assertEquals(expected,
						com.sqlapp.data.db.dialect.firebird.FirebirdPublications.get(schema).orElseThrow());
				Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
				assertEquals(expected,
						com.sqlapp.data.db.dialect.firebird.FirebirdPublications.get(restored).orElseThrow());
				var create = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
				assertTrue(create.stream().noneMatch(sql -> sql.getSqlText().contains("PUBLICATION")));
				statement.execute("ALTER DATABASE DISABLE PUBLICATION");
				statement.execute("ALTER DATABASE EXCLUDE ALL FROM PUBLICATION");
				statement.execute("DROP TABLE \"publication included\"");
				statement.execute("DROP TABLE \"publication excluded\"");
				for (var sql : create)
					statement.execute(sql.getSqlText());
				for (String sql : com.sqlapp.data.db.dialect.firebird.FirebirdPublications.restoreSql(restored,
						dialect))
					statement.execute(sql);
				assertEquals(new com.sqlapp.data.db.dialect.firebird.FirebirdPublication(false, auto,
						List.of("publication included")), reader.read(connection));
				Schema inactive = restored.clone();
				com.sqlapp.data.db.dialect.firebird.FirebirdPublications.set(inactive, reader.read(connection));
				var alter = (com.sqlapp.data.db.dialect.firebird.sql.FirebirdAlterSchemaFactory) dialect
						.createSqlFactoryRegistry().getSqlFactory(inactive, SqlType.ALTER);
				for (var sql : alter.createDiffSql(inactive.diff(restored),
						com.sqlapp.data.db.dialect.firebird.FirebirdPublications.Activation.SOURCE_STATE))
					statement.execute(sql.getSqlText());
				assertEquals(expected, reader.read(connection));
				statement.execute("CREATE TABLE FB_FUTURE_PUBLICATION (ID INTEGER PRIMARY KEY)");
				assertEquals(auto, reader.read(connection).tables().contains("FB_FUTURE_PUBLICATION"));
				statement.execute("DROP TABLE FB_FUTURE_PUBLICATION");
				statement.execute("ALTER DATABASE DISABLE PUBLICATION");
				statement.execute("ALTER DATABASE EXCLUDE ALL FROM PUBLICATION");
				statement.execute("DROP TABLE \"publication included\"");
				statement.execute("DROP TABLE \"publication excluded\"");
			}
	}

	private void nativeCollations(Connection connection, Statement statement, Dialect dialect) throws Exception {
		statement.execute(
				"CREATE COLLATION \"native natural\" FOR UTF8 FROM UNICODE NO PAD CASE INSENSITIVE ACCENT INSENSITIVE 'NUMERIC-SORT=1'");
		statement.execute("COMMENT ON COLLATION \"native natural\" IS 'natural''s collation'");
		statement.execute(
				"CREATE COLLATION \"native inherited\" FOR UTF8 FROM \"native natural\" PAD SPACE CASE SENSITIVE ACCENT SENSITIVE");
		statement.execute("CREATE DOMAIN \"native text\" AS VARCHAR(30) CHARACTER SET UTF8 COLLATE \"native natural\"");
		statement.execute(
				"CREATE TABLE \"native collation table\" (\"value\" \"native text\", \"strict value\" VARCHAR(30) CHARACTER SET UTF8 COLLATE \"native inherited\")");
		statement.execute("CREATE INDEX FB_NATIVE_COLLATION_IDX ON \"native collation table\" (\"value\")");
		var schemaReader = dialect.getCatalogReader().getSchemaReader();
		boolean exclude = schemaReader.getReaderOptions().isExcludeSystemObjects();
		Schema schema;
		try {
			schemaReader.getReaderOptions().setExcludeSystemObjects(true);
			schema = schemaReader.getAllFull(connection).get(0);
		} finally {
			schemaReader.getReaderOptions().setExcludeSystemObjects(exclude);
		}
		var nativeObjects = com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(schema);
		assertEquals(2, nativeObjects.size());
		assertTrue(nativeObjects.stream().allMatch(
				object -> object.kind() == com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObject.Kind.COLLATION));
		assertEquals("native natural", schema.getDomains().get("native text").getCollation());
		assertEquals("native inherited",
				schema.getTables().get("native collation table").getColumns().get("strict value").getCollation());
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertEquals(nativeObjects, com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(restored));
		assertCollationBehavior(statement);
		assertThrows(SQLException.class, () -> statement.execute("DROP COLLATION \"native natural\""));
		statement.execute("DROP TABLE \"native collation table\"");
		statement.execute("DROP DOMAIN \"native text\"");
		for (String sql : com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.dropSql(restored))
			statement.execute(sql);
		for (var sql : dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE))
			statement.execute(sql.getSqlText());
		assertEquals(nativeObjects,
				new com.sqlapp.data.db.dialect.firebird.metadata.FirebirdSchemaObjectReader(dialect).read(connection));
		assertCollationBehavior(statement);
		statement.execute("DROP TABLE \"native collation table\"");
		statement.execute("DROP DOMAIN \"native text\"");
		for (String sql : com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.dropSql(restored))
			statement.execute(sql);
	}

	private void assertCollationBehavior(Statement statement) throws SQLException {
		for (String value : List.of("É2", "E2 ", "item10", "item2"))
			statement.execute("INSERT INTO \"native collation table\" VALUES ('" + value + "', 'É2')");
		try (var rows = statement
				.executeQuery("SELECT COUNT(*) FROM \"native collation table\" WHERE \"value\"='e2'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		try (var rows = statement
				.executeQuery("SELECT COUNT(*) FROM \"native collation table\" WHERE \"strict value\"='E2'")) {
			assertTrue(rows.next());
			assertEquals(0, rows.getInt(1));
		}
		try (var rows = statement
				.executeQuery("SELECT COUNT(*) FROM \"native collation table\" WHERE \"strict value\"='É2 '")) {
			assertTrue(rows.next());
			assertEquals(4, rows.getInt(1));
		}
		try (var rows = statement.executeQuery(
				"SELECT \"value\" FROM \"native collation table\" WHERE \"value\" STARTING WITH 'item' ORDER BY \"value\"")) {
			assertTrue(rows.next());
			assertEquals("item2", rows.getString(1));
			assertTrue(rows.next());
			assertEquals("item10", rows.getString(1));
			assertFalse(rows.next());
		}
	}

	private void standaloneConstraintLifecycle(Connection connection, Statement statement, Dialect dialect)
			throws Exception {
		statement.execute(
				"CREATE TABLE \"drop parent\" (ID INTEGER NOT NULL, CODE INTEGER, CONSTRAINT \"drop pk\" PRIMARY KEY(ID), CONSTRAINT \"drop unique\" UNIQUE(CODE), CONSTRAINT \"drop check\" CHECK(CODE > 0))");
		statement.execute(
				"CREATE TABLE \"drop child\" (PARENT_ID INTEGER, CONSTRAINT \"drop fk\" FOREIGN KEY(PARENT_ID) REFERENCES \"drop parent\"(ID))");
		Schema schema = SchemaUtils.readXml(new java.io.StringReader(readUserSchema(connection, dialect).asXml()));
		var parent = schema.getTables().get("drop parent");
		var child = schema.getTables().get("drop child");
		var primary = parent.getConstraints().get("drop pk");
		var unique = parent.getConstraints().get("drop unique");
		var check = parent.getConstraints().get("drop check");
		var foreign = child.getConstraints().get("drop fk");
		var registry = dialect.createSqlFactoryRegistry();
		String primaryDrop = registry.createSql(primary, SqlType.DROP).getFirst().getSqlText();
		assertFalse(primaryDrop.contains("CASCADE"));
		assertThrows(SQLException.class, () -> statement.execute(primaryDrop));
		for (Constraint constraint : List.of(foreign, primary, unique, check))
			for (var operation : registry.createSql(constraint, SqlType.DROP))
				statement.execute(operation.getSqlText());
		statement.execute("INSERT INTO \"drop parent\" VALUES(1,-1)");
		statement.execute("INSERT INTO \"drop parent\" VALUES(1,-1)");
		statement.execute("INSERT INTO \"drop child\" VALUES(999)");
		statement.execute("DELETE FROM \"drop child\"");
		statement.execute("DELETE FROM \"drop parent\"");
		for (Constraint constraint : List.of(primary, unique, check, foreign))
			for (var operation : registry.createSql(constraint, SqlType.CREATE))
				statement.execute(operation.getSqlText());
		statement.execute("INSERT INTO \"drop parent\" VALUES(1,1)");
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"drop parent\" VALUES(1,2)"));
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"drop parent\" VALUES(2,1)"));
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"drop parent\" VALUES(2,-1)"));
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"drop child\" VALUES(999)"));
		statement.execute("INSERT INTO \"drop child\" VALUES(1)");
		assertThrows(SQLException.class, () -> statement.execute(primaryDrop));
		statement.execute("DROP TABLE \"drop child\"");
		statement.execute("DROP TABLE \"drop parent\"");
	}

	private void routineTypeReferences(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		Schema empty = readUserSchema(connection, dialect);
		statement.execute("CREATE DOMAIN \"positive domain\" AS INTEGER DEFAULT 7 NOT NULL CHECK (VALUE > 0)");
		statement.execute("CREATE DOMAIN \"text domain\" AS VARCHAR(10) CHARACTER SET UTF8 COLLATE UNICODE");
		statement.execute("CREATE TABLE \"reference table\" (\"id value\" \"positive domain\")");
		statement.execute(
				"CREATE VIEW \"reference view\" (\"id alias\") AS SELECT \"id value\" FROM \"reference table\"");
		for (var declaration : List.of("P_DOMAIN(A \"positive domain\")", "P_ONLY(A TYPE OF \"positive domain\")",
				"P_COLUMN(A TYPE OF COLUMN \"reference table\".\"id value\")",
				"P_VIEW(A TYPE OF COLUMN \"reference view\".\"id alias\")",
				"P_DEFAULT(A TYPE OF \"positive domain\" DEFAULT -1)",
				"P_LOCAL(A TYPE OF \"positive domain\" NOT NULL)"))
			statement.execute("CREATE PROCEDURE " + declaration
					+ " RETURNS (R INTEGER) AS BEGIN R=COALESCE(A,-77); SUSPEND; END");
		statement.execute(
				"CREATE PROCEDURE P_OUT(A INTEGER) RETURNS (R \"positive domain\") AS BEGIN R=A; SUSPEND; END");
		statement.execute(
				"CREATE PROCEDURE P_OUT_ONLY(A INTEGER) RETURNS (R TYPE OF \"positive domain\") AS BEGIN R=A; SUSPEND; END");
		statement.execute(
				"CREATE PROCEDURE P_TEXT(A TYPE OF \"text domain\" COLLATE UNICODE_CI) RETURNS (R INTEGER) AS BEGIN IF (A = _UTF8 'A') THEN R=1; ELSE R=0; SUSPEND; END");
		if (major >= 3) {
			statement.execute("CREATE FUNCTION F_DOMAIN(A \"positive domain\") RETURNS INTEGER AS BEGIN RETURN A; END");
			statement.execute(
					"CREATE FUNCTION F_ONLY(A TYPE OF \"positive domain\") RETURNS INTEGER AS BEGIN RETURN COALESCE(A,-77); END");
			statement.execute(
					"CREATE FUNCTION F_COLUMN(A TYPE OF COLUMN \"reference view\".\"id alias\") RETURNS TYPE OF COLUMN \"reference table\".\"id value\" AS BEGIN RETURN COALESCE(A,-77); END");
			statement.execute("CREATE FUNCTION F_RET(A INTEGER) RETURNS \"positive domain\" AS BEGIN RETURN A; END");
			statement.execute(
					"CREATE FUNCTION F_RET_ONLY(A INTEGER) RETURNS TYPE OF \"positive domain\" AS BEGIN RETURN A; END");
			statement.execute(
					"CREATE FUNCTION F_TEXT(A \"text domain\") RETURNS \"text domain\" AS BEGIN RETURN A; END");
		}
		assertRoutineReferenceBehavior(statement, major);
		Schema source = readUserSchema(connection, dialect);
		var reference = com.sqlapp.data.db.dialect.firebird.FirebirdRoutineTypeReference
				.get(source.getProcedures().get("P_DOMAIN").getArguments().get(0).getSpecifics());
		assertEquals(com.sqlapp.data.db.dialect.firebird.FirebirdRoutineTypeReference.domain("positive domain"),
				reference);
		assertNull(source.getProcedures().get("P_ONLY").getArguments().get(0).getDefaultValue());
		assertFalse(source.getProcedures().get("P_ONLY").getArguments().get(0).isNotNull());
		assertTrue(source.getProcedures().get("P_LOCAL").getArguments().get(0).isNotNull());
		assertEquals("UNICODE_CI", source.getProcedures().get("P_TEXT").getArguments().get(0).getCollation());
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(source.asXml()));
		var registry = dialect.createSqlFactoryRegistry();
		for (var operation : registry.getSqlFactory(source, SqlType.ALTER).createDiffSql(source.diff(empty)))
			statement.execute(operation.getSqlText());
		for (var operation : registry.createSql(restored, SqlType.CREATE))
			statement.execute(operation.getSqlText());
		assertRoutineReferenceBehavior(statement, major);
		var before = readUserSchema(connection, dialect).getProcedures().get("P_DOMAIN");
		var after = before.clone();
		com.sqlapp.data.db.dialect.firebird.FirebirdRoutineTypeReference.set(after.getArguments().get(0).getSpecifics(),
				com.sqlapp.data.db.dialect.firebird.FirebirdRoutineTypeReference.typeOfDomain("positive domain"));
		for (var operation : registry.createSql(before.diff(after), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertScalar(statement, "SELECT R FROM P_DOMAIN(-1)", -1);
		for (var operation : registry.createSql(after.diff(before), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertThrows(SQLException.class, () -> scalar(statement, "SELECT R FROM P_DOMAIN(-1)"));
		for (var operation : registry.getSqlFactory(restored, SqlType.ALTER).createDiffSql(restored.diff(empty)))
			statement.execute(operation.getSqlText());
		for (var operation : registry.getSqlFactory(empty, SqlType.ALTER).createDiffSql(empty.diff(restored)))
			statement.execute(operation.getSqlText());
		assertRoutineReferenceBehavior(statement, major);
		for (var operation : registry.getSqlFactory(restored, SqlType.ALTER).createDiffSql(restored.diff(empty)))
			statement.execute(operation.getSqlText());
	}

	private void assertRoutineReferenceBehavior(Statement statement, int major) throws Exception {
		assertThrows(SQLException.class, () -> scalar(statement, "SELECT R FROM P_DOMAIN(-1)"));
		assertThrows(SQLException.class, () -> scalar(statement, "SELECT R FROM P_DOMAIN(NULL)"));
		assertThrows(SQLException.class, () -> scalar(statement, "SELECT R FROM P_OUT(-1)"));
		assertThrows(SQLException.class, () -> scalar(statement, "SELECT R FROM P_LOCAL(NULL)"));
		for (String procedure : List.of("P_ONLY", "P_COLUMN", "P_VIEW")) {
			assertScalar(statement, "SELECT R FROM " + procedure + "(-1)", -1);
			assertScalar(statement, "SELECT R FROM " + procedure + "(NULL)", -77);
		}
		assertScalar(statement, "SELECT R FROM P_DEFAULT", -1);
		assertThrows(SQLException.class, () -> scalar(statement, "SELECT R FROM P_ONLY"));
		assertScalar(statement, "SELECT R FROM P_OUT_ONLY(-1)", -1);
		assertScalar(statement, "SELECT R FROM P_TEXT(_UTF8 'a')", 1);
		if (major >= 3) {
			assertThrows(SQLException.class, () -> scalar(statement, "SELECT F_DOMAIN(-1) FROM RDB$DATABASE"));
			assertThrows(SQLException.class, () -> scalar(statement, "SELECT F_RET(-1) FROM RDB$DATABASE"));
			assertScalar(statement, "SELECT F_ONLY(-1) FROM RDB$DATABASE", -1);
			assertScalar(statement, "SELECT F_COLUMN(NULL) FROM RDB$DATABASE", -77);
			assertScalar(statement, "SELECT F_RET_ONLY(-1) FROM RDB$DATABASE", -1);
			try (var rows = statement.executeQuery("SELECT F_TEXT(_UTF8 '日本語') FROM RDB$DATABASE")) {
				assertTrue(rows.next());
				assertEquals("日本語", rows.getString(1));
			}
		}
	}

	private int scalar(Statement statement, String sql) throws SQLException {
		try (var rows = statement.executeQuery(sql)) {
			assertTrue(rows.next(), sql);
			return rows.getInt(1);
		}
	}

	private void assertScalar(Statement statement, String sql, int expected) throws SQLException {
		assertEquals(expected, scalar(statement, sql), sql);
	}

	private void nativeSchemaAlter(Connection connection, Statement statement, Dialect dialect) throws Exception {
		Schema empty = readUserSchema(connection, dialect);
		statement.execute("CREATE EXCEPTION \"alter exception\" 'original message'");
		statement.execute("COMMENT ON EXCEPTION \"alter exception\" IS 'original comment'");
		statement.execute(
				"DECLARE FILTER \"alter filter\" INPUT_TYPE 0 OUTPUT_TYPE -1003 ENTRY_POINT 'old_entry' MODULE_NAME 'sqlapp_missing_library'");
		statement.execute("CREATE COLLATION \"alter collation\" FOR UTF8 FROM UNICODE CASE INSENSITIVE");
		statement.execute("CREATE COLLATION \"free alter collation\" FOR UTF8 FROM UNICODE CASE SENSITIVE");
		statement.execute("COMMENT ON COLLATION \"alter collation\" IS 'original collation comment'");
		statement.execute(
				"CREATE DOMAIN FB_NATIVE_DOMAIN AS VARCHAR(20) CHARACTER SET UTF8 COLLATE \"alter collation\"");
		statement.execute(
				"CREATE TABLE FB_NATIVE_TABLE (V FB_NATIVE_DOMAIN, OTHER_V VARCHAR(20) CHARACTER SET UTF8 COLLATE \"alter collation\")");
		statement.execute("CREATE PROCEDURE FB_ALTER_NATIVE AS BEGIN EXCEPTION \"alter exception\"; END");
		if (connection.getMetaData().getDatabaseMajorVersion() >= 3)
			statement.execute(
					"CREATE PACKAGE FB_NATIVE_PACKAGE AS BEGIN PROCEDURE P(V VARCHAR(20) CHARACTER SET UTF8 COLLATE \"alter collation\"); END");
		Schema before = readUserSchema(connection, dialect);
		if (connection.getMetaData().getDatabaseMajorVersion() >= 3)
			assertTrue(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.get(before).stream()
					.anyMatch(edge -> edge.dependent()
							.kind() == com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.Kind.PACKAGE
							&& edge.dependent().name().equals("FB_NATIVE_PACKAGE")
							&& edge.prerequisite()
									.kind() == com.sqlapp.data.db.dialect.firebird.FirebirdSchemaDependencies.Kind.COLLATION
							&& edge.prerequisite().name().equals("alter collation")));
		int identity;
		try (var rows = statement.executeQuery(
				"SELECT RDB$EXCEPTION_NUMBER FROM RDB$EXCEPTIONS WHERE RDB$EXCEPTION_NAME='alter exception'")) {
			assertTrue(rows.next());
			identity = rows.getInt(1);
		}
		statement.execute("ALTER EXCEPTION \"alter exception\" 'changed ''message'''");
		statement.execute("COMMENT ON EXCEPTION \"alter exception\" IS NULL");
		statement.execute("DROP FILTER \"alter filter\"");
		statement.execute(
				"DECLARE FILTER \"alter filter\" INPUT_TYPE 0 OUTPUT_TYPE -1003 ENTRY_POINT 'new_entry' MODULE_NAME 'sqlapp_missing_library'");
		statement.execute("COMMENT ON FILTER \"alter filter\" IS 'new filter comment'");
		statement.execute("COMMENT ON COLLATION \"alter collation\" IS 'new collation comment'");
		statement.execute("DROP COLLATION \"free alter collation\"");
		statement.execute("CREATE COLLATION \"free alter collation\" FOR UTF8 FROM UNICODE CASE INSENSITIVE");
		Schema after = readUserSchema(connection, dialect);
		statement.execute("ALTER EXCEPTION \"alter exception\" 'original message'");
		statement.execute("COMMENT ON EXCEPTION \"alter exception\" IS 'original comment'");
		statement.execute("COMMENT ON COLLATION \"alter collation\" IS 'original collation comment'");
		statement.execute("DROP COLLATION \"free alter collation\"");
		statement.execute("CREATE COLLATION \"free alter collation\" FOR UTF8 FROM UNICODE CASE SENSITIVE");
		statement.execute("DROP FILTER \"alter filter\"");
		statement.execute(
				"DECLARE FILTER \"alter filter\" INPUT_TYPE 0 OUTPUT_TYPE -1003 ENTRY_POINT 'old_entry' MODULE_NAME 'sqlapp_missing_library'");
		var registry = dialect.createSqlFactoryRegistry();
		var plan = registry.getSqlFactory(before, SqlType.ALTER).createDiffSql(before.diff(after));
		assertFalse(plan.stream().anyMatch(operation -> operation.getSqlText().startsWith("DROP EXCEPTION")));
		for (var operation : plan)
			statement.execute(operation.getSqlText());
		try (var rows = statement.executeQuery(
				"SELECT RDB$EXCEPTION_NUMBER FROM RDB$EXCEPTIONS WHERE RDB$EXCEPTION_NAME='alter exception'")) {
			assertTrue(rows.next());
			assertEquals(identity, rows.getInt(1));
		}
		SQLException raised = assertThrows(SQLException.class,
				() -> statement.execute("EXECUTE PROCEDURE FB_ALTER_NATIVE"));
		assertTrue(raised.getMessage().contains("changed 'message'"));
		try (var rows = statement.executeQuery(
				"SELECT CASE WHEN _UTF8 'a' COLLATE \"free alter collation\" = _UTF8 'A' THEN 1 ELSE 0 END FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		assertEquals(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(after),
				com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(readUserSchema(connection, dialect)));
		var unsafe = after.clone();
		com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.set(unsafe,
				com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(after).stream()
						.filter(object -> object
								.kind() != com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObject.Kind.EXCEPTION)
						.toList());
		assertThrows(UnsupportedOperationException.class,
				() -> registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(after.diff(unsafe)));
		statement.execute("ALTER PROCEDURE FB_ALTER_NATIVE AS BEGIN END");
		statement.execute("DROP EXCEPTION \"alter exception\"");
		Schema released = readUserSchema(connection, dialect);
		for (var object : com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(after))
			if (object.kind() == com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObject.Kind.EXCEPTION)
				for (String sql : object.definition())
					statement.execute(sql);
		statement.execute("ALTER PROCEDURE FB_ALTER_NATIVE AS BEGIN EXCEPTION \"alter exception\"; END");
		for (var operation : registry.getSqlFactory(after, SqlType.ALTER).createDiffSql(after.diff(released)))
			statement.execute(operation.getSqlText());
		statement.execute("EXECUTE PROCEDURE FB_ALTER_NATIVE");
		for (var operation : registry.getSqlFactory(released, SqlType.ALTER).createDiffSql(released.diff(empty)))
			statement.execute(operation.getSqlText());
		assertTrue(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(readUserSchema(connection, dialect))
				.isEmpty());
	}

	private void nativeSchemaObjects(Connection connection, Statement statement, Dialect dialect) throws Exception {
		statement.execute("CREATE EXCEPTION \"native exception\" '''message'' with apostrophe' ");
		statement.execute("COMMENT ON EXCEPTION \"native exception\" IS 'exception''s comment'");
		statement.execute(
				"DECLARE FILTER \"native filter\" INPUT_TYPE 0 OUTPUT_TYPE -1002 ENTRY_POINT 'missing_entry' MODULE_NAME 'sqlapp_missing_library'");
		statement.execute("COMMENT ON FILTER \"native filter\" IS 'filter''s comment'");
		var reader = new com.sqlapp.data.db.dialect.firebird.metadata.FirebirdSchemaObjectReader(dialect);
		var objects = reader.read(connection);
		assertEquals(2, objects.size());
		var schemaReader = dialect.getCatalogReader().getSchemaReader();
		assertEquals(objects, com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects
				.get(schemaReader.getAllFull(connection).get(0)));
		schemaReader.getReaderOptions().setReadStatement(false);
		assertTrue(com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects
				.get(schemaReader.getAllFull(connection).get(0)).isEmpty());
		schemaReader.getReaderOptions().setReadStatement(true);
		var schema = new Schema();
		com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.set(schema, objects);
		schema.getProcedures()
				.add(new Procedure("FB_NATIVE_EXCEPTION").setStatement("BEGIN EXCEPTION \"native exception\"; END"));
		Schema restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		assertEquals(objects, com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.get(restored));
		statement.execute("CREATE PROCEDURE FB_NATIVE_EXCEPTION AS BEGIN EXCEPTION \"native exception\"; END");
		SQLException error = assertThrows(SQLException.class,
				() -> statement.execute("EXECUTE PROCEDURE FB_NATIVE_EXCEPTION"));
		assertTrue(error.getMessage().contains("'message' with apostrophe"), error.getMessage());
		assertThrows(SQLException.class, () -> statement.execute("DROP EXCEPTION \"native exception\""));
		statement.execute("DROP PROCEDURE FB_NATIVE_EXCEPTION");
		for (String sql : com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.dropSql(restored))
			statement.execute(sql);
		for (var sql : dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE))
			statement.execute(sql.getSqlText());
		assertEquals(objects, reader.read(connection));
		assertThrows(SQLException.class, () -> statement.execute("EXECUTE PROCEDURE FB_NATIVE_EXCEPTION"));
		statement.execute("DROP PROCEDURE FB_NATIVE_EXCEPTION");
		for (String sql : com.sqlapp.data.db.dialect.firebird.FirebirdSchemaObjects.dropSql(restored))
			statement.execute(sql);
	}

	private void capabilityBoundaries(Connection connection, Statement statement, Dialect dialect) throws Exception {
		statement.execute("CREATE TABLE \"capability table\" (A INTEGER, B INTEGER, PRIMARY KEY(A,B))");
		statement.execute("INSERT INTO \"capability table\" VALUES(1,1)");
		statement.execute("INSERT INTO \"capability table\" VALUES(1,2)");
		statement.execute("INSERT INTO \"capability table\" VALUES(2,1)");
		Table model = new Table("capability table");
		model.getColumns().add(new Column("A").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		model.getColumns().add(new Column("B").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		var first = model.newRow();
		first.put("A", 1);
		first.put("B", 1);
		var second = model.newRow();
		second.put("A", 2);
		second.put("B", 1);
		var columns = new com.sqlapp.data.db.sql.SqlSignature.ColumnsHolder(
				new java.util.LinkedHashSet<>(model.getColumns()), List.of(first, second));
		var membership = new com.sqlapp.util.SqlBuilder(dialect);
		var bindings = columns.addInParameters(dialect, List.of(first, second), null, membership);
		try (var query = connection.prepareStatement("SELECT COUNT(*) FROM \"capability table\" WHERE " + membership)) {
			for (int i = 0; i < bindings.getBindParameters().size(); i++)
				query.setObject(i + 1, bindings.getBindParameters().get(i).getValue());
			try (var rows = query.executeQuery()) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
		}
		var greater = new com.sqlapp.util.SqlBuilder(dialect);
		var bounds = com.sqlapp.data.db.sql.RowComparisonOperator.GREATER_THAN.addOperator(dialect, columns, first,
				null, greater);
		try (var query = connection.prepareStatement("SELECT COUNT(*) FROM \"capability table\" WHERE " + greater)) {
			for (int i = 0; i < bounds.getBindParameters().size(); i++)
				query.setObject(i + 1, bounds.getBindParameters().get(i).getValue());
			try (var rows = query.executeQuery()) {
				assertTrue(rows.next());
				assertEquals(2, rows.getInt(1));
			}
		}
		assertThrows(SQLException.class,
				() -> statement.executeQuery("SELECT A FROM \"capability table\" WHERE (A,B) IN ((1,1))"));
		for (var operator : List.of(com.sqlapp.data.db.sql.RowComparisonOperator.EQUAL,
				com.sqlapp.data.db.sql.RowComparisonOperator.GREATER_THAN_OR_EQUAL,
				com.sqlapp.data.db.sql.RowComparisonOperator.LESS_THAN_OR_EQUAL)) {
			var anchor = model.newRow();
			anchor.put("A", 1);
			anchor.put("B", 2);
			var condition = new com.sqlapp.util.SqlBuilder(dialect);
			var parameters = operator.addOperator(dialect, columns, anchor, null, condition);
			try (var query = connection
					.prepareStatement("SELECT COUNT(*) FROM \"capability table\" WHERE " + condition)) {
				for (int i = 0; i < parameters.getBindParameters().size(); i++)
					query.setObject(i + 1, parameters.getBindParameters().get(i).getValue());
				try (var rows = query.executeQuery()) {
					assertTrue(rows.next());
					assertEquals(operator == com.sqlapp.data.db.sql.RowComparisonOperator.EQUAL ? 1 : 2,
							rows.getInt(1));
				}
			}
		}
		assertThrows(SQLException.class,
				() -> statement.executeQuery("SELECT A FROM \"capability table\" WHERE (A,B)>(1,1)"));
		assertTrue(dialect.supportsWith());
		try (var rows = statement.executeQuery("WITH C AS (SELECT 1 AS V FROM RDB$DATABASE) SELECT V FROM C")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		statement.execute("CREATE VIEW FB_CAPABILITY_DEP AS SELECT A FROM \"capability table\"");
		String drop = dialect.createSqlFactoryRegistry().createSql(model, SqlType.DROP).get(0).getSqlText();
		assertFalse(drop.contains("CASCADE"));
		assertThrows(SQLException.class, () -> statement.execute(drop));
		assertRowCount(statement, "\"capability table\"", 3);
		statement.execute("DROP VIEW FB_CAPABILITY_DEP");
		statement.execute(drop);
		var modern = dialect.getCatalogReader().getTableSpaceReader().getAll(connection);
		assertEquals(1, modern.size());
		var legacyReader = new com.sqlapp.data.db.dialect.firebird.metadata.FirebirdTableSpaceReader(dialect) {
			@Override
			protected com.sqlapp.jdbc.sql.node.SqlNode getSqlSqlNode(
					com.sqlapp.data.schemas.ProductVersionInfo version) {
				return super.getSqlSqlNode(
						new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(2).setMinorVersion(0));
			}
		};
		legacyReader.setReaderOptions(new com.sqlapp.data.db.metadata.ReaderOptions());
		var legacy = legacyReader.getAll(connection);
		assertEquals(1, legacy.size());
		assertEquals(modern.get(0).getTableSpaceFiles().get(0).getName(),
				legacy.get(0).getTableSpaceFiles().get(0).getName());
		assertNull(legacy.get(0).getCreatedAt());
	}

	private void generatedColumnAlter(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		statement.execute(
				"CREATE TABLE \"generated alter\" (\"base value\" INTEGER, \"calc value\" INTEGER COMPUTED BY (\"base value\"+1))");
		statement.execute("INSERT INTO \"generated alter\" (\"base value\") VALUES (10)");
		statement.execute("GRANT SELECT ON \"generated alter\" TO ROLE FB_SIGNATURE_READER");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("generated alter");
		Table original = reader.getAllFull(connection).get(0);
		Table target = original.clone();
		target.getColumns().get("calc value").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDataTypeName("BIGINT").setFormula("\"base value\"*2").setRemarks("計算 ' column");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Table typed = reader.getAllFull(connection).get(0);
		assertEquals(com.sqlapp.data.db.datatype.DataType.BIGINT, typed.getColumns().get("calc value").getDataType());
		assertEquals("計算 ' column", typed.getColumns().get("calc value").getRemarks());
		statement.execute("CREATE VIEW FB_GENERATED_DEP AS SELECT \"calc value\" AS V FROM \"generated alter\"");
		Table changed = typed.clone();
		changed.getColumns().get("calc value").setFormula("\"base value\"*3");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(typed.diff(changed), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		try (var rows = statement.executeQuery("SELECT V FROM FB_GENERATED_DEP")) {
			assertTrue(rows.next());
			assertEquals(30L, rows.getLong(1));
		}
		try (var rows = statement.executeQuery("SELECT \"base value\" FROM \"generated alter\"")) {
			assertTrue(rows.next());
			assertEquals(10, rows.getInt(1));
		}
		try (var rows = statement.executeQuery(
				"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='generated alter' AND RDB$USER='FB_SIGNATURE_READER' AND RDB$PRIVILEGE='S'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		if (major < 4)
			return;
		statement.execute(
				"CREATE TABLE \"identity alter\" (\"id value\" INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 10), LABEL VARCHAR(20))");
		statement.execute("INSERT INTO \"identity alter\" (LABEL) VALUES ('first')");
		reader.setObjectName("identity alter");
		Table identity = reader.getAllFull(connection).get(0);
		Table always = identity.clone();
		always.getColumns().get("id value")
				.setIdentityGenerationType(com.sqlapp.data.schemas.IdentityGenerationType.Always).setIdentityStep(3)
				.setRemarks("identity changed");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(identity.diff(always), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertThrows(java.sql.SQLException.class, () -> statement
				.execute("INSERT INTO \"identity alter\" (\"id value\", LABEL) VALUES (99, 'rejected')"));
		statement.execute("INSERT INTO \"identity alter\" (LABEL) VALUES ('second')");
		Table reread = reader.getAllFull(connection).get(0);
		var id = reread.getColumns().get("id value");
		assertEquals(com.sqlapp.data.schemas.IdentityGenerationType.Always, id.getIdentityGenerationType());
		assertEquals(3L, id.getIdentityStep());
		assertEquals(identity.getColumns().get("id value").getIdentityStartValue(), id.getIdentityStartValue());
		assertEquals("identity changed", id.getRemarks());
		Table byDefault = reread.clone();
		byDefault.getColumns().get("id value")
				.setIdentityGenerationType(com.sqlapp.data.schemas.IdentityGenerationType.ByDefault)
				.setIdentityStep(-2);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(reread.diff(byDefault), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		statement.execute("INSERT INTO \"identity alter\" (LABEL) VALUES ('third')");
		try (var rows = statement.executeQuery("SELECT \"id value\" FROM \"identity alter\" ORDER BY LABEL")) {
			assertTrue(rows.next());
			assertEquals(10, rows.getInt(1));
			assertTrue(rows.next());
			assertEquals(13, rows.getInt(1));
			assertTrue(rows.next());
			assertEquals(11, rows.getInt(1));
		}
		Table beforeDrop = reader.getAllFull(connection).get(0), regular = beforeDrop.clone();
		regular.getColumns().get("id value").setIdentity(false);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(beforeDrop.diff(regular), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertFalse(reader.getAllFull(connection).get(0).getColumns().get("id value").isIdentity());
		statement.execute("INSERT INTO \"identity alter\" (\"id value\", LABEL) VALUES (100, 'manual')");
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM \"identity alter\"")) {
			assertTrue(rows.next());
			assertEquals(4, rows.getInt(1));
		}
	}

	private void filteredRoutineArguments(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		statement.execute("CREATE PROCEDURE \"filtered proc one\" (\"param one\" INTEGER DEFAULT 3) AS BEGIN END");
		statement.execute("CREATE PROCEDURE \"filtered proc two\" (\"param two\" VARCHAR(5)) AS BEGIN END");
		if (major >= 3)
			statement.execute(
					"CREATE PACKAGE \"filtered package\" AS BEGIN PROCEDURE \"filtered proc two\" (\"package param\" INTEGER); END");
		var procedures = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
		procedures.setObjectName("filtered proc one");
		var selected = procedures.getAllFull(connection);
		assertEquals("true", selected.get(0).getSpecifics().get("firebird.validBlr"));
		assertEquals("SYSDBA", selected.get(0).getSpecifics().get("firebird.owner"));
		assertEquals(1, selected.size());
		assertEquals("filtered proc one", selected.get(0).getName());
		assertEquals(1, selected.get(0).getArguments().size());
		assertEquals("param one", selected.get(0).getArguments().get(0).getName());
		assertEquals("3", selected.get(0).getArguments().get(0).getDefaultValue());
		// The standalone argument API must honor routineName as well as the parent
		// reader.
		var arguments = new com.sqlapp.data.db.dialect.firebird.metadata.FirebirdProcedureArgumentReader(dialect) {
		};
		arguments.setReaderOptions(procedures.getReaderOptions());
		arguments.setObjectName("filtered proc two");
		var direct = arguments.getAllFull(connection);
		assertEquals(1, direct.size());
		assertEquals("param two", direct.get(0).getName());
		assertEquals("filtered proc two", direct.get(0).getRoutineName());
		procedures.setObjectName("missing filtered procedure");
		assertTrue(procedures.getAllFull(connection).isEmpty());
		if (major >= 3) {
			statement.execute(
					"CREATE FUNCTION \"filtered func one\" (\"arg one\" INTEGER) RETURNS BIGINT AS BEGIN RETURN \"arg one\"+1; END");
			statement.execute(
					"CREATE FUNCTION \"filtered func two\" (\"arg two\" VARCHAR(5)) RETURNS VARCHAR(5) AS BEGIN RETURN \"arg two\"; END");
			var functions = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
			functions.setObjectName("filtered func one");
			var full = functions.getAllFull(connection);
			assertEquals("true", full.get(0).getSpecifics().get("firebird.validBlr"));
			assertEquals("SYSDBA", full.get(0).getSpecifics().get("firebird.owner"));
			assertEquals(1, full.size());
			assertEquals(1, full.get(0).getArguments().size());
			assertEquals("arg one", full.get(0).getArguments().get(0).getName());
			assertEquals(com.sqlapp.data.db.datatype.DataType.BIGINT, full.get(0).getReturning().getDataType());
			var functionArguments = new com.sqlapp.data.db.dialect.firebird.metadata.Firebird30FunctionArgumentReader(
					dialect) {
			};
			functionArguments.setReaderOptions(functions.getReaderOptions());
			functionArguments.setObjectName("filtered func two");
			var filtered = functionArguments.getAllFull(connection);
			assertEquals(2, filtered.size());
			assertTrue(filtered.stream().allMatch(a -> "filtered func two".equals(a.getRoutineName())));
			try (var rows = statement.executeQuery("SELECT \"filtered func one\"(4) FROM RDB$DATABASE")) {
				assertTrue(rows.next());
				assertEquals(5, rows.getLong(1));
			}
		}
	}

	private void numericIdentity(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		if (major < 3)
			return;
		Table table = new Table("numeric identity");
		table.getColumns().add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.NUMERIC).setLength(18)
				.setScale(0).setIdentity(true).setIdentityStartValue(7));
		execute(statement, dialect, table, SqlType.CREATE);
		statement.execute("INSERT INTO \"numeric identity\" DEFAULT VALUES");
		try (var rows = statement.executeQuery("SELECT ID FROM \"numeric identity\"")) {
			assertTrue(rows.next());
			assertEquals(major == 3 ? 8 : 7, rows.getLong(1));
		}
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("numeric identity");
		Table restored = reader.getAllFull(connection).get(0);
		Column id = restored.getColumns().get("ID");
		assertTrue(id.isIdentity());
		assertTrue(id.isNotNull());
		assertEquals(18L, id.getLength());
		assertEquals(0, id.getScale());
		statement.execute("DROP TABLE \"numeric identity\"");
		execute(statement, dialect, restored, SqlType.CREATE);
		statement.execute("INSERT INTO \"numeric identity\" DEFAULT VALUES");
		try (var rows = statement.executeQuery("SELECT ID FROM \"numeric identity\"")) {
			assertTrue(rows.next());
			assertEquals(major == 3 ? 8 : 7, rows.getLong(1));
		}
	}

	private void temporaryForeignKeys(Connection connection, Statement statement, Dialect dialect) throws Exception {
		Table parent = new Table("fk parent").setTableType(Table.TableType.Temporary);
		parent.getColumns()
				.add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true));
		UniqueConstraint key = new UniqueConstraint("gtt parent pk", true);
		key.getColumns().add("ID");
		parent.getConstraints().add(key);
		Table child = new Table("fk child").setTableType(Table.TableType.Temporary);
		child.getColumns().add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		parent.getSpecifics().put("firebird.onCommit", "PRESERVE ROWS");
		child.getSpecifics().put("firebird.onCommit", "PRESERVE ROWS");
		child.getConstraints().add(new CheckConstraint("gtt positive", "ID>0"));
		child.getConstraints().add(new CheckConstraint("gtt constant", "1=1"));
		ForeignKeyConstraint foreign = new ForeignKeyConstraint("gtt fk", child.getColumns().get(0),
				parent.getColumns().get(0));
		foreign.setUpdateRule(CascadeRule.Cascade).setDeleteRule(CascadeRule.Cascade);
		foreign.getSpecifics().put("firebird.indexName", "gtt fk idx");
		foreign.getSpecifics().put("firebird.indexDescending", "true");
		foreign.getSpecifics().put("firebird.indexComment", "temporary foreign key index comment");
		child.getConstraints().add(foreign);
		for (Table model : new Table[] { parent, child }) {
			com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(model,
					SqlType.CREATE_TEMPORARY);
			factory.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
			factory.getTableOptions().setTemporaryTableNameSuffix(t -> "_x");
			for (var operation : factory.createSql(model))
				statement.execute(operation.getSqlText());
		}
		assertEquals("fk parent", foreign.getRelatedTable().getName());
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"tmp_fk child_x\" VALUES(1)"));
		statement.execute("INSERT INTO \"tmp_fk parent_x\" VALUES(1)");
		statement.execute("INSERT INTO \"tmp_fk child_x\" VALUES(1)");
		statement.execute("INSERT INTO \"tmp_fk parent_x\" VALUES(0)");
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"tmp_fk child_x\" VALUES(0)"));
		statement.execute("UPDATE \"tmp_fk parent_x\" SET ID=2 WHERE ID=1");
		try (var rows = statement.executeQuery("SELECT ID FROM \"tmp_fk child_x\"")) {
			assertTrue(rows.next());
			assertEquals(2, rows.getInt(1));
		}
		statement.execute("DELETE FROM \"tmp_fk parent_x\" WHERE ID=2");
		assertRowCount(statement, "\"tmp_fk child_x\"", 0);
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("tmp_fk child_x");
		Table restored = reader.getAllFull(connection).get(0);
		assertEquals(2, restored.getConstraints().getCheckConstraints().size());
		var fk = restored.getConstraints().getForeignKeyConstraints().get(0);
		assertEquals("tmp_fk parent_x", fk.getRelatedTableName());
		assertEquals("temporary foreign key index comment", fk.getSpecifics().get("firebird.indexComment"));
		assertEquals("gtt fk idx", fk.getSpecifics().get("firebird.indexName"));
		statement.execute("DROP TABLE \"tmp_fk child_x\"");
		execute(statement, dialect, restored, SqlType.CREATE);
		assertEquals(2, reader.getAllFull(connection).get(0).getConstraints().getCheckConstraints().size());
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"tmp_fk child_x\" VALUES(99)"));
	}

	private void temporaryIndexCreation(Connection connection, Statement statement, Dialect dialect, int major,
			String url) throws Exception {
		Table table = new Table("temp indexed").setRemarks("temporary 日本語 ' table");
		table.getColumns().add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true));
		table.getColumns().add(new Column("column name").setDataType(com.sqlapp.data.db.datatype.DataType.INT)
				.setRemarks("temporary 日本語 ' column"));
		table.getSpecifics().put("firebird.onCommit", "PRESERVE ROWS");
		if (major >= 4)
			table.getSpecifics().put("firebird.sqlSecurity", "INVOKER");
		UniqueConstraint key = new UniqueConstraint("temp pk", true);
		key.getColumns().add("ID");
		key.getSpecifics().put("firebird.indexName", "temp pk index");
		table.getConstraints().add(key);
		Index primary = new Index("temp pk index");
		primary.getColumns().add("ID");
		table.getIndexes().add(primary);
		Index unique = new Index("temp unique index").setUnique(true).setEnable(false)
				.setRemarks("temporary 日本語 ' index");
		unique.getColumns().add("column name");
		table.getIndexes().add(unique);
		com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table,
				SqlType.CREATE_TEMPORARY);
		factory.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		factory.getTableOptions().setTemporaryTableNameSuffix(t -> "_suffix");
		for (var operation : factory.createSql(table))
			statement.execute(operation.getSqlText());
		assertEquals("temp indexed", table.getName());
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("tmp_temp indexed_suffix");
		Table restored = reader.getAllFull(connection).get(0);
		assertEquals(Table.TableType.Temporary, restored.getTableType());
		assertEquals(table.getRemarks(), restored.getRemarks());
		assertEquals(table.getColumns().get("column name").getRemarks(),
				restored.getColumns().get("column name").getRemarks());
		assertEquals(unique.getRemarks(), restored.getIndexes().get("temp unique index").getRemarks());
		assertFalse(restored.getIndexes().get("temp unique index").isEnable());
		if (major >= 4)
			assertEquals("INVOKER", restored.getSpecifics().get("firebird.sqlSecurity"));
		execute(statement, dialect, restored.getIndexes().get("temp unique index"), SqlType.REBUILD);
		statement.execute("INSERT INTO \"tmp_temp indexed_suffix\" VALUES(1,10)");
		assertThrows(SQLException.class,
				() -> statement.execute("INSERT INTO \"tmp_temp indexed_suffix\" VALUES(1,20)"));
		assertThrows(SQLException.class,
				() -> statement.execute("INSERT INTO \"tmp_temp indexed_suffix\" VALUES(2,10)"));
		try (Connection other = DriverManager.getConnection(url, "SYSDBA", "masterkey");
				Statement otherStatement = other.createStatement()) {
			otherStatement.execute("INSERT INTO \"tmp_temp indexed_suffix\" VALUES(1,10)");
			assertRowCount(otherStatement, "\"tmp_temp indexed_suffix\"", 1);
			assertRowCount(statement, "\"tmp_temp indexed_suffix\"", 1);
		}
	}

	private void indexStatistics(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		statement.execute("CREATE TABLE FB_STATS_PARENT (ID INTEGER PRIMARY KEY)");
		statement.execute("INSERT INTO FB_STATS_PARENT VALUES(1)");
		statement.execute(
				"CREATE TABLE \"statistics table\" (ID INTEGER NOT NULL, P INTEGER, LABEL VARCHAR(20), CONSTRAINT \"statistics pk\" PRIMARY KEY(ID), CONSTRAINT \"statistics fk\" FOREIGN KEY(P) REFERENCES FB_STATS_PARENT(ID))");
		statement.execute("CREATE INDEX \"statistics \"\" idx\" ON \"statistics table\" (P)");
		statement.execute("CREATE INDEX \"statistics expression\" ON \"statistics table\" COMPUTED BY (UPPER(LABEL))");
		statement.execute("CREATE INDEX \"statistics inactive\" ON \"statistics table\" (LABEL)");
		statement.execute("ALTER INDEX \"statistics inactive\" INACTIVE");
		statement.execute("COMMENT ON INDEX \"statistics \"\" idx\" IS 'statistics 日本語'");
		if (major >= 5)
			statement.execute("CREATE INDEX \"statistics partial\" ON \"statistics table\" (P) WHERE P IS NOT NULL");
		for (int i = 1; i <= 40; i++)
			statement.execute("INSERT INTO \"statistics table\" VALUES(" + i + ",1,'label" + i + "')");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("statistics table");
		Table table = reader.getAllFull(connection).get(0);
		var registry = dialect.createSqlFactoryRegistry();
		var operations = registry.createSql(table, SqlType.ANALYZE);
		assertEquals(major >= 5 ? 5 : 4, operations.size());
		assertEquals(operations.size(),
				operations.stream().map(operation -> operation.getSqlText()).distinct().count());
		for (var operation : operations) {
			assertTrue(operation.getSqlText().startsWith("SET STATISTICS INDEX "));
			assertFalse(operation.getSqlText().contains("statistics inactive"));
			statement.execute(operation.getSqlText());
		}
		execute(statement, dialect, table.getIndexes().get("statistics \" idx"), SqlType.ANALYZE);
		try (var rows = statement
				.executeQuery("SELECT RDB$STATISTICS FROM RDB$INDICES WHERE RDB$INDEX_NAME='statistics \" idx'")) {
			assertTrue(rows.next());
			assertEquals(1.0, rows.getDouble(1), 0.000001);
		}
		Table reread = reader.getAllFull(connection).get(0);
		assertFalse(reread.getIndexes().get("statistics inactive").isEnable());
		assertEquals("statistics 日本語", reread.getIndexes().get("statistics \" idx").getRemarks());
		assertEquals(table.getIndexes().get("statistics expression").getColumns().get(0).getName(),
				reread.getIndexes().get("statistics expression").getColumns().get(0).getName());
		assertThrows(SQLException.class,
				() -> statement.execute("INSERT INTO \"statistics table\" VALUES(1,1,'duplicate')"));
		assertThrows(SQLException.class,
				() -> statement.execute("INSERT INTO \"statistics table\" VALUES(41,2,'missing parent')"));
		assertRowCount(statement, "\"statistics table\"", 40);
	}

	private void indexMaintenance(Connection connection, Statement statement, Dialect dialect) throws Exception {
		Table model = new Table("index maintenance");
		model.getColumns().add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT).setNotNull(true));
		model.getColumns().add(new Column("V").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		UniqueConstraint key = new UniqueConstraint("maintenance pk", true);
		key.getColumns().add("ID");
		key.getSpecifics().put("firebird.indexName", "pk rebuild");
		model.getConstraints().add(key);
		Index keyIndex = new Index("pk rebuild");
		keyIndex.getColumns().add("ID");
		model.getIndexes().add(keyIndex);
		Index unique = new Index("unique rebuild").setUnique(true).setEnable(false).setRemarks("maintenance 日本語");
		unique.getColumns().add("V");
		model.getIndexes().add(unique);
		execute(statement, dialect, model, SqlType.CREATE);
		statement.execute("INSERT INTO \"index maintenance\" VALUES(1,10)");
		statement.execute("INSERT INTO \"index maintenance\" VALUES(2,20)");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("index maintenance");
		Table table = reader.getAllFull(connection).get(0);
		Index primary = new Index("pk rebuild");
		primary.getColumns().add("ID");
		table.getIndexes().add(primary);
		var registry = dialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(primary, SqlType.CREATE).isEmpty());
		execute(statement, dialect, primary, SqlType.REBUILD);
		execute(statement, dialect, primary, SqlType.ALTER);
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"index maintenance\" VALUES(1,30)"));
		assertFalse(table.getIndexes().get("unique rebuild").isEnable());
		assertEquals(unique.getRemarks(), table.getIndexes().get("unique rebuild").getRemarks());
		statement.execute("INSERT INTO \"index maintenance\" VALUES(3,10)");
		String rebuild = registry.createSql(unique, SqlType.REBUILD).get(0).getSqlText();
		assertThrows(SQLException.class, () -> statement.execute(rebuild));
		assertFalse(reader.getAllFull(connection).get(0).getIndexes().get("unique rebuild").isEnable());
		statement.execute("DELETE FROM \"index maintenance\" WHERE ID=3");
		statement.execute(rebuild);
		statement.execute(rebuild); // Already-active indexes can also be rebuilt.
		Index restored = reader.getAllFull(connection).get(0).getIndexes().get("unique rebuild");
		assertTrue(restored.isEnable());
		assertTrue(restored.isUnique());
		assertEquals(unique.getRemarks(), restored.getRemarks());
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO \"index maintenance\" VALUES(3,10)"));
		assertRowCount(statement, "\"index maintenance\"", 2);
	}

	private void deleteAllRows(Connection connection, Statement statement, Dialect dialect, String url)
			throws Exception {
		statement.execute("CREATE TABLE \"delete target\" (ID INTEGER)");
		statement.execute("CREATE TABLE \"tmp_delete target_suffix\" (ID INTEGER)");
		statement.execute("CREATE TABLE FB_DELETE_AUDIT (ID INTEGER)");
		statement.execute(
				"CREATE TRIGGER FB_DELETE_TRIGGER FOR \"delete target\" AFTER DELETE AS BEGIN INSERT INTO FB_DELETE_AUDIT VALUES(OLD.ID); END");
		statement.execute("INSERT INTO \"delete target\" VALUES(1)");
		statement.execute("INSERT INTO \"tmp_delete target_suffix\" VALUES(2)");
		com.sqlapp.data.db.sql.SqlFactory<Table> regular = dialect.createSqlFactoryRegistry()
				.getSqlFactory(new Table("delete target"), SqlType.TRUNCATE);
		regular.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		regular.getTableOptions().setTemporaryTableNameSuffix(t -> "_suffix");
		String sql = regular.createSql(new Table("delete target")).get(0).getSqlText();
		connection.setAutoCommit(false);
		try {
			statement.execute(sql);
			assertRowCount(statement, "\"delete target\"", 0);
			assertRowCount(statement, "\"tmp_delete target_suffix\"", 1);
			assertRowCount(statement, "FB_DELETE_AUDIT", 1);
			connection.rollback();
			assertRowCount(statement, "\"delete target\"", 1);
			assertRowCount(statement, "FB_DELETE_AUDIT", 0);
			statement.execute(sql);
			connection.commit();
		} finally {
			connection.rollback();
			connection.setAutoCommit(true);
		}
		assertRowCount(statement, "\"delete target\"", 0);
		Table temporary = new Table("clear temporary");
		temporary.getColumns().add(new Column("ID").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		temporary.getSpecifics().put("firebird.onCommit", "PRESERVE ROWS");
		com.sqlapp.data.db.sql.SqlFactory<Table> create = dialect.createSqlFactoryRegistry().getSqlFactory(temporary,
				SqlType.CREATE_TEMPORARY);
		create.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		create.getTableOptions().setTemporaryTableNameSuffix(t -> "_suffix");
		for (var operation : create.createSql(temporary))
			statement.execute(operation.getSqlText());
		com.sqlapp.data.db.sql.SqlFactory<Table> clear = dialect.createSqlFactoryRegistry().getSqlFactory(temporary,
				SqlType.TRUNCATE_TEMPORARY);
		clear.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		clear.getTableOptions().setTemporaryTableNameSuffix(t -> "_suffix");
		String clearSql = clear.createSql(temporary).get(0).getSqlText();
		statement.execute("INSERT INTO \"tmp_clear temporary_suffix\" VALUES(10)");
		try (Connection other = DriverManager.getConnection(url, "SYSDBA", "masterkey");
				Statement otherStatement = other.createStatement()) {
			otherStatement.execute("INSERT INTO \"tmp_clear temporary_suffix\" VALUES(20)");
			connection.setAutoCommit(false);
			try {
				statement.execute(clearSql);
				assertRowCount(statement, "\"tmp_clear temporary_suffix\"", 0);
				assertRowCount(otherStatement, "\"tmp_clear temporary_suffix\"", 1);
				connection.rollback();
				assertRowCount(statement, "\"tmp_clear temporary_suffix\"", 1);
				statement.execute(clearSql);
				connection.commit();
			} finally {
				connection.rollback();
				connection.setAutoCommit(true);
			}
			assertRowCount(statement, "\"tmp_clear temporary_suffix\"", 0);
			assertRowCount(otherStatement, "\"tmp_clear temporary_suffix\"", 1);
		}
	}

	private void assertRowCount(Statement statement, String relation, int expected) throws SQLException {
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + relation)) {
			assertTrue(rows.next());
			assertEquals(expected, rows.getInt(1));
		}
	}

	private void sequenceRecreation(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		for (int increment : major >= 3 ? new int[] { 5, -5 } : new int[] { 1 }) {
			String name = "sequence resume " + (increment > 0 ? "up" : "down");
			Sequence sequence = new Sequence(name).setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
					.setStartValue(50).setLastValue(100).setIncrementBy(increment).setRemarks("resume 日本語 ' comment");
			execute(statement, dialect, sequence, SqlType.CREATE);
			var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
			reader.setObjectName(name);
			Sequence original = reader.getAllFull(connection).get(0);
			assertEquals(java.math.BigInteger.valueOf(100), original.getLastValue());
			assertEquals(sequence.getRemarks(), original.getRemarks());
			try (var rows = statement.executeQuery("SELECT NEXT VALUE FOR \"" + name + "\" FROM RDB$DATABASE")) {
				assertTrue(rows.next());
				assertEquals(100 + increment, rows.getLong(1));
			}
			Sequence advanced = reader.getAllFull(connection).get(0);
			statement.execute("DROP SEQUENCE \"" + name + "\"");
			execute(statement, dialect, advanced, SqlType.CREATE);
			Sequence restored = reader.getAllFull(connection).get(0);
			assertEquals(advanced.getLastValue(), restored.getLastValue());
			assertEquals(advanced.getStartValue(), restored.getStartValue());
			assertEquals(advanced.getIncrementBy(), restored.getIncrementBy());
			assertEquals(advanced.getRemarks(), restored.getRemarks());
			try (var rows = statement.executeQuery("SELECT NEXT VALUE FOR \"" + name + "\" FROM RDB$DATABASE")) {
				assertTrue(rows.next());
				assertEquals(100 + 2 * increment, rows.getLong(1));
			}
			statement.execute("DROP SEQUENCE \"" + name + "\"");
			execute(statement, dialect, sequence.clone().setLastValue(0), SqlType.CREATE);
			assertEquals(java.math.BigInteger.ZERO, reader.getAllFull(connection).get(0).getLastValue());
		}
	}

	private void sequenceAlter(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		String name = "sequence alter";
		statement.execute("CREATE SEQUENCE \"sequence alter\"");
		statement.execute("SET GENERATOR \"sequence alter\" TO 90");
		statement.execute(
				"CREATE PROCEDURE FB_SEQUENCE_DEP RETURNS (V BIGINT) AS BEGIN V=GEN_ID(\"sequence alter\",1); SUSPEND; END");
		if (major >= 3)
			statement.execute("GRANT USAGE ON SEQUENCE \"sequence alter\" TO ROLE FB_SIGNATURE_READER");
		var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
		reader.setObjectName(name);
		Sequence original = reader.getAllFull(connection).get(0);
		assertEquals(java.math.BigInteger.valueOf(90), original.getLastValue());
		assertEquals(major >= 3 ? "SYSDBA" : null, original.getSpecifics().get("firebird.owner"));
		Sequence target = original.clone().setLastValue(0).setRemarks("sequence 日本語");
		if (major >= 3)
			target.setIncrementBy(-3);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Sequence restored = reader.getAllFull(connection).get(0);
		assertEquals(java.math.BigInteger.ZERO, restored.getLastValue());
		assertEquals(original.getStartValue(), restored.getStartValue());
		assertEquals(major >= 3 ? "SYSDBA" : null, restored.getSpecifics().get("firebird.owner"));
		assertEquals("sequence 日本語", restored.getRemarks());
		if (major >= 3)
			assertEquals(java.math.BigInteger.valueOf(-3), restored.getIncrementBy());
		long next = major >= 3 ? -3 : 1;
		try (var rows = statement.executeQuery("SELECT NEXT VALUE FOR \"sequence alter\" FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(next, rows.getLong(1));
		}
		// A stale metadata snapshot must not reset the now-advanced sequence on a
		// comment-only diff.
		for (var operation : dialect.createSqlFactoryRegistry()
				.createSql(restored.diff(restored.clone().setRemarks(null)), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Sequence uncommented = reader.getAllFull(connection).get(0);
		assertNull(uncommented.getRemarks());
		assertEquals(java.math.BigInteger.valueOf(next), uncommented.getLastValue());
		Sequence negative = uncommented.clone().setLastValue(-20);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(uncommented.diff(negative), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		try (var rows = statement.executeQuery("SELECT V FROM FB_SEQUENCE_DEP")) {
			assertTrue(rows.next());
			assertEquals(-19, rows.getLong(1));
		}
		if (major >= 3) {
			try (var rows = statement.executeQuery(
					"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='sequence alter' AND RDB$USER='FB_SIGNATURE_READER' AND RDB$PRIVILEGE='G'")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
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
		assertFalse(original.getColumns().get("LABEL").isNotNull());
		assertTrue(original.getColumns().get("LABEL").getDomain().isNotNull());
		assertFalse(original.getColumns().get("OPTIONAL_VALUE").isNotNull());
		assertEquals(12L, original.getColumns().get("LABEL").getLength());
		assertEquals(48L, original.getColumns().get("LABEL").getOctetLength());
		assertNull(original.getColumns().get("LABEL").getDefaultValue());
		assertEquals("'fallback'", original.getColumns().get("LABEL").getDomain().getDefaultValue());
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
		assertEquals("SYSDBA", table.getSpecifics().get("firebird.owner"));
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
			assertEquals("true", original.getSpecifics().get("firebird.validBlr"));
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

	private void packageAlter(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		if (major < 3)
			return;
		statement.execute(
				"CREATE PACKAGE \"package alter\" AS BEGIN FUNCTION V(P INTEGER DEFAULT 3) RETURNS INTEGER; END");
		statement.execute(
				"CREATE PACKAGE BODY \"package alter\" AS BEGIN FUNCTION V(P INTEGER) RETURNS INTEGER AS BEGIN RETURN P+1; END END");
		statement.execute("GRANT EXECUTE ON PACKAGE \"package alter\" TO ROLE FB_SIGNATURE_READER");
		statement.execute("CREATE VIEW FB_PACKAGE_DEP AS SELECT \"package alter\".V(10) AS V FROM RDB$DATABASE");
		var schema = dialect.getCatalogReader().getSchemaReader();
		var headers = schema.getPackageReader();
		headers.setObjectName("package alter");
		var bodies = schema.getPackageBodyReader();
		bodies.setObjectName("package alter");
		var original = headers.getAll(connection).get(0);
		var originalBody = bodies.getAll(connection).get(0);
		assertEquals("true", original.getSpecifics().get("firebird.validBody"));
		assertEquals("true", original.getSpecifics().get("firebird.hasBody"));
		var target = original.clone().setStatement("BEGIN FUNCTION V(P INTEGER DEFAULT 5) RETURNS INTEGER; END")
				.setRemarks("package 日本語");
		if (major >= 4)
			target.getSpecifics().put("firebird.sqlSecurity", "DEFINER");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		var restored = headers.getAll(connection).get(0);
		assertEquals("false", restored.getSpecifics().get("firebird.validBody"));
		assertEquals("true", restored.getSpecifics().get("firebird.hasBody"));
		assertEquals("package 日本語", restored.getRemarks());
		assertEquals(originalBody.getStatement(), bodies.getAll(connection).get(0).getStatement());
		// Header ALTER retains the source but the engine requires recompiling its body.
		execute(statement, dialect, originalBody, SqlType.ALTER);
		assertEquals("true", headers.getAll(connection).get(0).getSpecifics().get("firebird.validBody"));
		try (var rows = statement.executeQuery("SELECT \"package alter\".V() FROM RDB$DATABASE")) {
			assertTrue(rows.next());
			assertEquals(6, rows.getInt(1));
		}
		var changedBody = originalBody.clone()
				.setStatement("BEGIN FUNCTION V(P INTEGER) RETURNS INTEGER AS BEGIN RETURN P+2; END END");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(originalBody.diff(changedBody),
				SqlType.ALTER))
			statement.execute(operation.getSqlText());
		try (var rows = statement.executeQuery("SELECT V FROM FB_PACKAGE_DEP")) {
			assertTrue(rows.next());
			assertEquals(12, rows.getInt(1));
		}
		assertEquals(changedBody.getStatement(), bodies.getAll(connection).get(0).getStatement());
		for (var operation : dialect.createSqlFactoryRegistry()
				.createSql(restored.diff(restored.clone().setRemarks(null)), SqlType.ALTER)) {
			assertTrue(operation.getSqlText().startsWith("COMMENT ON"));
			statement.execute(operation.getSqlText());
		}
		var uncommented = headers.getAll(connection).get(0);
		assertNull(uncommented.getRemarks());
		if (major >= 4) {
			assertEquals("DEFINER", uncommented.getSpecifics().get("firebird.sqlSecurity"));
			var invoker = uncommented.clone();
			invoker.getSpecifics().put("firebird.sqlSecurity", "INVOKER");
			for (var operation : dialect.createSqlFactoryRegistry().createSql(uncommented.diff(invoker), SqlType.ALTER))
				statement.execute(operation.getSqlText());
			execute(statement, dialect, changedBody, SqlType.ALTER);
			var invokerRead = headers.getAll(connection).get(0);
			assertEquals("INVOKER", invokerRead.getSpecifics().get("firebird.sqlSecurity"));
			var cleared = invokerRead.clone();
			DbInfo specifics = new DbInfo();
			for (var entry : cleared.getSpecifics().entrySet())
				if (!entry.getKey().equalsIgnoreCase("firebird.sqlSecurity"))
					specifics.put(entry.getKey(), entry.getValue());
			cleared.setSpecifics(specifics);
			for (var operation : dialect.createSqlFactoryRegistry().createSql(invokerRead.diff(cleared), SqlType.ALTER))
				statement.execute(operation.getSqlText());
			execute(statement, dialect, changedBody, SqlType.ALTER);
			assertNull(headers.getAll(connection).get(0).getSpecifics().get("firebird.sqlSecurity"));
		}
		try (var rows = statement.executeQuery("SELECT V FROM FB_PACKAGE_DEP")) {
			assertTrue(rows.next());
			assertEquals(12, rows.getInt(1));
		}
		try (var rows = statement.executeQuery(
				"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='package alter' AND RDB$USER='FB_SIGNATURE_READER' AND RDB$PRIVILEGE='X'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
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
		assertEquals("true", body.getSpecifics().get("firebird.validBody"));
		assertEquals("SYSDBA", header.getSpecifics().get("firebird.owner"));
		assertEquals("SYSDBA", body.getSpecifics().get("firebird.owner"));
		assertEquals(major >= 4 ? "DEFINER" : null, header.getSpecifics().get("firebird.sqlSecurity"));
		statement.execute("DROP PACKAGE BODY FB_RECREATE_PACKAGE");
		var headerOnly = headers.getAll(connection).get(0);
		assertEquals("false", headerOnly.getSpecifics().get("firebird.hasBody"));
		assertEquals("false", headerOnly.getSpecifics().get("firebird.validBody"));
		assertTrue(bodies.getAll(connection).isEmpty(),
				"A header without a body must not produce an empty PackageBody");
		statement.execute("DROP PACKAGE FB_RECREATE_PACKAGE");
		execute(statement, dialect, header, SqlType.CREATE);
		execute(statement, dialect, body, SqlType.CREATE);
		assertEquals(header.getStatement(), headers.getAll(connection).get(0).getStatement());
		assertEquals(body.getStatement(), bodies.getAll(connection).get(0).getStatement());
		assertEquals("SYSDBA", headers.getAll(connection).get(0).getSpecifics().get("firebird.owner"));
		assertEquals("SYSDBA", bodies.getAll(connection).get(0).getSpecifics().get("firebird.owner"));
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

	private void alterObjects(Connection connection, Statement statement, Dialect dialect, int major) throws Exception {
		var schema = dialect.getCatalogReader().getSchemaReader();
		statement.execute("CREATE DOMAIN FB_ALTER_DOMAIN AS INTEGER DEFAULT 3 NOT NULL CHECK (VALUE>0)");
		statement.execute("CREATE TABLE FB_ALTER_TABLE (ID INTEGER, V FB_ALTER_DOMAIN)");
		statement.execute("INSERT INTO FB_ALTER_TABLE(ID) VALUES (1)");
		var domains = schema.getDomainReader();
		domains.setObjectName("FB_ALTER_DOMAIN");
		Domain domain = domains.getAll(connection).get(0);
		assertEquals(major >= 3 ? "SYSDBA" : null, domain.getSpecifics().get("firebird.owner"));
		Domain changedDomain = domain.clone().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDataTypeName("BIGINT").setDefaultValue("7").setCheck("VALUE>=2").setRemarks("domain changed");
		if (major >= 3)
			changedDomain.setNotNull(false);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(domain.diff(changedDomain), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Domain restored = domains.getAll(connection).get(0);
		assertEquals(major >= 3 ? "SYSDBA" : null, restored.getSpecifics().get("firebird.owner"));
		assertEquals(com.sqlapp.data.db.datatype.DataType.BIGINT, restored.getDataType());
		assertEquals("7", restored.getDefaultValue());
		assertEquals("domain changed", restored.getRemarks());
		assertEquals(major < 3, restored.isNotNull());
		statement.execute("INSERT INTO FB_ALTER_TABLE(ID) VALUES (2)");
		try (var rows = statement.executeQuery("SELECT V FROM FB_ALTER_TABLE WHERE ID=2")) {
			assertTrue(rows.next());
			assertEquals(7L, rows.getLong(1));
		}
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO FB_ALTER_TABLE VALUES(3,1)"));
		Domain cleared = restored.clone().setDefaultValue(null).setCheck(null);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(restored.diff(cleared), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertNull(domains.getAll(connection).get(0).getDefaultValue());
		assertNull(domains.getAll(connection).get(0).getCheck());
		if (major >= 3) {
			Domain before = domains.getAll(connection).get(0);
			Domain required = before.clone().setNotNull(true);
			for (var operation : dialect.createSqlFactoryRegistry().createSql(before.diff(required), SqlType.ALTER))
				statement.execute(operation.getSqlText());
			assertTrue(domains.getAll(connection).get(0).isNotNull());
		}
		domains.setObjectName("FB text domain");
		Domain characterDomain = domains.getAll(connection).get(0);
		Domain longer = characterDomain.clone().setLength(20);
		assertThrows(IllegalArgumentException.class,
				() -> dialect.createSqlFactoryRegistry().createSql(characterDomain.diff(longer), SqlType.ALTER));
		assertEquals(12L, domains.getAll(connection).get(0).getLength());
		assertEquals("UTF8", domains.getAll(connection).get(0).getCharacterSet());
		assertEquals("UNICODE_CI", domains.getAll(connection).get(0).getCollation());
		statement.execute("CREATE DOMAIN FB_DEFAULT_TEXT_DOMAIN AS VARCHAR(12) CHARACTER SET UTF8");
		domains.setObjectName("FB_DEFAULT_TEXT_DOMAIN");
		Domain defaultText = domains.getAll(connection).get(0);
		Domain extendedText = defaultText.clone().setLength(20);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(defaultText.diff(extendedText),
				SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertEquals(20L, domains.getAll(connection).get(0).getLength());
		assertEquals("UTF8", domains.getAll(connection).get(0).getCollation());
		// Keep domain nullability from masking CHECK OPTION failures on view INSERT.
		statement.execute("ALTER DOMAIN FB_ALTER_DOMAIN SET DEFAULT 7");
		statement
				.execute("CREATE VIEW FB_ALTER_VIEW (N) AS SELECT ID FROM FB_ALTER_TABLE WHERE ID>0 WITH CHECK OPTION");
		statement.execute("COMMENT ON COLUMN FB_ALTER_VIEW.N IS 'old column'");
		statement.execute("GRANT SELECT ON FB_ALTER_VIEW TO ROLE FB_SIGNATURE_READER");
		var views = schema.getViewReader();
		views.setObjectName("FB_ALTER_VIEW");
		Table view = views.getAllFull(connection).get(0);
		assertEquals("SYSDBA", view.getSpecifics().get("firebird.owner"));
		Table changedView = view.clone().setStatement("SELECT ID FROM FB_ALTER_TABLE WHERE ID>=5 WITH CHECK OPTION")
				.setRemarks("view changed");
		changedView.getColumns().get(0).setRemarks(null);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(view.diff(changedView), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Table rereadView = views.getAllFull(connection).get(0);
		assertEquals("SYSDBA", rereadView.getSpecifics().get("firebird.owner"));
		assertEquals("view changed", rereadView.getRemarks());
		assertNull(rereadView.getColumns().get(0).getRemarks());
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM FB_ALTER_VIEW")) {
			assertTrue(rows.next());
			assertEquals(0, rows.getInt(1));
		}
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO FB_ALTER_VIEW VALUES(4)"));
		statement.execute("INSERT INTO FB_ALTER_VIEW VALUES(6)");
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM FB_ALTER_VIEW")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		try (var rows = statement.executeQuery(
				"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='FB_ALTER_VIEW' AND RDB$USER='FB_SIGNATURE_READER' AND RDB$PRIVILEGE='S'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		Table checkedAlias = rereadView.clone();
		checkedAlias.getColumns().get(0).setName("checked n");
		if (major < 3)
			assertThrows(UnsupportedOperationException.class,
					() -> dialect.createSqlFactoryRegistry().createSql(rereadView.diff(checkedAlias), SqlType.ALTER));
		else {
			for (var operation : dialect.createSqlFactoryRegistry().createSql(rereadView.diff(checkedAlias),
					SqlType.ALTER))
				statement.execute(operation.getSqlText());
			assertEquals("checked n", views.getAllFull(connection).get(0).getColumns().get(0).getName());
		}
		statement.execute("CREATE VIEW FB_ALIAS_ALTER_VIEW (N) AS SELECT ID FROM FB_ALTER_TABLE");
		statement.execute("GRANT SELECT ON FB_ALIAS_ALTER_VIEW TO ROLE FB_SIGNATURE_READER");
		views.setObjectName("FB_ALIAS_ALTER_VIEW");
		Table aliasView = views.getAllFull(connection).get(0);
		Table renamedView = aliasView.clone();
		renamedView.getColumns().get(0).setName("renamed n").setRemarks("renamed column");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(aliasView.diff(renamedView), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertEquals("renamed n", views.getAllFull(connection).get(0).getColumns().get(0).getName());
		assertEquals("renamed column", views.getAllFull(connection).get(0).getColumns().get(0).getRemarks());
		try (var rows = statement.executeQuery(
				"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='FB_ALIAS_ALTER_VIEW' AND RDB$USER='FB_SIGNATURE_READER' AND RDB$PRIVILEGE='S'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
		statement.execute("CREATE INDEX FB_ALTER_INDEX ON FB_ALTER_TABLE(ID)");
		var tables = schema.getTableReader();
		tables.setObjectName("FB_ALTER_TABLE");
		Index index = tables.getAllFull(connection).get(0).getIndexes().get("FB_ALTER_INDEX");
		Index inactive = index.clone().setEnable(false).setRemarks("index changed");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(index.diff(inactive), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Index rereadIndex = tables.getAllFull(connection).get(0).getIndexes().get("FB_ALTER_INDEX");
		assertFalse(rereadIndex.isEnable());
		assertEquals("index changed", rereadIndex.getRemarks());
		Index active = rereadIndex.clone().setEnable(true);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(rereadIndex.diff(active), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		assertTrue(tables.getAllFull(connection).get(0).getIndexes().get("FB_ALTER_INDEX").isEnable());
		statement.execute("CREATE TRIGGER FB_ALTER_TRIGGER FOR FB_ALTER_TABLE ACTIVE BEFORE INSERT POSITION 7 "
				+ (major >= 4 ? "SQL SECURITY DEFINER " : "") + "AS BEGIN NEW.ID=NEW.ID+1; END");
		statement.execute("GRANT SELECT ON FB_ALTER_TABLE TO TRIGGER FB_ALTER_TRIGGER");
		var triggers = schema.getTriggerReader();
		triggers.setObjectName("FB_ALTER_TRIGGER");
		Trigger trigger = triggers.getAll(connection).get(0);
		Trigger changedTrigger = trigger.clone().setEnable(false).setStatement("BEGIN NEW.ID=NEW.ID+2; END")
				.setRemarks("trigger changed");
		changedTrigger.getSpecifics().put("POSITION", "0");
		if (major >= 4) {
			DbInfo specifics = new DbInfo();
			for (var entry : changedTrigger.getSpecifics().entrySet())
				if (!entry.getKey().equalsIgnoreCase("firebird.sqlSecurity"))
					specifics.put(entry.getKey(), entry.getValue());
			changedTrigger.setSpecifics(specifics);
		}
		for (var operation : dialect.createSqlFactoryRegistry().createSql(trigger.diff(changedTrigger), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Trigger rereadTrigger = triggers.getAll(connection).get(0);
		assertFalse(rereadTrigger.isEnable());
		assertEquals("0", rereadTrigger.getSpecifics().get("POSITION"));
		assertEquals("trigger changed", rereadTrigger.getRemarks());
		if (major >= 4)
			assertNull(rereadTrigger.getSpecifics().get("firebird.sqlSecurity"));
		statement.execute("INSERT INTO FB_ALTER_TABLE VALUES(20,7)");
		Trigger enabled = rereadTrigger.clone().setEnable(true);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(rereadTrigger.diff(enabled), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		statement.execute("INSERT INTO FB_ALTER_TABLE VALUES(30,7)");
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM FB_ALTER_TABLE WHERE ID IN (20,32)")) {
			assertTrue(rows.next());
			assertEquals(2, rows.getInt(1));
		}
		try (var rows = statement.executeQuery(
				"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='FB_ALTER_TABLE' AND RDB$USER='FB_ALTER_TRIGGER' AND RDB$PRIVILEGE='S'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
	}

	private void domainReferencesAndExternalTables(Connection connection, Statement statement, Dialect dialect)
			throws Exception {
		statement.execute("CREATE DOMAIN FB_REF_INT AS INTEGER DEFAULT 3 NOT NULL CHECK (VALUE>0)");
		statement.execute(
				"CREATE DOMAIN FB_REF_TEXT AS VARCHAR(20) CHARACTER SET UTF8 DEFAULT 'VALUE' CHECK (VALUE<>'INVALID VALUE') COLLATE UNICODE_CI");
		statement.execute("CREATE DOMAIN FB_REF_ARRAY AS INTEGER[-2:2,3:4]");
		statement.execute(
				"CREATE TABLE FB_DOMAIN_REFERENCES (A FB_REF_INT, B FB_REF_INT DEFAULT 9 NOT NULL, TXT FB_REF_TEXT DEFAULT NULL COLLATE UNICODE, ARR FB_REF_ARRAY)");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("FB_DOMAIN_REFERENCES");
		Table table = reader.getAllFull(connection).get(0);
		assertEquals("FB_REF_INT", table.getColumns().get("A").getDomainName());
		assertNull(table.getColumns().get("A").getDefaultValue());
		assertFalse(table.getColumns().get("A").isNotNull(),
				"column-local nullability must remain distinct from domain nullability");
		assertTrue(table.getColumns().get("A").getDomain().isNotNull());
		assertEquals("3", table.getColumns().get("A").getDomain().getDefaultValue());
		assertEquals("9", table.getColumns().get("B").getDefaultValue());
		assertTrue(table.getColumns().get("B").isNotNull());
		assertEquals("NULL", table.getColumns().get("TXT").getDefaultValue());
		assertEquals("UNICODE", table.getColumns().get("TXT").getCollation());
		assertEquals("UNICODE_CI", table.getColumns().get("TXT").getDomain().getCollation());
		assertEquals("FB_REF_ARRAY", table.getColumns().get("ARR").getDomainName());
		statement.execute("DROP TABLE FB_DOMAIN_REFERENCES");
		execute(statement, dialect, table, SqlType.CREATE);
		Table restored = reader.getAllFull(connection).get(0);
		assertEquals("FB_REF_ARRAY", restored.getColumns().get("ARR").getDomainName());
		assertEquals("-2:2,3:4", restored.getColumns().get("ARR").getSpecifics().get("firebird.array.bounds"));
		try (var rows = statement.executeQuery(
				"SELECT RDB$DEFAULT_SOURCE,RDB$NULL_FLAG FROM RDB$RELATION_FIELDS WHERE RDB$RELATION_NAME='FB_DOMAIN_REFERENCES' AND RDB$FIELD_NAME='A'")) {
			assertTrue(rows.next());
			assertNull(rows.getString(1));
			assertNull(rows.getObject(2));
		}
		statement.execute("ALTER DOMAIN FB_REF_INT SET DEFAULT 7");
		statement.execute("INSERT INTO FB_DOMAIN_REFERENCES DEFAULT VALUES");
		try (var rows = statement.executeQuery("SELECT A,B,TXT FROM FB_DOMAIN_REFERENCES")) {
			assertTrue(rows.next());
			assertEquals(7, rows.getInt(1));
			assertEquals(9, rows.getInt(2));
			assertNull(rows.getString(3));
		}
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO FB_DOMAIN_REFERENCES(A) VALUES(0)"));
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO FB_DOMAIN_REFERENCES(A) VALUES(NULL)"));
		assertThrows(SQLException.class,
				() -> statement.execute("INSERT INTO FB_DOMAIN_REFERENCES(TXT) VALUES('INVALID VALUE')"));
		statement.execute(
				"CREATE TABLE FB_EXTERNAL_TABLE EXTERNAL FILE '/firebird/data/sqlapp-external.dat' (V CHAR(8))");
		statement.execute("INSERT INTO FB_EXTERNAL_TABLE VALUES('persist')");
		reader.setObjectName("FB_EXTERNAL_TABLE");
		Table external = reader.getAllFull(connection).get(0);
		assertEquals(Table.TableType.File, external.getTableType());
		assertEquals("/firebird/data/sqlapp-external.dat", external.getSpecifics().get("firebird.external.file"));
		statement.execute("DROP TABLE FB_EXTERNAL_TABLE");
		execute(statement, dialect, external, SqlType.CREATE);
		try (var rows = statement.executeQuery("SELECT V FROM FB_EXTERNAL_TABLE")) {
			assertTrue(rows.next());
			assertEquals("persist", rows.getString(1).trim());
		}
		assertEquals(Table.TableType.File, reader.getAllFull(connection).get(0).getTableType());
	}

	private void tableColumnAlter(Connection connection, Statement statement, Dialect dialect, int major)
			throws Exception {
		statement.execute(
				"CREATE TABLE FB_COLUMN_ALTER (ID INTEGER NOT NULL, V INTEGER DEFAULT 3, TEXT_VALUE VARCHAR(12) CHARACTER SET UTF8)");
		statement.execute("INSERT INTO FB_COLUMN_ALTER(ID,TEXT_VALUE) VALUES(1,'before')");
		statement.execute("GRANT SELECT ON FB_COLUMN_ALTER TO ROLE FB_SIGNATURE_READER");
		var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setObjectName("FB_COLUMN_ALTER");
		Table original = reader.getAllFull(connection).get(0);
		Table renamed = original.clone();
		renamed.getColumns().get("V").setName("renamed value");
		var renameSql = dialect.createSqlFactoryRegistry().createSql(original.diff(renamed), SqlType.ALTER);
		assertTrue(renameSql.stream().noneMatch(op -> op.getSqlText().contains(" DROP ")), renameSql.toString());
		for (var operation : renameSql)
			statement.execute(operation.getSqlText());
		original = reader.getAllFull(connection).get(0);
		Table target = original.clone().setRemarks("table changed");
		target.getColumns().get("renamed value").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT)
				.setDataTypeName("BIGINT").setDefaultValue("7").setRemarks("column changed");
		target.getColumns().add(new Column("ADDED").setDataType(com.sqlapp.data.db.datatype.DataType.INT)
				.setDefaultValue("9").setRemarks("added"));
		target.getColumns().get("TEXT_VALUE").setLength(20);
		var check = new CheckConstraint("CK_COLUMN_ALTER", "ID>0");
		target.getConstraints().add(check);
		Index index = new Index("IX_COLUMN_ALTER");
		index.getColumns().add("renamed value");
		target.getIndexes().add(index);
		if (major >= 4)
			target.getSpecifics().put("firebird.sqlSecurity", "INVOKER");
		for (var operation : dialect.createSqlFactoryRegistry().createSql(original.diff(target), SqlType.ALTER)) {
			assertFalse(operation.getSqlText().startsWith("DROP TABLE"));
			statement.execute(operation.getSqlText());
		}
		Table restored = reader.getAllFull(connection).get(0);
		assertEquals("table changed", restored.getRemarks());
		assertEquals("column changed", restored.getColumns().get("renamed value").getRemarks());
		assertEquals("added", restored.getColumns().get("ADDED").getRemarks());
		assertEquals(20L, restored.getColumns().get("TEXT_VALUE").getLength());
		assertEquals("UTF8", restored.getColumns().get("TEXT_VALUE").getCharacterSet());
		assertEquals("UTF8", restored.getColumns().get("TEXT_VALUE").getCollation());
		assertEquals(com.sqlapp.data.db.datatype.DataType.BIGINT,
				restored.getColumns().get("renamed value").getDataType());
		statement.execute("INSERT INTO FB_COLUMN_ALTER(ID) VALUES(2)");
		try (var rows = statement
				.executeQuery("SELECT \"renamed value\",ADDED,TEXT_VALUE FROM FB_COLUMN_ALTER WHERE ID=1")) {
			assertTrue(rows.next());
			assertEquals(3L, rows.getLong(1));
			rows.getInt(2);
			assertTrue(rows.wasNull());
			assertEquals("before", rows.getString(3));
		}
		try (var rows = statement.executeQuery("SELECT \"renamed value\",ADDED FROM FB_COLUMN_ALTER WHERE ID=2")) {
			assertTrue(rows.next());
			assertEquals(7L, rows.getLong(1));
			assertEquals(9, rows.getInt(2));
		}
		assertThrows(SQLException.class, () -> statement.execute("INSERT INTO FB_COLUMN_ALTER(ID) VALUES(-1)"));
		assertNotNull(restored.getIndexes().get("IX_COLUMN_ALTER"));
		statement.execute("CREATE DOMAIN FB_COLUMN_DOMAIN AS BIGINT DEFAULT 11 CHECK (VALUE>0)");
		Table domainTarget = restored.clone();
		domainTarget.getColumns().get("renamed value").setDomainName("FB_COLUMN_DOMAIN").setDefaultValue(null);
		domainTarget.getIndexes().get("IX_COLUMN_ALTER").setEnable(false);
		for (var operation : dialect.createSqlFactoryRegistry().createSql(restored.diff(domainTarget), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Table domainRestored = reader.getAllFull(connection).get(0);
		assertEquals("FB_COLUMN_DOMAIN", domainRestored.getColumns().get("renamed value").getDomainName());
		assertNull(domainRestored.getColumns().get("renamed value").getDefaultValue());
		assertFalse(domainRestored.getIndexes().get("IX_COLUMN_ALTER").isEnable());
		statement.execute("INSERT INTO FB_COLUMN_ALTER(ID) VALUES(3)");
		try (var rows = statement.executeQuery("SELECT \"renamed value\" FROM FB_COLUMN_ALTER WHERE ID=3")) {
			assertTrue(rows.next());
			assertEquals(11L, rows.getLong(1));
		}
		Table removed = domainRestored.clone();
		removed.getColumns().remove("ADDED");
		if (major >= 4) {
			DbInfo specifics = new DbInfo();
			for (var entry : removed.getSpecifics().entrySet())
				if (!entry.getKey().equalsIgnoreCase("firebird.sqlSecurity"))
					specifics.put(entry.getKey(), entry.getValue());
			removed.setSpecifics(specifics);
		}
		for (var operation : dialect.createSqlFactoryRegistry().createSql(domainRestored.diff(removed), SqlType.ALTER))
			statement.execute(operation.getSqlText());
		Table withoutAdded = reader.getAllFull(connection).get(0);
		assertNull(withoutAdded.getColumns().get("ADDED"));
		if (major >= 4)
			assertNull(withoutAdded.getSpecifics().get("firebird.sqlSecurity"));
		if (major >= 3) {
			Table required = withoutAdded.clone();
			required.getColumns().get("renamed value").setNotNull(true);
			for (var operation : dialect.createSqlFactoryRegistry().createSql(withoutAdded.diff(required),
					SqlType.ALTER))
				statement.execute(operation.getSqlText());
			Table requiredRead = reader.getAllFull(connection).get(0);
			assertTrue(requiredRead.getColumns().get("renamed value").isNotNull());
			Table optional = requiredRead.clone();
			optional.getColumns().get("renamed value").setNotNull(false);
			for (var operation : dialect.createSqlFactoryRegistry().createSql(requiredRead.diff(optional),
					SqlType.ALTER))
				statement.execute(operation.getSqlText());
			assertFalse(reader.getAllFull(connection).get(0).getColumns().get("renamed value").isNotNull());
		}
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM FB_COLUMN_ALTER")) {
			assertTrue(rows.next());
			assertEquals(3, rows.getInt(1));
		}
		try (var rows = statement.executeQuery(
				"SELECT COUNT(*) FROM RDB$USER_PRIVILEGES WHERE RDB$RELATION_NAME='FB_COLUMN_ALTER' AND RDB$USER='FB_SIGNATURE_READER' AND RDB$PRIVILEGE='S'")) {
			assertTrue(rows.next());
			assertEquals(1, rows.getInt(1));
		}
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

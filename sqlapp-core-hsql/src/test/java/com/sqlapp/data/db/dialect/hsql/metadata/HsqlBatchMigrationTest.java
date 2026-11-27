/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Access;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Phase;
import com.sqlapp.data.schemas.Catalog;

class HsqlBatchMigrationTest {
	@Test
	void transitiveCrossSchemaViewsAndColumnGrantsSurviveReplacementAndRecovery() throws Exception {
		for (boolean fail : java.util.List.of(false, true))
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:batch-views-" + fail, "SA", "");
					var sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				sql.execute("CREATE SCHEMA \"Other.Schema\" AUTHORIZATION DBA");
				sql.execute("CREATE DOMAIN APP.D AS INTEGER");
				sql.execute("CREATE TABLE APP.A(ID INTEGER, V APP.D)");
				sql.execute("INSERT INTO APP.A VALUES(1,-2)");
				sql.execute("CREATE VIEW APP.Z AS SELECT ID,V FROM APP.A");
				sql.execute("CREATE VIEW \"Other.Schema\".X AS SELECT ID,V FROM APP.Z");
				sql.execute("CREATE TABLE APP.AUDIT(V INTEGER)");
				sql.execute(
						"CREATE TRIGGER APP.A_TR AFTER INSERT ON APP.A FOR EACH ROW INSERT INTO APP.AUDIT SELECT V FROM \"Other.Schema\".X");
				sql.execute(
						"CREATE TRIGGER \"Other.Schema\".TR INSTEAD OF INSERT ON \"Other.Schema\".X REFERENCING NEW ROW AS N FOR EACH ROW INSERT INTO APP.A VALUES(N.ID,N.V)");
				sql.execute("CREATE USER LIMITED PASSWORD ''");
				sql.execute("GRANT SELECT(V) ON \"Other.Schema\".X TO LIMITED");
				var source = read(connection);
				var target = source.clone();
				target.getSchemas().get("APP").getDomains().get("D").setDataTypeName("BIGINT");
				if (fail)
					target.getSchemas().get("APP").getDomains().get("D").setCheck("VALUE>0");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
				var plan = migration.planAll(source, target);
				assertEquals(2, plan.dependentViews().size());
				assertTrue(plan.replace().stream().filter(c -> c.sql().startsWith("DROP VIEW")).findFirst()
						.orElseThrow().sql().contains("\"Other.Schema\".\"X\""));
				var json = new com.sqlapp.util.JsonConverter();
				assertEquals(plan,
						json.getObjectMapper()
								.readerFor(HsqlTableMigration.BatchPlan.class).<HsqlTableMigration.BatchPlan>readValue(
										json.toJsonString(plan)));
				if (fail) {
					var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
							() -> migration.execute(connection, plan, Access.EXCLUSIVE));
					assertEquals(Phase.RESTORE, failure.getPhase());
					assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
				} else
					assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
				try (var limited = DriverManager.getConnection("jdbc:hsqldb:mem:batch-views-" + fail, "LIMITED", "");
						var query = limited.createStatement()) {
					try (var rs = query.executeQuery("SELECT V FROM \"Other.Schema\".X")) {
						assertTrue(rs.next());
						assertEquals(-2, rs.getInt(1));
					}
					assertThrows(java.sql.SQLException.class,
							() -> query.executeQuery("SELECT ID FROM \"Other.Schema\".X"));
				}
				try (var rs = sql.executeQuery("SELECT COUNT(*) FROM APP.AUDIT")) {
					assertTrue(rs.next());
					assertEquals(0, rs.getInt(1));
				}
				sql.execute("INSERT INTO \"Other.Schema\".X VALUES(2,3)");
				try (var rs = sql.executeQuery("SELECT COUNT(*) FROM APP.A")) {
					assertTrue(rs.next());
					assertEquals(2, rs.getInt(1));
				}
				try (var rs = sql.executeQuery("SELECT COUNT(*) FROM APP.AUDIT")) {
					assertTrue(rs.next());
					assertEquals(2, rs.getInt(1));
				}
			}
	}

	private Catalog read(Connection connection) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader();
		var schemas = reader.getSchemaReader();
		var catalog = new Catalog("PUBLIC");
		for (String schema : java.util.List.of("APP", "Other.Schema")) {
			schemas.setSchemaName(schema);
			catalog.getSchemas().addAll(schemas.getAllFull(connection));
		}
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(connection));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(connection));
		catalog.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(connection));
		return catalog;
	}

	@Test
	void dependentViewSchemaAndPrivilegesAreVerifiedBeforeBackup() throws Exception {
		for (boolean grant : java.util.List.of(false, true))
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:batch-view-drift-" + grant, "SA", "");
					var sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				sql.execute("CREATE SCHEMA \"Other.Schema\" AUTHORIZATION DBA");
				sql.execute("CREATE TYPE APP.M AS INTEGER");
				sql.execute("CREATE TABLE APP.A(V APP.M)");
				sql.execute("CREATE VIEW \"Other.Schema\".X AS SELECT V FROM APP.A");
				var source = read(connection);
				var target = source.clone();
				target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
				var plan = migration.planAll(source, target);
				if (grant) {
					sql.execute("CREATE ROLE READER");
					sql.execute("GRANT SELECT ON \"Other.Schema\".X TO READER");
				} else
					sql.execute("ALTER VIEW \"Other.Schema\".X AS SELECT V FROM APP.A WHERE FALSE");
				var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
						() -> migration.execute(connection, plan, Access.EXCLUSIVE));
				assertEquals(Phase.PREFLIGHT, failure.getPhase());
				assertTrue(failure.getBackups().isEmpty());
				assertThrows(java.sql.SQLException.class,
						() -> sql.executeQuery("SELECT * FROM " + plan.members().getFirst().backupTable()));
			}
	}

	@Test
	void inferredTypeUsersAndCyclicCrossSchemaKeysMigrateTogether() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:batch-types", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE SCHEMA \"Other.Schema\" AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			sql.execute(
					"CREATE TABLE APP.A(ID APP.M PRIMARY KEY, B_ID APP.M, V INTEGER, G INTEGER GENERATED ALWAYS AS(V*2))");
			sql.execute("CREATE TABLE \"Other.Schema\".B(ID APP.M PRIMARY KEY, A_ID APP.M)");
			sql.execute("ALTER TABLE APP.A ADD CONSTRAINT A_B FOREIGN KEY(B_ID) REFERENCES \"Other.Schema\".B(ID)");
			sql.execute("ALTER TABLE \"Other.Schema\".B ADD CONSTRAINT B_A FOREIGN KEY(A_ID) REFERENCES APP.A(ID)");
			sql.execute("CREATE TABLE APP.OUTSIDE(ID INTEGER REFERENCES APP.A(ID))");
			sql.execute("CREATE TABLE APP.AUDIT(V INTEGER)");
			sql.execute(
					"CREATE TRIGGER APP.TR AFTER INSERT ON APP.A REFERENCING NEW ROW AS N FOR EACH ROW INSERT INTO APP.AUDIT VALUES(N.V)");
			sql.execute("INSERT INTO APP.A VALUES(CAST(1 AS APP.M),NULL,3,DEFAULT)");
			sql.execute("INSERT INTO \"Other.Schema\".B VALUES(CAST(2 AS APP.M),CAST(1 AS APP.M))");
			sql.execute("UPDATE APP.A SET B_ID=CAST(2 AS APP.M)");
			sql.execute("INSERT INTO APP.OUTSIDE VALUES 1");
			sql.execute("CREATE USER LIMITED PASSWORD ''");
			sql.execute("GRANT SELECT(V) ON APP.A TO LIMITED");
			sql.execute("CREATE ROLE READER");
			sql.execute("GRANT USAGE ON TYPE APP.M TO READER");
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			target.getSchemas().get("APP").getTables().get("A").getColumns().get("G").setFormula("V*3");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			String original = source.asXml(), desired = target.asXml();
			var plan = migration.planAll(source, target);
			assertEquals(plan.fingerprint(), migration.planAll(source, target).fingerprint());
			assertEquals(original, source.asXml());
			assertEquals(desired, target.asXml());
			assertEquals(2, plan.members().size());
			assertEquals(2, plan.members().stream().map(HsqlTableMigration.Plan::backupTable).distinct().count());
			assertEquals(plan, new HsqlTableMigration.BatchPlan(plan.fingerprint(), plan.members(), plan.replace(),
					plan.restore(), plan.recovery(), plan.cleanup()));
			var json = new com.sqlapp.util.JsonConverter();
			var legacyJson = (tools.jackson.databind.node.ObjectNode) json.getObjectMapper()
					.readTree(json.toJsonString(plan));
			legacyJson.remove("dependentDomains");
			assertEquals(plan, json.fromJsonString(legacyJson.toString(), HsqlTableMigration.BatchPlan.class));
			assertEquals(1, plan.replace().stream().filter(c -> c.sql().startsWith("DROP TYPE")).count());
			assertFalse(plan.replace().toString().contains("CASCADE"));
			assertEquals(plan,
					json.getObjectMapper()
							.readerFor(HsqlTableMigration.BatchPlan.class).<HsqlTableMigration.BatchPlan>readValue(
									json.toJsonString(plan)));
			assertThrows(RuntimeException.class,
					() -> json.getObjectMapper().readerFor(HsqlTableMigration.BatchPlan.class)
							.readValue(json.toJsonString(plan).replace(plan.fingerprint(), "0".repeat(64))));
			assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT G,(SELECT COUNT(*) FROM APP.AUDIT) FROM APP.A")) {
				assertTrue(rs.next());
				assertEquals(9, rs.getInt(1));
				assertEquals(1, rs.getInt(2));
			}
			assertThrows(java.sql.SQLException.class, () -> sql.execute("DELETE FROM APP.A"));
			assertThrows(java.sql.SQLException.class,
					() -> sql.execute("UPDATE \"Other.Schema\".B SET A_ID=CAST(99 AS APP.M)"));
			try (var limited = DriverManager.getConnection("jdbc:hsqldb:mem:batch-types", "LIMITED", "");
					var query = limited.createStatement()) {
				try (var rs = query.executeQuery("SELECT V FROM APP.A")) {
					assertTrue(rs.next());
					assertEquals(3, rs.getInt(1));
				}
				assertThrows(java.sql.SQLException.class, () -> query.executeQuery("SELECT ID FROM APP.A"));
			}
		}
	}

	@Test
	void failedRestoreRecoversEveryTableAndOriginalDomainAfterBackupVerification() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:batch-recovery", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN APP.D AS INTEGER DEFAULT 1");
			sql.execute("CREATE TABLE APP.A(ID INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,V APP.D)");
			sql.execute("CREATE TABLE APP.B(ID INTEGER PRIMARY KEY, A_ID INTEGER REFERENCES APP.A(ID), V APP.D)");
			sql.execute("INSERT INTO APP.A(V) VALUES 2");
			sql.execute("INSERT INTO APP.B VALUES(3,0,-1)");
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getDomains().get("D").setDataTypeName("BIGINT").setCheck("VALUE > 0");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.planAll(source, target);
			assertEquals(2, plan.members().size());
			assertEquals(2, plan.members().stream().map(HsqlTableMigration.Plan::backupTable).distinct().count());
			var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE));
			assertEquals(Phase.RESTORE, failure.getPhase());
			assertEquals(2, failure.getBackups().size());
			String backup = failure.getBackups().get(1).backupTable();
			sql.execute("UPDATE " + backup + " SET V=V+100");
			assertThrows(HsqlTableMigration.BatchFailure.class,
					() -> migration.recover(connection, failure, Access.EXCLUSIVE));
			try (var rs = sql.executeQuery("SELECT COUNT(*) FROM APP.B")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
			sql.execute("UPDATE " + backup + " SET V=V-100");
			assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT V FROM APP.B")) {
				assertTrue(rs.next());
				assertEquals(-1, rs.getInt(1));
			}
			assertThrows(java.sql.SQLException.class, () -> sql.execute("DELETE FROM APP.A"));
			sql.execute("INSERT INTO APP.A(V) VALUES 4");
			try (var rs = sql.executeQuery("SELECT MAX(ID) FROM APP.A")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt(1));
			}
		}
	}

	@Test
	void metadataQueryCountDoesNotGrowWithTablesInTheSameSchema() throws Exception {
		int one = metadataQueries(1), five = metadataQueries(5);
		assertTrue(one > 0);
		assertEquals(one, five);
	}

	private int metadataQueries(int tables) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:batch-count-" + tables, "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			for (int i = 0; i < tables; i++) {
				sql.execute("CREATE TABLE APP.T" + i + "(V APP.M)");
				sql.execute("INSERT INTO APP.T" + i + " VALUES CAST(1 AS APP.M)");
				sql.execute("CREATE VIEW APP.V" + i + " AS SELECT V FROM APP.T" + i);
				sql.execute("CREATE FUNCTION APP.F" + i
						+ "() RETURNS INTEGER READS SQL DATA RETURN (SELECT COUNT(*) FROM APP.T" + i + ")");
			}
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.planAll(source, target);
			var count = new java.util.concurrent.atomic.AtomicInteger();
			var observed = (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("prepareStatement")
								&& args[0].toString().toUpperCase(java.util.Locale.ROOT).contains("INFORMATION_SCHEMA"))
							count.incrementAndGet();
						try {
							Object result = method.invoke(connection, args);
							if (method.getName().equals("createStatement")) {
								var statement = (java.sql.Statement) result;
								return java.lang.reflect.Proxy.newProxyInstance(
										java.sql.Statement.class.getClassLoader(),
										new Class<?>[] { java.sql.Statement.class }, (p, m, a) -> {
											if ((m.getName().equals("executeQuery") || m.getName().equals("execute"))
													&& a != null && a[0].toString().toUpperCase(java.util.Locale.ROOT)
															.contains("INFORMATION_SCHEMA"))
												count.incrementAndGet();
											try {
												return m.invoke(statement, a);
											} catch (java.lang.reflect.InvocationTargetException failure) {
												throw failure.getCause();
											}
										});
							}
							return result;
						} catch (java.lang.reflect.InvocationTargetException failure) {
							throw failure.getCause();
						}
					});
			assertEquals(Phase.COMPLETE, migration.execute(observed, plan, Access.EXCLUSIVE).phase());
			return count.get();
		}
	}

	@Test
	void distinctTypeReturnRoutineMigratesWithEveryTable() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:batch-restrict", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA \"Other.Schema\" AUTHORIZATION DBA");
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			sql.execute("CREATE TABLE APP.A(V APP.M)");
			sql.execute("CREATE TABLE APP.B(V APP.M)");
			sql.execute("INSERT INTO APP.A VALUES CAST(1 AS APP.M)");
			sql.execute("INSERT INTO APP.B VALUES CAST(2 AS APP.M)");
			sql.execute("CREATE FUNCTION \"Other.Schema\".F() RETURNS APP.M RETURN CAST(4 AS APP.M)");
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.planAll(source, target);
			assertEquals(1, plan.dependentRoutines().size());
			assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery(
					"SELECT CAST(A.V AS INTEGER),CAST(B.V AS INTEGER),CAST(\"Other.Schema\".F() AS INTEGER) FROM APP.A A CROSS JOIN APP.B B")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt(1));
				assertEquals(2, rs.getInt(2));
				assertEquals(4, rs.getInt(3));
			}
		}
	}

	@Test
	void lossyMemberRefusesTheWholeBatchBeforeAnyReplacement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:batch-loss", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS VARCHAR(20)");
			sql.execute("CREATE TABLE APP.A(V APP.M)");
			sql.execute("CREATE TABLE APP.B(V APP.M)");
			sql.execute("INSERT INTO APP.A VALUES CAST('1' AS APP.M)");
			sql.execute("INSERT INTO APP.B VALUES CAST('001' AS APP.M)");
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("INTEGER");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.planAll(source, target);
			var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE));
			assertEquals(Phase.VALIDATE, failure.getPhase());
			assertEquals(2, failure.getBackups().size());
			try (var rs = sql.executeQuery("SELECT CAST(V AS VARCHAR(20)) FROM APP.B")) {
				assertTrue(rs.next());
				assertEquals("001", rs.getString(1));
			}
			assertThrows(HsqlTableMigration.BatchFailure.class,
					() -> migration.recover(connection, failure, Access.EXCLUSIVE));
		}
	}
}

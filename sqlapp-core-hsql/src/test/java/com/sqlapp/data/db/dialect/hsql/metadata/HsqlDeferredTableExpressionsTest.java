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

class HsqlDeferredTableExpressionsTest {
	@Test
	void omittedSchemaExpressionOwnersAreRejectedBeforeBackup() throws Exception {
		for (int kind = 0; kind < 4; kind++)
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:expression-hidden-owner-" + kind, "SA",
					""); var sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				sql.execute("CREATE TYPE APP.M AS INTEGER");
				sql.execute("CREATE TABLE APP.A(U APP.M)");
				sql.execute(
						"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC DETERMINISTIC CONTAINS SQL RETURN CAST(CAST(N AS APP.M) AS INTEGER)+1");
				sql.execute(
						"CREATE PROCEDURE APP.P() READS SQL DATA BEGIN ATOMIC DECLARE N INTEGER; SET N=(SELECT COUNT(*) FROM APP.A); END");
				var source = read(connection);
				var target = source.clone();
				target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
				var plan = migration.planAll(source, target);
				sql.execute("CREATE SCHEMA HIDDEN AUTHORIZATION DBA");
				if (kind == 0)
					sql.execute("CREATE TABLE HIDDEN.T(V INTEGER,G INTEGER GENERATED ALWAYS AS(APP.F(V)))");
				if (kind == 1)
					sql.execute("CREATE TABLE HIDDEN.T(V INTEGER,CONSTRAINT C CHECK(APP.F(V)>V))");
				if (kind == 2)
					sql.execute("CREATE DOMAIN HIDDEN.D AS INTEGER CONSTRAINT C CHECK(APP.F(VALUE)>VALUE)");
				if (kind == 3) {
					sql.execute("CREATE TABLE HIDDEN.T(V INTEGER)");
					sql.execute("CREATE TRIGGER HIDDEN.TR AFTER INSERT ON HIDDEN.T FOR EACH ROW CALL APP.P()");
				}
				var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
						() -> migration.execute(connection, plan, Access.EXCLUSIVE));
				assertEquals(Phase.PREFLIGHT, failure.getPhase());
				assertTrue(failure.getBackups().isEmpty());
				assertThrows(java.sql.SQLException.class,
						() -> sql.executeQuery("SELECT * FROM " + plan.members().getFirst().backupTable()));
			}
	}

	@Test
	void generatedColumnsKeepTheirPureRoutinesUntilDropAndInitializeBeforeCopy() throws Exception {
		for (boolean cycle : java.util.List.of(false, true))
			for (boolean fail : java.util.List.of(false, true))
				try (var connection = DriverManager
						.getConnection("jdbc:hsqldb:mem:generated-routine-expressions-" + cycle + "-" + fail, "SA", "");
						var sql = connection.createStatement()) {
					sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
					sql.execute("CREATE TYPE APP.M AS INTEGER");
					sql.execute(
							"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC DETERMINISTIC CONTAINS SQL RETURN CAST(CAST(N AS APP.M) AS INTEGER)+1");
					if (cycle) {
						sql.execute(
								"CREATE FUNCTION APP.H(N INTEGER) RETURNS INTEGER SPECIFIC H_SPEC DETERMINISTIC CONTAINS SQL RETURN APP.F(N)");
						sql.execute(
								"ALTER SPECIFIC ROUTINE APP.F_SPEC BODY BEGIN ATOMIC IF N>0 THEN RETURN APP.H(N-1)+1; ELSE RETURN CAST(CAST(1 AS APP.M) AS INTEGER); END IF; END");
					}
					sql.execute(
							"CREATE TABLE APP.A(V INTEGER,U APP.M,G INTEGER GENERATED ALWAYS AS(APP.F(V)),CONSTRAINT C CHECK(V<APP.F(V)))");
					sql.execute("INSERT INTO APP.A(V,U) VALUES(2,CAST(20 AS APP.M)),(3,CAST(30 AS APP.M))");
					sql.execute(
							"CREATE FUNCTION APP.R() RETURNS INTEGER SPECIFIC R_SPEC READS SQL DATA RETURN (SELECT COUNT(*) FROM APP.A)");
					sql.execute("CREATE VIEW APP.V AS SELECT APP.R() AS N FROM (VALUES(0))");
					var source = read(connection);
					var target = source.clone();
					target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
					target.getSchemas().get("APP").getTables().get("A").getColumns().get("G").setFormula("APP.F(V)*2");
					if (fail)
						target.getSchemas().get("APP").getTables().get("A").getConstraints()
								.add(new com.sqlapp.data.schemas.CheckConstraint(null, "V>APP.F(V)"));
					var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
					var plan = migration.planAll(source, target);
					assertEquals(plan.fingerprint(), migration.planAll(source, target).fingerprint());
					if (fail) {
						var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
								() -> migration.execute(connection, plan, Access.EXCLUSIVE));
						assertEquals(Phase.RESTORE, failure.getPhase());
						assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
					} else
						assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
					try (var rs = sql.executeQuery("SELECT SUM(G) FROM APP.A")) {
						assertTrue(rs.next());
						assertEquals(fail ? 7 : 14, rs.getInt(1));
					}
					try (var rs = sql.executeQuery("SELECT N FROM APP.V")) {
						assertTrue(rs.next());
						assertEquals(2, rs.getInt(1));
					}
				}
	}

	@Test
	void engineRejectsDataReadingChecksAndUserRoutineDefaults() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:expression-engine-boundaries", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TABLE APP.A(V INTEGER)");
			sql.execute(
					"CREATE FUNCTION APP.READING(N INTEGER) RETURNS INTEGER DETERMINISTIC READS SQL DATA RETURN N+(SELECT COUNT(*) FROM APP.A)");
			sql.execute("CREATE FUNCTION APP.PURE(N INTEGER) RETURNS INTEGER DETERMINISTIC CONTAINS SQL RETURN N+1");
			assertTrue(assertThrows(java.sql.SQLException.class,
					() -> sql.execute("ALTER TABLE APP.A ADD CONSTRAINT C CHECK(V<APP.READING(V))")).getSQLState()
					.startsWith("42"));
			for (String expression : java.util.List.of("APP.PURE(10)", "(APP.PURE(10))"))
				assertTrue(assertThrows(java.sql.SQLException.class,
						() -> sql.execute("ALTER TABLE APP.A ALTER COLUMN V SET DEFAULT " + expression)).getSQLState()
						.startsWith("42"));
			var table = read(connection).getSchemas().get("APP").getTables().get("A");
			assertTrue(table.getConstraints().isEmpty());
			assertNull(table.getColumns().get("V").getDefaultValue());
		}
	}

	@Test
	void unselectedCheckOwnersRequireExplicitBatchSelection() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:expression-outside-table", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			sql.execute("CREATE TABLE APP.A(U APP.M)");
			sql.execute("INSERT INTO APP.A VALUES CAST(2 AS APP.M)");
			sql.execute(
					"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC DETERMINISTIC CONTAINS SQL RETURN CAST(CAST(N AS APP.M) AS INTEGER)+1");
			sql.execute("CREATE TABLE APP.OUTSIDE(V INTEGER, CONSTRAINT C CHECK(V<APP.F(V)))");
			sql.execute("INSERT INTO APP.OUTSIDE VALUES 5");
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			assertThrows(IllegalArgumentException.class, () -> migration.planAll(source, target));
			var plan = migration.planAll(source, target, target.getSchemas().get("APP").getTables(),
					HsqlTableMigration.ConversionPolicy.LOSSLESS);
			assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT V FROM APP.OUTSIDE")) {
				assertTrue(rs.next());
				assertEquals(5, rs.getInt(1));
			}
		}
	}

	private Catalog read(Connection connection) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader();
		reader.getSchemaReader().setSchemaName("APP");
		var catalog = new Catalog("PUBLIC");
		catalog.getSchemas().addAll(reader.getSchemaReader().getAllFull(connection));
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(connection));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(connection));
		catalog.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(connection));
		return catalog;
	}

	@Test
	void checksAreRestoredAfterDataAndRoutinesAndRecoverAfterValidationFailure() throws Exception {
		for (boolean fail : java.util.List.of(false, true))
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:deferred-expressions-" + fail, "SA", "");
					var sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				sql.execute("CREATE TYPE APP.M AS INTEGER");
				sql.execute("CREATE TABLE APP.A(V INTEGER,U APP.M)");
				sql.execute("INSERT INTO APP.A VALUES(2,CAST(20 AS APP.M)),(3,CAST(30 AS APP.M))");
				sql.execute(
						"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC DETERMINISTIC CONTAINS SQL RETURN CAST(CAST(N AS APP.M) AS INTEGER)+1");
				sql.execute("ALTER TABLE APP.A ADD CONSTRAINT \"Check.With.Dot\" CHECK(V<APP.F(V))");
				sql.execute("ALTER TABLE APP.A ADD CONSTRAINT C2 CHECK(APP.F(V)>0)");
				sql.execute("ALTER TABLE APP.A ALTER COLUMN V SET DEFAULT 7");
				var source = read(connection);
				var target = source.clone();
				target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
				target.getSchemas().get("APP").getTables().get("A").getConstraints()
						.add(new com.sqlapp.data.schemas.CheckConstraint(null, fail ? "V>APP.F(V)" : "V<=APP.F(V)"));
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
				String original = source.asXml(), desired = target.asXml();
				var plan = migration.planAll(source, target);
				assertEquals(original, source.asXml());
				assertEquals(desired, target.asXml());
				assertTrue(plan.replace().stream().anyMatch(c -> c.sql().contains("DROP CONSTRAINT")));
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
				try (var rs = sql.executeQuery("SELECT COUNT(*),SUM(V) FROM APP.A")) {
					assertTrue(rs.next());
					assertEquals(2, rs.getInt(1));
					assertEquals(5, rs.getInt(2));
				}
				sql.execute("INSERT INTO APP.A DEFAULT VALUES");
				try (var rs = sql.executeQuery("SELECT MAX(V) FROM APP.A")) {
					assertTrue(rs.next());
					assertEquals(7, rs.getInt(1));
				}
				var restored = read(connection);
				assertEquals(fail ? 2 : 3, restored.getSchemas().get("APP").getTables().get("A").getConstraints()
						.getCheckConstraints().size());
			}
	}
}

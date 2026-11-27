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

class HsqlRoutineMigrationTest {
	@Test
	void distinctReturnSignatureIsRetainedWhenBootstrappingMutualRecursion() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:typed-cycle-migration", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			sql.execute("CREATE TABLE APP.A(V APP.M)");
			sql.execute("INSERT INTO APP.A VALUES CAST(-2 AS APP.M)");
			sql.execute(
					"CREATE FUNCTION APP.F(N INTEGER) RETURNS APP.M SPECIFIC F_TYPED READS SQL DATA RETURN CAST(0 AS APP.M)");
			sql.execute(
					"CREATE FUNCTION OTHER.G(N INTEGER) RETURNS APP.M SPECIFIC G_TYPED READS SQL DATA RETURN APP.F(N)");
			sql.execute(
					"ALTER SPECIFIC ROUTINE APP.F_TYPED BODY BEGIN ATOMIC IF N>0 THEN RETURN CAST(CAST(OTHER.G(N-1) AS INTEGER)+(SELECT COUNT(*) FROM APP.A) AS APP.M); ELSE RETURN CAST(0 AS APP.M); END IF; END");
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.planAll(source, target);
			assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("VALUES CAST(APP.F(4) AS BIGINT)")) {
				assertTrue(rs.next());
				assertEquals(4, rs.getLong(1));
			}
			var restored = read(connection);
			for (String schema : java.util.List.of("APP", "OTHER")) {
				String ddl = String.join("\n",
						restored.getSchemas().get(schema).getFunctions().getFirst().getDefinition());
				assertTrue(ddl.matches("(?s).*RETURNS\\s+\"?APP\"?\\.\"?M\"?.*"), ddl);
			}
		}
	}

	@Test
	void unselectedTableTriggerCallingAffectedRoutineIsRejectedDuringPlanning() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:routine-outside-trigger", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			sql.execute("CREATE TABLE APP.A(V APP.M)");
			sql.execute("CREATE TABLE APP.OUTSIDE(V INTEGER)");
			sql.execute("CREATE TABLE APP.LOG(V INTEGER)");
			sql.execute(
					"CREATE PROCEDURE APP.P() READS SQL DATA BEGIN ATOMIC DECLARE N INTEGER; SET N=(SELECT COUNT(*) FROM APP.A); END");
			sql.execute("CREATE TRIGGER APP.TR AFTER INSERT ON APP.OUTSIDE FOR EACH ROW CALL APP.P()");
			var source = read(connection);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			assertTrue(assertThrows(IllegalArgumentException.class, () -> migration.planAll(source, target))
					.getMessage().contains("trigger"));
		}
	}

	@Test
	void crossSchemaDistinctSignatureRecreatesFromCompleteDefinition() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:typed-routine-source", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
			sql.execute("CREATE TYPE OTHER.\"Mixed Type\" AS INTEGER");
			sql.execute("CREATE FUNCTION APP.F(V OTHER.\"Mixed Type\") RETURNS OTHER.\"Mixed Type\" RETURN V");
			var schema = read(connection).getSchemas().get("APP");
			com.sqlapp.data.schemas.Schema restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(schema.asXml()));
			var function = restored.getFunctions().get("F");
			assertFalse(function.getDefinition().isEmpty());
			assertTrue(String.join("\n", function.getDefinition()).contains("Mixed Type"));
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			try (var target = DriverManager.getConnection("jdbc:hsqldb:mem:typed-routine-target", "SA", "");
					var statement = target.createStatement()) {
				statement.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				statement.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
				statement.execute("CREATE TYPE OTHER.\"Mixed Type\" AS INTEGER");
				for (var op : registry.createSql(function, com.sqlapp.data.db.sql.SqlType.CREATE))
					statement.execute(op.getSqlText());
				try (var rs = statement
						.executeQuery("VALUES CAST(APP.F(CAST(7 AS OTHER.\"Mixed Type\")) AS INTEGER)")) {
					assertTrue(rs.next());
					assertEquals(7, rs.getInt(1));
				}
			}
		}
	}

	@Test
	void quotedOutAndInoutArgumentsSurviveMetadataXmlAndRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:argument-source", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute(
					"CREATE PROCEDURE APP.P(INOUT \"Case Arg\" INTEGER,OUT \"Out Arg\" INTEGER) SPECIFIC P_ARGS BEGIN ATOMIC SET \"Case Arg\"=\"Case Arg\"+1; SET \"Out Arg\"=\"Case Arg\"*2; END");
			var schema = read(connection).getSchemas().get("APP");
			com.sqlapp.data.schemas.Schema restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(schema.asXml()));
			var procedure = restored.getProcedures().get("P").setDefinition((String) null);
			assertEquals(com.sqlapp.jdbc.sql.ParameterDirection.Inout,
					procedure.getArguments().get("Case Arg").getDirection());
			assertEquals(com.sqlapp.jdbc.sql.ParameterDirection.Output,
					procedure.getArguments().get("Out Arg").getDirection());
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			try (var target = DriverManager.getConnection("jdbc:hsqldb:mem:argument-target", "SA", "");
					var statement = target.createStatement()) {
				statement.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				for (var op : registry.createSql(procedure, com.sqlapp.data.db.sql.SqlType.CREATE))
					statement.execute(op.getSqlText());
				try (var call = target.prepareCall("{call APP.P(?,?)}")) {
					call.setInt(1, 7);
					call.registerOutParameter(1, java.sql.Types.INTEGER);
					call.registerOutParameter(2, java.sql.Types.INTEGER);
					call.execute();
					assertEquals(8, call.getInt(1));
					assertEquals(16, call.getInt(2));
				}
			}
		}
	}

	@Test
	void changedRoutineBodyGrantsAndUnknownCrossSchemaDependentsAreRejectedBeforeBackup() throws Exception {
		for (int change = 0; change < 4; change++)
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:routine-drift-" + change, "SA", "");
					var sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
				sql.execute("CREATE TYPE APP.M AS INTEGER");
				sql.execute("CREATE TABLE APP.A(V APP.M)");
				sql.execute(
						"CREATE FUNCTION OTHER.F() RETURNS INTEGER SPECIFIC F_SPEC READS SQL DATA RETURN (SELECT COUNT(*) FROM APP.A)");
				var source = read(connection);
				var target = source.clone();
				target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
				var plan = migration.planAll(source, target);
				if (change == 0)
					sql.execute("ALTER SPECIFIC ROUTINE OTHER.F_SPEC BODY RETURN 7");
				if (change == 1) {
					sql.execute("CREATE ROLE READER");
					sql.execute("GRANT EXECUTE ON ROUTINE OTHER.F TO READER");
				}
				if (change == 2) {
					sql.execute("CREATE SCHEMA HIDDEN AUTHORIZATION DBA");
					sql.execute("CREATE FUNCTION HIDDEN.G() RETURNS INTEGER READS SQL DATA RETURN OTHER.F()");
				}
				if (change == 3) {
					sql.execute("CREATE SCHEMA HIDDEN AUTHORIZATION DBA");
					sql.execute("CREATE FUNCTION HIDDEN.G() RETURNS APP.M RETURN CAST(1 AS APP.M)");
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
	void mutualFunctionsAndViewCyclesMigrateAndRecoverUsingFailClosedBodies() throws Exception {
		for (boolean fail : java.util.List.of(false, true))
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:routine-cycle-migration-" + fail, "SA",
					""); var sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
				sql.execute("CREATE DOMAIN APP.D AS INTEGER");
				sql.execute("CREATE TABLE APP.A(V APP.D)");
				sql.execute("INSERT INTO APP.A VALUES -2");
				sql.execute(
						"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC READS SQL DATA RETURN (SELECT COUNT(*) FROM APP.A)");
				sql.execute(
						"CREATE FUNCTION OTHER.G(N INTEGER) RETURNS INTEGER SPECIFIC G_SPEC READS SQL DATA RETURN APP.F(N)");
				sql.execute(
						"ALTER SPECIFIC ROUTINE APP.F_SPEC BODY RETURN CASE WHEN N>0 THEN OTHER.G(N-1)+(SELECT COUNT(*) FROM APP.A) ELSE 0 END");
				sql.execute("CREATE FUNCTION APP.C(X INTEGER) RETURNS INTEGER SPECIFIC C_SPEC READS SQL DATA RETURN X");
				sql.execute("CREATE VIEW OTHER.V AS SELECT APP.C(3) AS N FROM (VALUES(0))");
				sql.execute(
						"ALTER SPECIFIC ROUTINE APP.C_SPEC BODY BEGIN ATOMIC IF X<0 THEN RETURN (SELECT MAX(N) FROM OTHER.V); ELSE RETURN X+(SELECT COUNT(*) FROM APP.A); END IF; END");
				assertCycleValues(sql);
				var source = read(connection);
				var target = source.clone();
				target.getSchemas().get("APP").getDomains().get("D").setDataTypeName("BIGINT");
				if (fail)
					target.getSchemas().get("APP").getDomains().get("D").setCheck("VALUE>0");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
				var plan = migration.planAll(source, target);
				assertEquals(plan.fingerprint(), migration.planAll(source, target).fingerprint());
				assertTrue(
						plan.replace().stream().anyMatch(c -> c.sql().contains("Routine initialization incomplete")));
				if (fail) {
					var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
							() -> migration.execute(connection, plan, Access.EXCLUSIVE));
					assertEquals(Phase.RESTORE, failure.getPhase());
					assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
				} else
					assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
				assertCycleValues(sql);
				var after = read(connection);
				var desired = after.clone();
				desired.getSchemas().get("APP").getTables().get("A").getColumns().get("V").setFormula("APP.F(0)");
				assertTrue(assertThrows(IllegalArgumentException.class, () -> migration.planAll(after, desired))
						.getMessage().contains("expressions"));
			}
	}

	private void assertCycleValues(java.sql.Statement sql) throws Exception {
		try (var rs = sql.executeQuery("VALUES(APP.F(4),OTHER.G(2),APP.C(-1))")) {
			assertTrue(rs.next());
			assertEquals(4, rs.getInt(1));
			assertEquals(2, rs.getInt(2));
			assertEquals(4, rs.getInt(3));
		}
	}

	private Catalog read(Connection connection) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader();
		var catalog = new Catalog("PUBLIC");
		for (String schema : java.util.List.of("APP", "OTHER")) {
			reader.getSchemaReader().setSchemaName(schema);
			catalog.getSchemas().addAll(reader.getSchemaReader().getAllFull(connection));
		}
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(connection));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(connection));
		catalog.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(connection));
		return catalog;
	}

	@Test
	void functionsProceduresViewsTypesAndOverloadGrantsMigrateAndRecoverTogether() throws Exception {
		for (boolean fail : java.util.List.of(false, true))
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:routine-migration-" + fail, "SA", "");
					var sql = connection.createStatement()) {
				sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
				sql.execute("CREATE DOMAIN APP.D AS INTEGER");
				sql.execute("CREATE TABLE APP.A(V APP.D)");
				sql.execute("INSERT INTO APP.A VALUES -2");
				sql.execute(
						"CREATE FUNCTION APP.F() RETURNS INTEGER SPECIFIC F_READ READS SQL DATA RETURN (SELECT MAX(V) FROM APP.A)");
				sql.execute("CREATE FUNCTION APP.F(P INTEGER) RETURNS INTEGER SPECIFIC F_ARG RETURN P");
				sql.execute("CREATE VIEW OTHER.V AS SELECT APP.F() AS N FROM (VALUES(0))");
				sql.execute(
						"CREATE FUNCTION OTHER.G() RETURNS INTEGER SPECIFIC G_READ READS SQL DATA RETURN (SELECT N FROM OTHER.V)");
				sql.execute("CREATE PROCEDURE OTHER.P(OUT N INTEGER) SPECIFIC P_READ READS SQL DATA SET N=OTHER.G()");
				sql.execute("CREATE FUNCTION APP.TYPED() RETURNS APP.D SPECIFIC TYPED_SPEC RETURN CAST(4 AS APP.D)");
				sql.execute("CREATE USER LIMITED PASSWORD ''");
				sql.execute("GRANT EXECUTE ON ROUTINE APP.F TO LIMITED");
				sql.execute("GRANT EXECUTE ON ROUTINE OTHER.P TO LIMITED");
				sql.execute("GRANT EXECUTE ON ROUTINE APP.TYPED TO LIMITED");
				sql.execute("GRANT SELECT ON OTHER.V TO LIMITED");
				var source = read(connection);
				var target = source.clone();
				target.getSchemas().get("APP").getDomains().get("D").setDataTypeName("BIGINT");
				if (fail)
					target.getSchemas().get("APP").getDomains().get("D").setCheck("VALUE>0");
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
				String original = source.asXml(), desired = target.asXml();
				var plan = migration.planAll(source, target);
				assertEquals(original, source.asXml());
				assertEquals(desired, target.asXml());
				assertEquals(4, plan.dependentRoutines().size());
				assertEquals(1, plan.dependentViews().size());
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
				try (var limited = DriverManager.getConnection("jdbc:hsqldb:mem:routine-migration-" + fail, "LIMITED",
						""); var query = limited.createStatement()) {
					try (var rs = query.executeQuery("VALUES(APP.F(),APP.F(9),CAST(APP.TYPED() AS INTEGER))")) {
						assertTrue(rs.next());
						assertEquals(-2, rs.getInt(1));
						assertEquals(9, rs.getInt(2));
						assertEquals(4, rs.getInt(3));
					}
					try (var call = limited.prepareCall("{call OTHER.P(?)}")) {
						call.registerOutParameter(1, java.sql.Types.INTEGER);
						call.execute();
						assertEquals(-2, call.getInt(1));
					}
					assertThrows(java.sql.SQLException.class, () -> query.executeQuery("SELECT * FROM APP.A"));
				}
			}
	}
}

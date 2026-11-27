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

class HsqlDomainRoutineMigrationTest {
	@Test
	void domainChecksSurviveSuccessAndPartialRestoreAcrossSchemas() throws Exception {
		for (boolean fail : java.util.List.of(false, true))
			try (var c = DriverManager.getConnection("jdbc:hsqldb:mem:domain-routine-" + fail, "SA", "");
					var s = c.createStatement()) {
				s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
				s.execute("CREATE SCHEMA TYPES AUTHORIZATION DBA");
				s.execute("CREATE TYPE APP.M AS INTEGER");
				s.execute(
						"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC DETERMINISTIC CONTAINS SQL RETURN CAST(CAST(N AS APP.M) AS INTEGER)+1");
				s.execute(
						"CREATE DOMAIN TYPES.D AS INTEGER CONSTRAINT \"Check.With.Dot\" CHECK(APP.F(VALUE)>VALUE) CONSTRAINT SECOND CHECK(APP.F(VALUE)>0)");
				s.execute("CREATE TABLE APP.A(V TYPES.D,U APP.M)");
				s.execute("INSERT INTO APP.A VALUES(2,CAST(20 AS APP.M)),(3,CAST(30 AS APP.M))");
				s.execute("CREATE TABLE TYPES.UNSELECTED(V TYPES.D)");
				s.execute("INSERT INTO TYPES.UNSELECTED VALUES 7");
				var source = read(c);
				var target = source.clone();
				target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
				if (fail)
					target.getSchemas().get("APP").getTables().get("A").getConstraints()
							.add(new com.sqlapp.data.schemas.CheckConstraint("BAD", "V<0"));
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				var plan = migration.planAll(source, target);
				assertEquals(1, plan.dependentDomains().size());
				var json = new com.sqlapp.util.JsonConverter();
				plan = json.fromJsonString(json.toJsonString(plan), HsqlTableMigration.BatchPlan.class);
				final var executePlan = plan;
				if (fail) {
					var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
							() -> migration.execute(c, executePlan, Access.EXCLUSIVE));
					assertEquals(Phase.RESTORE, failure.getPhase());
					assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
				} else
					assertEquals(Phase.COMPLETE, migration.execute(c, plan, Access.EXCLUSIVE).phase());
				assertThrows(java.sql.SQLException.class, () -> s.execute("INSERT INTO TYPES.UNSELECTED VALUES -5"));
				try (var rs = s.executeQuery("SELECT SUM(V) FROM APP.A")) {
					assertTrue(rs.next());
					assertEquals(5, rs.getInt(1));
				}
				try (var rs = s.executeQuery("SELECT V FROM TYPES.UNSELECTED")) {
					assertTrue(rs.next());
					assertEquals(7, rs.getInt(1));
				}
				assertEquals(2, read(c).getSchemas().get("TYPES").getDomains().get("D").getChecks().size());
			}
	}

	@Test
	void domainRoutineDeclarationsWorkWithGeneratedColumnsAndMutualRecursion() throws Exception {
		try (var c = DriverManager.getConnection("jdbc:hsqldb:mem:domain-generated-cycle", "SA", "");
				var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE SCHEMA TYPES AUTHORIZATION DBA");
			s.execute("CREATE TYPE APP.M AS INTEGER");
			s.execute(
					"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC DETERMINISTIC CONTAINS SQL RETURN CAST(CAST(N AS APP.M) AS INTEGER)+1");
			s.execute(
					"CREATE FUNCTION APP.H(N INTEGER) RETURNS INTEGER SPECIFIC H_SPEC DETERMINISTIC CONTAINS SQL RETURN APP.F(N)");
			s.execute(
					"ALTER SPECIFIC ROUTINE APP.F_SPEC BODY BEGIN ATOMIC IF N>0 THEN RETURN APP.H(N-1)+1; ELSE RETURN CAST(CAST(1 AS APP.M) AS INTEGER); END IF; END");
			s.execute("CREATE DOMAIN TYPES.D AS INTEGER CONSTRAINT C CHECK(APP.F(VALUE)>VALUE AND VALUE>=0)");
			s.execute("CREATE TABLE APP.A(V TYPES.D,U APP.M,G INTEGER GENERATED ALWAYS AS(APP.F(V)))");
			s.execute("INSERT INTO APP.A(V,U) VALUES(2,CAST(20 AS APP.M))");
			var source = read(c);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
			assertEquals(Phase.COMPLETE,
					migration.execute(c, migration.planAll(source, target), Access.EXCLUSIVE).phase());
			try (var rs = s.executeQuery("SELECT G FROM APP.A")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
			assertThrows(java.sql.SQLException.class,
					() -> s.execute("INSERT INTO APP.A(V,U) VALUES(-2,CAST(1 AS APP.M))"));
		}
	}

	@Test
	void changedDomainOwnerFailsPreflightAndAddingCheckValidatesExistingRows() throws Exception {
		try (var c = DriverManager.getConnection("jdbc:hsqldb:mem:domain-routine-drift", "SA", "");
				var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE SCHEMA TYPES AUTHORIZATION DBA");
			s.execute("CREATE TYPE APP.M AS INTEGER");
			s.execute(
					"CREATE FUNCTION APP.F(N INTEGER) RETURNS INTEGER SPECIFIC F_SPEC DETERMINISTIC CONTAINS SQL RETURN CAST(CAST(N AS APP.M) AS INTEGER)+1");
			s.execute("CREATE DOMAIN TYPES.D AS INTEGER CONSTRAINT C CHECK(APP.F(VALUE)>0)");
			s.execute("CREATE TABLE APP.A(U APP.M)");
			s.execute("CREATE TABLE TYPES.T(V TYPES.D)");
			s.execute("INSERT INTO TYPES.T VALUES 1");
			var source = read(c);
			var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
			var plan = migration.planAll(source, target);
			s.execute("ALTER DOMAIN TYPES.D DROP CONSTRAINT C");
			s.execute("INSERT INTO TYPES.T VALUES -2");
			assertThrows(java.sql.SQLException.class,
					() -> s.execute("ALTER DOMAIN TYPES.D ADD CONSTRAINT C CHECK(APP.F(VALUE)>0)"));
			var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
					() -> migration.execute(c, plan, Access.EXCLUSIVE));
			assertEquals(Phase.PREFLIGHT, failure.getPhase());
			assertTrue(failure.getBackups().isEmpty());
		}
	}

	private Catalog read(Connection c) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader();
		var catalog = new Catalog("PUBLIC");
		for (String schema : java.util.List.of("APP", "TYPES")) {
			reader.getSchemaReader().setSchemaName(schema);
			catalog.getSchemas().addAll(reader.getSchemaReader().getAllFull(c));
		}
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(c));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(c));
		catalog.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(c));
		return catalog;
	}
}

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

class HsqlTypeReplacementTest {
	private Catalog read(Connection connection) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader();
		var catalog = new Catalog("PUBLIC");
		var schemas = reader.getSchemaReader();
		for (String schema : java.util.List.of("APP", "Types.Schema")) {
			schemas.setSchemaName(schema);
			catalog.getSchemas().addAll(schemas.getAllFull(connection));
		}
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(connection));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(connection));
		return catalog;
	}
	@Test
	void replacesDomainAndDistinctDefinitionsWithDataGrantsAndIncomingKeys() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:type-replace", "SA", ""); var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA"); sql.execute("CREATE SCHEMA \"Types.Schema\" AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN \"Types.Schema\".\"Domain.Type\" AS DECIMAL(10,2) DEFAULT 1 CONSTRAINT LOW CHECK(VALUE>0)");
			sql.execute("CREATE TYPE \"Types.Schema\".\"Distinct.Type\" AS DECIMAL(10,2)");
			sql.execute("CREATE TABLE APP.T(ID INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, D \"Types.Schema\".\"Domain.Type\", M \"Types.Schema\".\"Distinct.Type\")");
			sql.execute("CREATE INDEX APP.IDX_D ON APP.T(D)");
			sql.execute("CREATE TABLE APP.CHILD(ID INTEGER REFERENCES APP.T(ID))");
			sql.execute("CREATE ROLE READER");
			sql.execute("GRANT USAGE ON DOMAIN \"Types.Schema\".\"Domain.Type\" TO READER");
			sql.execute("GRANT USAGE ON TYPE \"Types.Schema\".\"Distinct.Type\" TO READER WITH GRANT OPTION");
			sql.execute("INSERT INTO APP.T(D,M) VALUES(1.25,CAST(2.50 AS \"Types.Schema\".\"Distinct.Type\")),(1.25,CAST(2.50 AS \"Types.Schema\".\"Distinct.Type\")),(1.25,NULL)");
			sql.execute("INSERT INTO APP.CHILD VALUES 0");
			var source = read(connection); var target = source.clone();
			target.getSchemas().get("Types.Schema").getDomains().get("Domain.Type").setDataTypeName("DECIMAL(12,3)").setDefaultValue("8");
			target.getSchemas().get("Types.Schema").getDomains().get("Domain.Type").setCheck("VALUE < 100");
			target.getSchemas().get("Types.Schema").getDomains().get("Domain.Type").setNullable(false);
			target.getSchemas().get("Types.Schema").getTypes().get("Distinct.Type").setStatement("DECIMAL(12,3)");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.plan(source, target, target.getSchemas().get("APP").getTables().get("T"));
			assertEquals(2, plan.typeReplacements().size()); assertFalse(plan.replace().toString().contains("CASCADE"));
			var json = new com.sqlapp.util.JsonConverter();
			assertEquals(plan, json.getObjectMapper().readerFor(HsqlTableMigration.Plan.class).<HsqlTableMigration.Plan>readValue(json.toJsonString(plan)));
			assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT COUNT(*),SUM(D),SUM(CAST(M AS DECIMAL(12,3))) FROM APP.T")) {
				assertTrue(rs.next()); assertEquals(3, rs.getInt(1)); assertEquals("3.750", rs.getBigDecimal(2).toPlainString()); assertEquals("5.000", rs.getBigDecimal(3).toPlainString());
			}
			assertThrows(java.sql.SQLException.class, () -> sql.execute("DELETE FROM APP.T WHERE ID=0"));
			sql.execute("INSERT INTO APP.T DEFAULT VALUES");
			try (var rs = sql.executeQuery("SELECT ID,D FROM APP.T WHERE ID=3")) { assertTrue(rs.next()); assertEquals(8, rs.getInt(2)); }
			var actual = read(connection);
			assertNotNull(actual.getSchemas().get("APP").getTables().get("T").getIndexes().get("IDX_D"));
			assertTrue(actual.getSchemas().get("Types.Schema").getTypes().get("Distinct.Type").getStatement().get(0).contains("12,3"));
			assertEquals(3, actual.getSchemas().get("Types.Schema").getDomains().get("Domain.Type").getChecks().size());
			assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO APP.T(D) VALUES 101"));
			assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO APP.T(D) VALUES NULL"));
			try (var rs = sql.executeQuery("SELECT COUNT(*) FROM " + plan.backupTable())) { assertTrue(rs.next()); assertEquals(3, rs.getInt(1)); }
		}
	}
	@Test
	void failedNewDomainChecksRecoverOriginalDefinitionAndData() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:type-replace-recovery", "SA", ""); var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN APP.D AS INTEGER DEFAULT 1 CONSTRAINT OLD_CHECK CHECK(VALUE>=-5)");
			sql.execute("CREATE TABLE APP.T(V APP.D)"); sql.execute("INSERT INTO APP.T VALUES -1,3");
			var source = read(connection); var target = source.clone();
			var domain = target.getSchemas().get("APP").getDomains().get("D");
			domain.setDataTypeName("BIGINT").setDefaultValue("8"); domain.getChecks().clear();
			domain.getChecks().add(new com.sqlapp.data.schemas.DomainCheckConstraint("POSITIVE", "VALUE>0"));
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var failure = assertThrows(HsqlTableMigration.Failure.class, () -> migration.execute(connection,
					migration.plan(source, target, target.getSchemas().get("APP").getTables().get("T")), Access.EXCLUSIVE));
			assertEquals(Phase.RESTORE, failure.getPhase());
			assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT COUNT(*),SUM(V) FROM APP.T")) { assertTrue(rs.next()); assertEquals(2, rs.getInt(1)); assertEquals(2, rs.getInt(2)); }
			var restored = read(connection).getSchemas().get("APP").getDomains().get("D");
			assertEquals("1", restored.getDefaultValue()); assertNotNull(restored.getChecks().get("OLD_CHECK"));
		}
	}
	@Test
	void restrictedTypeDropRetainsOriginalDefinitionAndRecoveryRestoresTheTable() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:type-replace-restrict", "SA", ""); var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN APP.D AS INTEGER DEFAULT 1 CONSTRAINT OLD_CHECK CHECK(VALUE>=0)");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			sql.execute("CREATE TABLE APP.T(D APP.D,V APP.M)"); sql.execute("INSERT INTO APP.T VALUES(2,CAST(3 AS APP.M))");
			sql.execute("CREATE FUNCTION APP.F() RETURNS APP.M RETURN CAST(4 AS APP.M)");
			var source = read(connection); var target = source.clone();
			target.getSchemas().get("APP").getDomains().get("D").setDataTypeName("BIGINT");
			target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var failure = assertThrows(HsqlTableMigration.Failure.class, () -> migration.execute(connection,
					migration.plan(source, target, target.getSchemas().get("APP").getTables().get("T")), Access.EXCLUSIVE));
			assertEquals(Phase.REPLACE, failure.getPhase());
			assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT D,CAST(V AS INTEGER),CAST(APP.F() AS INTEGER) FROM APP.T")) {
				assertTrue(rs.next()); assertEquals(2, rs.getInt(1)); assertEquals(3, rs.getInt(2)); assertEquals(4, rs.getInt(3));
			}
		}
	}

	@Test
	void incompleteCatalogCannotHideOtherTablesUsingTheChangedType() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:type-replace-hidden", "SA", ""); var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA"); sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER"); sql.execute("CREATE TABLE APP.T(V APP.M)");
			sql.execute("CREATE TABLE OTHER.T(V APP.M)");
			var source = read(connection); var target = source.clone(); target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.plan(source, target, target.getSchemas().get("APP").getTables().get("T"));
			var failure = assertThrows(HsqlTableMigration.Failure.class, () -> migration.execute(connection, plan, Access.EXCLUSIVE));
			assertEquals(Phase.PREFLIGHT, failure.getPhase());
			assertThrows(java.sql.SQLException.class, () -> sql.executeQuery(plan.backupCount()));
		}
	}
	@Test
	void validationUsesTheNewBaseRatherThanTheStillInstalledOldType() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:type-replace-widen", "SA", ""); var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA"); sql.execute("CREATE TYPE APP.M AS SMALLINT");
			sql.execute("CREATE TABLE APP.T(V INTEGER)"); sql.execute("INSERT INTO APP.T VALUES 100000");
			var source = read(connection); var target = source.clone(); target.getSchemas().get("APP").getTypes().get("M").setStatement("BIGINT");
			var table = target.getSchemas().get("APP").getTables().get("T"); table.getColumns().get("V").setDataTypeName("\"APP\".\"M\"");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			assertEquals(Phase.COMPLETE, migration.execute(connection, migration.plan(source, target, table), Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT CAST(V AS BIGINT) FROM APP.T")) { assertTrue(rs.next()); assertEquals(100000, rs.getLong(1)); }
		}
	}

	@Test
	void conflictingCompleteDefinitionsCannotBypassLosslessValidation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:type-replace-definition", "SA", ""); var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA"); sql.execute("CREATE TYPE APP.M AS VARCHAR(20)");
			sql.execute("CREATE TABLE APP.T(V APP.M)"); sql.execute("INSERT INTO APP.T VALUES CAST('original' AS APP.M)");
			var source = read(connection); var target = source.clone();
			target.getSchemas().get("APP").getTypes().get("M").setDefinition("CREATE TYPE \"APP\".\"M\" AS VARCHAR(2)");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			assertThrows(IllegalArgumentException.class, () -> migration.plan(source, target, target.getSchemas().get("APP").getTables().get("T")));
			try (var rs = sql.executeQuery("SELECT CAST(V AS VARCHAR(20)) FROM APP.T")) { assertTrue(rs.next()); assertEquals("original", rs.getString(1)); }
		}
	}

	@Test
	void lossyDistinctDefinitionChangeRequiresExplicitPolicy() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:type-replace-loss", "SA", ""); var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA"); sql.execute("CREATE TYPE APP.M AS VARCHAR(20)");
			sql.execute("CREATE TABLE APP.T(V APP.M)"); sql.execute("INSERT INTO APP.T VALUES CAST('001' AS APP.M),CAST('001' AS APP.M)");
			var source = read(connection); var target = source.clone(); target.getSchemas().get("APP").getTypes().get("M").setStatement("INTEGER");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var table = target.getSchemas().get("APP").getTables().get("T"); var plan = migration.plan(source, target, table);
			var failure = assertThrows(HsqlTableMigration.Failure.class, () -> migration.execute(connection, plan, Access.EXCLUSIVE));
			assertEquals(Phase.VALIDATE, failure.getPhase()); for (var command : plan.cleanup()) sql.execute(command.sql());
			assertEquals(Phase.COMPLETE, migration.execute(connection, migration.plan(source, target, table, HsqlTableMigration.ConversionPolicy.ALLOW_LOSSY), Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT SUM(CAST(V AS INTEGER)) FROM APP.T")) { assertTrue(rs.next()); assertEquals(2, rs.getInt(1)); }
		}
	}
}

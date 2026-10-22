/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Access;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Phase;
import com.sqlapp.data.schemas.Catalog;

class HsqlNamedColumnMigrationTest {
	private Catalog read(Connection connection) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader();
		var catalog = new Catalog("PUBLIC");
		for (String schema : java.util.List.of("APP", "Types.Schema")) {
			reader.setSchemaName(schema);
			catalog.getSchemas().addAll(reader.getAllFull(connection));
		}
		return catalog;
	}

	@Test
	void namedTypesUseIndependentBuiltinBackupsAndExplicitConversions() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:named-migration", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE SCHEMA \"Types.Schema\" AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN APP.D AS DECIMAL(10,2) CHECK(VALUE>0)");
			sql.execute("CREATE DOMAIN \"Types.Schema\".\"Domain.New\" AS DECIMAL(12,3) CHECK(VALUE>0)");
			sql.execute("CREATE TYPE APP.M AS DECIMAL(10,2)");
			sql.execute("CREATE TYPE \"Types.Schema\".\"Money.New\" AS DECIMAL(12,3)");
			sql.execute("CREATE TABLE APP.T(D APP.D, M APP.M)");
			sql.execute("INSERT INTO APP.T VALUES(1.25,CAST(2.50 AS APP.M)),(1.25,CAST(2.50 AS APP.M)),(NULL,NULL)");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("D").setDataTypeName("\"Types.Schema\".\"Domain.New\"");
			table.getColumns().get("M").setDataTypeName("\"Types.Schema\".\"Money.New\"");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.plan(source, target, table);
			assertEquals(4, plan.namedTypes().size());
			var json = new com.sqlapp.util.JsonConverter();
			assertEquals(plan,
					json.getObjectMapper().readerFor(HsqlTableMigration.Plan.class).<HsqlTableMigration.Plan>readValue(
							json.toJsonString(plan)));
			assertEquals(Phase.COMPLETE, migration.execute(connection, plan, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT COUNT(*),SUM(D),SUM(CAST(M AS DECIMAL(12,3))) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
				assertEquals("2.500", rs.getBigDecimal(2).toPlainString());
				assertEquals("5.000", rs.getBigDecimal(3).toPlainString());
			}
			sql.execute("DROP TYPE APP.M");
			sql.execute("DROP DOMAIN APP.D");
			try (var rs = sql.executeQuery("SELECT COUNT(*) FROM " + plan.backupTable())) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
			var actual = read(connection).getSchemas().get("APP").getTables().get("T");
			assertEquals(table.getColumns().get("M").getDataTypeName(), actual.getColumns().get("M").getDataTypeName());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void builtinAndNamedColumnConversionsWorkInBothDirections(boolean toNamed) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:named-direction-" + toNamed, "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN APP.D AS DECIMAL(12,3)");
			sql.execute("CREATE TYPE APP.M AS DECIMAL(12,3)");
			sql.execute(toNamed ? "CREATE TABLE APP.T(D DECIMAL(12,3), M DECIMAL(12,3))"
					: "CREATE TABLE APP.T(D APP.D, M APP.M)");
			sql.execute(toNamed ? "INSERT INTO APP.T VALUES(1.125,2.250)"
					: "INSERT INTO APP.T VALUES(1.125,CAST(2.250 AS APP.M))");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			for (var column : table.getColumns())
				column.setDataTypeName(toNamed ? "\"APP\".\"" + column.getName() + "\"" : "DECIMAL(12,3)");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			assertEquals(Phase.COMPLETE,
					migration.execute(connection, migration.plan(source, target, table), Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT D,CAST(M AS DECIMAL(12,3)) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals("1.125", rs.getBigDecimal(1).toPlainString());
				assertEquals("2.250", rs.getBigDecimal(2).toPlainString());
			}
		}
	}

	@Test
	void rejectedDomainValuesRecoverOriginalNamedColumns() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:named-recovery", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN APP.OLD_D AS INTEGER");
			sql.execute("CREATE DOMAIN APP.NEW_D AS INTEGER CHECK(VALUE>0)");
			sql.execute("CREATE TABLE APP.T(V APP.OLD_D)");
			sql.execute("INSERT INTO APP.T VALUES -1,3");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("V").setDataTypeName("\"APP\".\"NEW_D\"");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var failure = assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, migration.plan(source, target, table), Access.EXCLUSIVE));
			assertTrue(failure.getPhase() == Phase.VALIDATE || failure.getPhase() == Phase.RESTORE);
			if (failure.getPhase() == Phase.RESTORE)
				assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT COUNT(*),SUM(V) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt(1));
				assertEquals(2, rs.getInt(2));
			}
		}
	}

	@Test
	void changedExternalDomainDefinitionsAreRejectedBeforeBackup() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:named-stale", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE SCHEMA \"Types.Schema\" AUTHORIZATION DBA");
			sql.execute("CREATE DOMAIN \"Types.Schema\".D AS INTEGER DEFAULT 1");
			sql.execute("CREATE TABLE APP.T(V \"Types.Schema\".D, G INTEGER GENERATED ALWAYS AS(V*2))");
			sql.execute("INSERT INTO APP.T(V) VALUES 3");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("G").setFormula("V*3");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.plan(source, target, table);
			sql.execute("ALTER DOMAIN \"Types.Schema\".D SET DEFAULT 8");
			var failure = assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE));
			assertEquals(Phase.PREFLIGHT, failure.getPhase());
			assertThrows(java.sql.SQLException.class, () -> sql.executeQuery(plan.backupCount()));
			try (var rs = sql.executeQuery("SELECT V,G FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
				assertEquals(6, rs.getInt(2));
			}
		}
	}

	@Test
	void incompleteOrNonScalarNamedTypeModelsAreRejectedDuringPlanning() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:named-invalid", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TYPE APP.M AS INTEGER");
			sql.execute("CREATE TABLE APP.T(V APP.M)");
			var source = read(connection);
			var target = source.clone();
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var table = target.getSchemas().get("APP").getTables().get("T");
			target.getSchemas().get("APP").getTypes().clear();
			assertThrows(IllegalArgumentException.class, () -> migration.plan(source, target, table));
			var invalidTarget = source.clone();
			invalidTarget.getSchemas().get("APP").getTypes().get("M").setStatement("BLOB");
			assertThrows(IllegalArgumentException.class, () -> migration.plan(source, invalidTarget,
					invalidTarget.getSchemas().get("APP").getTables().get("T")));
		}
	}
}

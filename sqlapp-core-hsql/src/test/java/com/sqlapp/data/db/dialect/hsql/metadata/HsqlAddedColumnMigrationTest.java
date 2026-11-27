/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Access;
import com.sqlapp.data.db.dialect.hsql.HsqlTableMigration.Phase;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;

class HsqlAddedColumnMigrationTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void defaultsNullsArraysAndGeneratedValuesArePreserved(boolean batch) throws Exception {
		try (var c = open("success-" + batch); var s = c.createStatement()) {
			setup(c);
			if (batch)
				s.execute("CREATE VIEW APP.V AS SELECT N FROM APP.T");
			var source = read(c);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("N").setDataType(DataType.BIGINT);
			table.getColumns()
					.add(new Column("Added.Default").setDataType(DataType.INT).setNullable(false).setDefaultValue("7"));
			table.getColumns().add(new Column("OPTIONAL").setDataType(DataType.VARCHAR).setLength(20L));
			table.getColumns().add(new Column("G").setDataType(DataType.BIGINT).setFormula("COALESCE(N,0)*2"));
			table.getColumns().add(new Column("S").setDataType(DataType.TIMESTAMP).setNullable(false)
					.setDefaultValue("CURRENT_TIMESTAMP"));
			table.getColumns()
					.add(new Column("A").setDataType(DataType.INT).setArrayDimension(1).setArrayDimensionUpperBound(4));
			String sourceXml = source.asXml(), targetXml = target.asXml();
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
			var json = new com.sqlapp.util.JsonConverter();
			if (batch) {
				var plan = migration.planAll(source, target);
				var restored = json.getObjectMapper()
						.readerFor(HsqlTableMigration.BatchPlan.class).<HsqlTableMigration.BatchPlan>readValue(
								json.toJsonString(plan));
				assertEquals(plan, restored);
				assertEquals(Phase.COMPLETE, migration.execute(c, restored, Access.EXCLUSIVE).phase());
				try (var rs = s.executeQuery("SELECT COUNT(*) FROM APP.V")) {
					assertTrue(rs.next());
					assertEquals(3, rs.getInt(1));
				}
			} else {
				var plan = migration.plan(source, target, table);
				var restored = json.getObjectMapper()
						.readerFor(HsqlTableMigration.Plan.class).<HsqlTableMigration.Plan>readValue(
								json.toJsonString(plan));
				assertEquals(plan, restored);
				assertEquals(Phase.COMPLETE, migration.execute(c, restored, Access.EXCLUSIVE).phase());
			}
			assertEquals(sourceXml, source.asXml());
			assertEquals(targetXml, target.asXml());
			try (var rs = s.executeQuery(
					"SELECT COUNT(*),SUM(N),SUM(\"Added.Default\"),COUNT(OPTIONAL),SUM(G),COUNT(S) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
				assertEquals(2, rs.getInt(2));
				assertEquals(21, rs.getInt(3));
				assertEquals(0, rs.getInt(4));
				assertEquals(4, rs.getInt(5));
				assertEquals(3, rs.getInt(6));
			}
			try (var rs = s.executeQuery("SELECT A FROM APP.T")) {
				int count = 0;
				while (rs.next()) {
					count++;
					assertNull(rs.getArray(1));
				}
				assertEquals(3, count);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void failingAddedColumnConstraintRecoversOriginalShapeAndDuplicateRows(boolean batch) throws Exception {
		try (var c = open("recover-" + batch); var s = c.createStatement()) {
			setup(c);
			var source = read(c);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().add(new Column("ADDED").setDataType(DataType.INT).setDefaultValue("-1"));
			table.getConstraints().add(new CheckConstraint("POSITIVE", "ADDED>0"));
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
			if (batch) {
				var plan = migration.planAll(source, target);
				var failure = assertThrows(HsqlTableMigration.BatchFailure.class,
						() -> migration.execute(c, plan, Access.EXCLUSIVE));
				assertEquals(Phase.RESTORE, failure.getPhase());
				assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
			} else {
				var plan = migration.plan(source, target, table);
				var failure = assertThrows(HsqlTableMigration.Failure.class,
						() -> migration.execute(c, plan, Access.EXCLUSIVE));
				assertEquals(Phase.RESTORE, failure.getPhase());
				assertEquals(Phase.RECOVERED, migration.recover(c, failure, Access.EXCLUSIVE).phase());
			}
			assertEquals(1, read(c).getSchemas().get("APP").getTables().get("T").getColumns().size());
			try (var rs = s.executeQuery("SELECT COUNT(*),COUNT(N),SUM(N) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
				assertEquals(2, rs.getInt(2));
				assertEquals(2, rs.getInt(3));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void undefinedRequiredValuesAndDestructiveIdentityChangesFailPlanning(boolean batch) throws Exception {
		try (var c = open("invalid-" + batch); var s = c.createStatement()) {
			setup(c);
			var source = read(c);
			for (String invalid : java.util.List.of("required", "identity", "remove", "rename")) {
				var target = source.clone();
				var table = target.getSchemas().get("APP").getTables().get("T");
				switch (invalid) {
				case "required" ->
					table.getColumns().add(new Column("ADDED").setDataType(DataType.INT).setNullable(false));
				case "identity" ->
					table.getColumns().add(new Column("ADDED").setDataType(DataType.INT).setIdentity(true));
				case "remove" -> table.getColumns().remove("N");
				case "rename" -> {
					table.getColumns().remove("N");
					table.getColumns().add(new Column("RENAMED").setDataType(DataType.INT));
				}
				default -> throw new AssertionError(invalid);
				}
				var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(c));
				if (batch)
					assertThrows(IllegalArgumentException.class, () -> migration.planAll(source, target));
				else
					assertThrows(IllegalArgumentException.class, () -> migration.plan(source, target, table));
			}
			try (var rs = s.executeQuery("SELECT COUNT(*) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
			try (var rs = s.executeQuery(
					"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='APP' AND TABLE_NAME LIKE 'SQLAPP_BACKUP_%'")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	private Connection open(String name) throws Exception {
		return DriverManager.getConnection("jdbc:hsqldb:mem:added-column-" + name, "SA", "");
	}

	private void setup(Connection c) throws Exception {
		try (var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE TABLE APP.T(N INTEGER)");
			s.execute("INSERT INTO APP.T VALUES(1),(1),(NULL)");
		}
	}

	private Catalog read(Connection c) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader();
		reader.getSchemaReader().setSchemaName("APP");
		var catalog = new Catalog("PUBLIC");
		catalog.getSchemas().addAll(reader.getSchemaReader().getAllFull(c));
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(c));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(c));
		catalog.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(c));
		return catalog;
	}
}

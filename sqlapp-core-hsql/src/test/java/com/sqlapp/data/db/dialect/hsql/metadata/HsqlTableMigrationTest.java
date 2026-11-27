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

class HsqlTableMigrationTest {
	private Catalog read(Connection connection) throws Exception {
		var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader();
		var schemas = reader.getSchemaReader();
		schemas.setSchemaName("APP");
		var catalog = new Catalog("PUBLIC");
		catalog.getSchemas().addAll(schemas.getAllFull(connection));
		catalog.getObjectPrivileges().addAll(reader.getObjectPrivilegeReader().getAll(connection));
		catalog.getColumnPrivileges().addAll(reader.getColumnPrivilegeReader().getAll(connection));
		return catalog;
	}

	@Test
	void generatedExpressionAndTypeChangesRetainDataIdentityIndexesTriggersAndForeignKeys() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:table-migration", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute(
					"CREATE TABLE APP.T(ID INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, V INTEGER, G INTEGER GENERATED ALWAYS AS(V*2))");
			sql.execute("CREATE INDEX APP.IDX_V ON APP.T(V)");
			sql.execute("CREATE ROLE APP_READER");
			sql.execute("CREATE USER MIGRATION_READER PASSWORD ''");
			sql.execute("GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON APP.T TO APP_READER");
			sql.execute("GRANT SELECT(V) ON APP.T TO MIGRATION_READER");
			sql.execute("CREATE TABLE APP.CHILD(ID INTEGER, CONSTRAINT CHILD_T FOREIGN KEY(ID) REFERENCES APP.T(ID))");
			sql.execute("CREATE TABLE APP.AUDIT(V INTEGER)");
			sql.execute(
					"CREATE TRIGGER APP.TR AFTER INSERT ON APP.T REFERENCING NEW ROW AS N FOR EACH ROW INSERT INTO APP.AUDIT VALUES(N.V)");
			sql.execute("INSERT INTO APP.T(V) VALUES(3),(3),(7)");
			sql.execute("INSERT INTO APP.CHILD VALUES 0");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("G").setFormula("V*3");
			table.getColumns().get("V").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			String original = source.asXml();
			var plan = migration.plan(source, target, table);
			assertEquals(original, source.asXml());
			assertEquals(plan.fingerprint(), migration.plan(source, target, table).fingerprint());
			String serialized = new com.sqlapp.util.JsonConverter().toJsonString(plan);
			var restoredPlan = new com.sqlapp.util.JsonConverter().getObjectMapper()
					.readerFor(HsqlTableMigration.Plan.class).<HsqlTableMigration.Plan>readValue(serialized);
			assertEquals(plan, restoredPlan);
			var mapper = new com.sqlapp.util.JsonConverter().getObjectMapper();
			var legacy = (tools.jackson.databind.node.ObjectNode) mapper.readTree(serialized);
			legacy.remove("namedTypes");
			legacy.remove("typeReplacements");
			assertEquals(plan, mapper.treeToValue(legacy, HsqlTableMigration.Plan.class));
			assertEquals(plan, new HsqlTableMigration.Plan(plan.schema(), plan.table(), plan.backupTable(),
					plan.sourceFingerprint(), plan.targetFingerprint(), plan.fingerprint(), plan.conversionPolicy(),
					plan.sourceSchemaXml(), plan.sourcePrivileges(), plan.inboundFingerprint(), plan.backup(),
					plan.validationQueries(), plan.replace(), plan.restore(), plan.recovery(), plan.cleanup(),
					plan.backupVerification(), plan.targetVerification(), plan.sourceCount(), plan.backupCount(),
					plan.targetCount(), plan.backupRows()));
			assertThrows(RuntimeException.class,
					() -> new com.sqlapp.util.JsonConverter().getObjectMapper().readerFor(HsqlTableMigration.Plan.class)
							.readValue(serialized.replace(plan.fingerprint(), "0".repeat(64))));
			assertFalse(plan.replace().toString().contains("CASCADE"));
			var result = migration.execute(connection, plan, Access.EXCLUSIVE);
			assertEquals(Phase.COMPLETE, result.phase());
			assertEquals(3, result.rows());
			try (var rs = sql.executeQuery("SELECT SUM(V), SUM(G), COUNT(*) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(13, rs.getLong(1));
				assertEquals(39, rs.getLong(2));
				assertEquals(3, rs.getLong(3));
			}
			try (var rs = sql.executeQuery("SELECT COUNT(*) FROM APP.AUDIT")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
			sql.execute("INSERT INTO APP.T(V) VALUES 9");
			try (var rs = sql.executeQuery("SELECT MAX(ID) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
			assertThrows(java.sql.SQLException.class, () -> sql.execute("DELETE FROM APP.T WHERE ID=0"));
			try (var rs = sql.executeQuery("SELECT SUM(G) FROM " + result.backupTable())) {
				assertTrue(rs.next());
				assertEquals(26, rs.getInt(1));
			}
			assertNotNull(read(connection).getSchemas().get("APP").getTables().get("T").getIndexes().get("IDX_V"));
			try (var restricted = DriverManager.getConnection("jdbc:hsqldb:mem:table-migration", "MIGRATION_READER",
					""); var query = restricted.createStatement()) {
				try (var rs = query.executeQuery("SELECT SUM(V) FROM APP.T")) {
					assertTrue(rs.next());
					assertEquals(22, rs.getInt(1));
				}
				assertThrows(java.sql.SQLException.class, () -> query.executeQuery("SELECT ID FROM APP.T"));
			}
		}
	}

	@Test
	void allPrivilegesWithUnrestorableTriggerRightAreRejectedBeforeReplacement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:migration-all-rights", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TABLE APP.T(V INTEGER)");
			sql.execute("INSERT INTO APP.T VALUES 7");
			sql.execute("CREATE ROLE READER");
			sql.execute("GRANT ALL PRIVILEGES ON APP.T TO READER");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("V").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			assertThrows(IllegalArgumentException.class, () -> migration.plan(source, target, table));
			try (var rs = sql.executeQuery("SELECT V FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(7, rs.getInt(1));
			}
		}
	}

	@Test
	void lossyConversionFailsBeforeReplacementAndPreservesBackup() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:migration-loss", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TABLE APP.T(V VARCHAR(20))");
			sql.execute("INSERT INTO APP.T VALUES ('001'),('001')");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("V").setDataType(com.sqlapp.data.db.datatype.DataType.INT).setLength(null);
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.plan(source, target, table);
			var failure = assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE));
			assertEquals(Phase.VALIDATE, failure.getPhase());
			try (var rs = sql.executeQuery("SELECT V FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals("001", rs.getString(1));
			}
			try (var rs = sql.executeQuery(plan.backupCount())) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt(1));
			}
			for (var command : plan.cleanup())
				sql.execute(command.sql());
			var approved = migration.plan(source, target, table, HsqlTableMigration.ConversionPolicy.ALLOW_LOSSY);
			assertEquals(Phase.COMPLETE, migration.execute(connection, approved, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT COUNT(*),SUM(V) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt(1));
				assertEquals(2, rs.getInt(2));
			}
		}
	}

	@Test
	void failedReplacementExposesExecutableRecoveryWithoutDeletingBackup() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:migration-recovery", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TABLE APP.T(ID INTEGER PRIMARY KEY, V INTEGER)");
			sql.execute("CREATE TABLE APP.CHILD(ID INTEGER, CONSTRAINT CHILD_T FOREIGN KEY(ID) REFERENCES APP.T(ID))");
			sql.execute("INSERT INTO APP.T VALUES (1,-1),(2,-1),(3,3)");
			sql.execute("INSERT INTO APP.CHILD VALUES 1");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getConstraints().addCheckConstraint("POSITIVE", "V>0");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.plan(source, target, table);
			var failure = assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE));
			assertEquals(Phase.RESTORE, failure.getPhase());
			sql.execute("UPDATE " + plan.backupTable() + " SET V=V+100");
			assertEquals(Phase.RECOVER, assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.recover(connection, failure, Access.EXCLUSIVE)).getPhase());
			try (var rs = sql.executeQuery("SELECT COUNT(*) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
			sql.execute("UPDATE " + plan.backupTable() + " SET V=V-100");
			assertEquals(Phase.RECOVERED, migration.recover(connection, failure, Access.EXCLUSIVE).phase());
			try (var rs = sql.executeQuery("SELECT COUNT(*),SUM(V) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
				assertEquals(1, rs.getInt(2));
			}
			try (var rs = sql.executeQuery(plan.backupCount())) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
		}
	}

	@Test
	void staleSchemaAndCallerTransactionsAreRejectedBeforeBackup() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:migration-stale", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE TABLE APP.T(V INTEGER)");
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			var migration = new HsqlTableMigration(DialectResolver.getInstance().getDialect(connection));
			var plan = migration.plan(source, target, table);
			connection.setAutoCommit(false);
			assertEquals(Phase.PREFLIGHT, assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE)).getPhase());
			connection.rollback();
			connection.setAutoCommit(true);
			sql.execute("CREATE ROLE NEW_READER");
			sql.execute("GRANT SELECT(V) ON APP.T TO NEW_READER");
			assertEquals(Phase.PREFLIGHT, assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE)).getPhase());
			sql.execute("ALTER TABLE APP.T ADD COLUMN EXTRA INTEGER");
			assertEquals(Phase.PREFLIGHT, assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, plan, Access.EXCLUSIVE)).getPhase());
			assertThrows(java.sql.SQLException.class, () -> sql.executeQuery(plan.backupCount()));
		}
	}

	@Test
	void incompleteCrossSchemaReferencesAreRejectedAndCompleteCatalogRestoresThem() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:migration-references", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE SCHEMA OTHER AUTHORIZATION DBA");
			sql.execute("CREATE TABLE APP.T(ID INTEGER PRIMARY KEY,V INTEGER)");
			sql.execute("INSERT INTO APP.T VALUES(1,10)");
			sql.execute("CREATE TABLE OTHER.CHILD(ID INTEGER, CONSTRAINT FK_T FOREIGN KEY(ID) REFERENCES APP.T(ID))");
			sql.execute("INSERT INTO OTHER.CHILD VALUES 1");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var migration = new HsqlTableMigration(dialect);
			var source = read(connection);
			var target = source.clone();
			var table = target.getSchemas().get("APP").getTables().get("T");
			table.getColumns().get("V").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			var incomplete = migration.plan(source, target, table);
			assertEquals(Phase.PREFLIGHT, assertThrows(HsqlTableMigration.Failure.class,
					() -> migration.execute(connection, incomplete, Access.EXCLUSIVE)).getPhase());
			assertThrows(java.sql.SQLException.class, () -> sql.executeQuery(incomplete.backupCount()));
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("OTHER");
			source.getSchemas().addAll(reader.getAllFull(connection));
			var desired = source.clone();
			var completeTable = desired.getSchemas().get("APP").getTables().get("T");
			completeTable.getColumns().get("V").setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			var complete = migration.plan(source, desired, completeTable);
			assertNotEquals(incomplete.fingerprint(), complete.fingerprint());
			assertEquals(Phase.COMPLETE, migration.execute(connection, complete, Access.EXCLUSIVE).phase());
			assertThrows(java.sql.SQLException.class, () -> sql.execute("DELETE FROM APP.T WHERE ID=1"));
		}
	}
}

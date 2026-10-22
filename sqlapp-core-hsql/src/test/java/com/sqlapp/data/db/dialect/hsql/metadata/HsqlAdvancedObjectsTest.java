/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DomainCheckConstraint;

class HsqlAdvancedObjectsTest {
	@Test
	void unsupportedTypeAndSynonymAlterationsFailExplicitly() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:explicit-alter", "SA", "")) {
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			var type = new com.sqlapp.data.schemas.Type("D");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(type.diff(type.clone().setStatement("BIGINT")), SqlType.ALTER));
			var synonym = new com.sqlapp.data.schemas.Synonym("S");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(synonym.diff(synonym.clone().setName("R")), SqlType.ALTER));
		}
	}

	@Test
	void schemaAlterUsesTheDomainAlterFactory() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-schema-alter", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			statement.execute("CREATE DOMAIN APP.D AS INTEGER DEFAULT 5 CONSTRAINT LOW CHECK (VALUE > 0)");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("APP");
			var original = reader.getAllFull(connection).get(0);
			var target = original.clone();
			target.getDomains().get("D").setDefaultValue("8");
			var factory = dialect.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
			var operations = factory.createDiffSql(original.diff(target));
			assertFalse(operations.isEmpty());
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			assertEquals("8", reader.getAllFull(connection).get(0).getDomains().get("D").getDefaultValue());
		}
	}

	@Test
	void catalogRecreationOrdersDependenciesAcrossSchemas() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-catalog", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA Z AUTHORIZATION DBA");
			statement.execute("CREATE SCHEMA A AUTHORIZATION DBA");
			statement.execute("CREATE TABLE Z.T (V INTEGER)");
			statement
					.execute("CREATE FUNCTION Z.BASE() RETURNS INTEGER READS SQL DATA RETURN (SELECT SUM(V) FROM Z.T)");
			statement.execute("CREATE FUNCTION A.CALL_BASE() RETURNS INTEGER READS SQL DATA RETURN Z.BASE()");
			statement.execute("CREATE VIEW A.TOTAL AS SELECT A.CALL_BASE() AS V FROM (VALUES(0))");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader();
			var catalog = new com.sqlapp.data.schemas.Catalog("PUBLIC");
			reader.setSchemaName("A");
			catalog.getSchemas().add(reader.getAllFull(connection).get(0));
			reader.setSchemaName("Z");
			catalog.getSchemas().add(reader.getAllFull(connection).get(0));
			var operations = dialect.createSqlFactoryRegistry().createSql(catalog, SqlType.CREATE);
			try (var target = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-catalog-target", "SA", "");
					var targetStatement = target.createStatement()) {
				for (var operation : operations)
					targetStatement.execute(operation.getSqlText());
				targetStatement.execute("INSERT INTO Z.T VALUES (11)");
				try (var rs = targetStatement.executeQuery("SELECT V FROM A.TOTAL")) {
					assertTrue(rs.next());
					assertEquals(11, rs.getInt(1));
				}
			}
		}
	}

	@Test
	void updateOfTriggerPreservesColumnFilteringAndQuotedNames() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-update-trigger", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T (ID INTEGER, \"Value Name\" INTEGER, OTHER INTEGER)");
			statement.execute("CREATE TABLE AUDIT (V INTEGER)");
			statement.execute(
					"CREATE TRIGGER AUDIT_T AFTER UPDATE OF \"Value Name\" ON T REFERENCING NEW ROW AS N FOR EACH ROW INSERT INTO AUDIT VALUES (N.\"Value Name\")");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTriggerReader();
			reader.setSchemaName("PUBLIC");
			var trigger = reader.getAll(connection).get(0);
			var operations = dialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE);
			statement.execute("DROP TRIGGER AUDIT_T");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO T VALUES (1, 3, 4)");
			statement.execute("UPDATE T SET OTHER = 5");
			statement.execute("UPDATE T SET \"Value Name\" = 7");
			try (var rs = statement.executeQuery("SELECT COUNT(*), SUM(V) FROM AUDIT")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt(1));
				assertEquals(7, rs.getInt(2));
			}
		}
	}

	@Test
	void schemaRecreationOrdersRoutinesAndViewsAfterTheirTables() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-schema", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			statement.execute("CREATE TABLE APP.T (V INTEGER PRIMARY KEY)");
			statement.execute(
					"CREATE TABLE APP.CHILD (V INTEGER, CONSTRAINT FK_PARENT FOREIGN KEY(V) REFERENCES APP.T(V))");
			statement.execute("CREATE INDEX APP.IDX_CHILD ON APP.CHILD(V)");
			statement.execute(
					"CREATE FUNCTION APP.SUM_T() RETURNS INTEGER READS SQL DATA RETURN (SELECT SUM(V) FROM APP.T)");
			statement
					.execute("CREATE PROCEDURE APP.ADD_T(IN V INTEGER) MODIFIES SQL DATA INSERT INTO APP.T VALUES (V)");
			statement.execute("CREATE VIEW APP.SUM_VIEW AS SELECT APP.SUM_T() AS TOTAL FROM (VALUES(0))");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("APP");
			var schema = reader.getAllFull(connection).get(0);
			assertFalse(com.sqlapp.data.db.dialect.hsql.HsqlDdlDependencies.get(schema).isEmpty());
			com.sqlapp.data.schemas.Schema restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(schema.asXml()));
			var operations = dialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE);
			statement.execute("DROP SCHEMA APP CASCADE");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			statement.execute("CALL APP.ADD_T(7)");
			statement.execute("INSERT INTO APP.CHILD VALUES (7)");
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO APP.CHILD VALUES (8)"));
			try (var rs = statement.executeQuery("SELECT TOTAL FROM APP.SUM_VIEW")) {
				assertTrue(rs.next());
				assertEquals(7, rs.getInt(1));
			}
		}
	}

	@Test
	void synonymsRetainQuotedTargets() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-synonym", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE \"Target.Table\" (V INTEGER)");
			statement.execute("INSERT INTO \"Target.Table\" VALUES (9)");
			statement.execute("CREATE SYNONYM \"Alias Name\" FOR PUBLIC.\"Target.Table\"");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getSynonymReader();
			reader.setSchemaName("PUBLIC");
			var synonyms = reader.getAll(connection);
			assertEquals(1, synonyms.size());
			var operations = dialect.createSqlFactoryRegistry().createSql(synonyms.get(0), SqlType.CREATE);
			statement.execute("DROP SYNONYM \"Alias Name\"");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery("SELECT V FROM \"Alias Name\"")) {
				assertTrue(rs.next());
				assertEquals(9, rs.getInt(1));
			}
		}
	}

	@Test
	void distinctTypesAndTypedColumnsSurviveRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-type", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			statement.execute("CREATE TYPE APP.\"Money Type\" AS DECIMAL(12, 3)");
			statement.execute("CREATE TABLE APP.T (AMOUNT APP.\"Money Type\")");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("APP");
			var types = reader.getTypeReader().getAllFull(connection);
			assertEquals(1, types.size());
			var type = types.get(0);
			assertTrue(String.join("\n", type.getStatement()).contains("DECIMAL(12,3)"));
			var table = reader.getTableReader().getAllFull(connection).get(0);
			var registry = dialect.createSqlFactoryRegistry();
			var typeSql = registry.createSql(type, SqlType.CREATE);
			var tableSql = registry.createSql(table, SqlType.CREATE);
			statement.execute("DROP TABLE APP.T");
			statement.execute("DROP TYPE APP.\"Money Type\"");
			for (var operation : typeSql)
				statement.execute(operation.getSqlText());
			for (var operation : tableSql)
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery(
					"SELECT UDT_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='APP' AND TABLE_NAME='T'")) {
				assertTrue(rs.next());
				assertEquals("Money Type", rs.getString(1));
			}
		}
	}

	@Test
	void domainAlterRetainsDependentColumnsAndWorksInBothDirections() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-domain", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE DOMAIN D AS INTEGER DEFAULT 5 CONSTRAINT LOW CHECK (VALUE > 0) CONSTRAINT HIGH CHECK (VALUE < 100)");
			statement.execute("CREATE TABLE T (V D)");
			statement.execute("INSERT INTO T VALUES (5)");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getDomainReader();
			reader.setSchemaName("PUBLIC");
			var original = reader.getAll(connection).get(0);
			var target = original.clone();
			target.setDefaultValue("6");
			target.getChecks().get("LOW").setExpression("VALUE > 1");
			target.getChecks().remove("HIGH");
			target.getChecks().add(new DomainCheckConstraint("LIMIT", "VALUE < 50"));
			var factory = dialect.createSqlFactoryRegistry().getSqlFactory(original, SqlType.ALTER);
			for (var operation : factory.createDiffSql(original.diff(target)))
				statement.execute(operation.getSqlText());
			var actual = reader.getAll(connection).get(0);
			assertEquals("6", actual.getDefaultValue());
			assertNotNull(actual.getChecks().get("LIMIT"));
			assertNull(actual.getChecks().get("HIGH"));
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO T VALUES (1)"));
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO T VALUES (50)"));
			for (var operation : factory.createDiffSql(target.diff(original)))
				statement.execute(operation.getSqlText());
			assertEquals("5", reader.getAll(connection).get(0).getDefaultValue());
			statement.execute("INSERT INTO T VALUES (1), (50)");
			var withoutDefault = original.clone().setDefaultValue(null);
			for (var operation : factory.createDiffSql(original.diff(withoutDefault)))
				statement.execute(operation.getSqlText());
			assertNull(reader.getAll(connection).get(0).getDefaultValue());
			var changedType = original.clone().setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT);
			assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(original.diff(changedType)));
		}
	}

	@Test
	void statementTriggerTransitionTableSurvivesRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:advanced-trigger", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T (V INTEGER)");
			statement.execute("CREATE TABLE AUDIT (V INTEGER)");
			statement.execute(
					"CREATE TRIGGER AUDIT_T AFTER INSERT ON T REFERENCING NEW TABLE AS N FOR EACH STATEMENT INSERT INTO AUDIT VALUES (3)");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTriggerReader();
			reader.setSchemaName("PUBLIC");
			var trigger = reader.getAll(connection).get(0);
			assertEquals("N", trigger.getSpecifics().get("ACTION_REFERENCE_NEW_TABLE"));
			var operations = dialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE);
			statement.execute("DROP TRIGGER AUDIT_T");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO T VALUES (1), (2)");
			try (var rs = statement.executeQuery("SELECT SUM(V) FROM AUDIT")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
		}
	}
}

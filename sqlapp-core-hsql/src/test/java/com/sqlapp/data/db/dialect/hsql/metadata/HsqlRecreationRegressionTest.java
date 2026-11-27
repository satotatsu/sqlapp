/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class HsqlRecreationRegressionTest {
	@Test
	void defaultRemovalAndGeneratedExpressionChangesAreExplicit() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:alter-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SAMPLE (ID INTEGER DEFAULT 7, TOTAL INTEGER GENERATED ALWAYS AS (ID * 2))");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("PUBLIC");
			var table = reader.getAllFull(connection).get(0);
			var changed = table.clone();
			changed.getColumns().get("ID").setDefaultValue(null);
			var factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.ALTER);
			for (var operation : factory.createDiffSql(table.diff(changed)))
				statement.execute(operation.getSqlText());
			assertNull(reader.getAllFull(connection).get(0).getColumns().get("ID").getDefaultValue());
			changed.getColumns().get("TOTAL").setFormula("ID * 3");
			assertThrows(IllegalArgumentException.class, () -> factory.createDiffSql(table.diff(changed)));
		}
	}

	@Test
	void procedureBodyPreservesSemicolonsInsideStrings() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:procedure-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SAMPLE (NAME VARCHAR(50))");
			statement.execute(
					"CREATE PROCEDURE ADD_LITERAL() MODIFIES SQL DATA BEGIN ATOMIC INSERT INTO SAMPLE VALUES ('PUBLIC.;value'); END");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getProcedureReader();
			reader.setSchemaName("PUBLIC");
			reader.setObjectName("ADD_LITERAL");
			var procedure = reader.getAllFull(connection).get(0);
			var operations = dialect.createSqlFactoryRegistry().createSql(procedure, SqlType.CREATE);
			statement.execute("DROP PROCEDURE ADD_LITERAL");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			statement.execute("CALL ADD_LITERAL()");
			try (var rs = statement.executeQuery("SELECT NAME FROM SAMPLE")) {
				assertTrue(rs.next());
				assertEquals("PUBLIC.;value", rs.getString(1));
			}
		}
	}

	@Test
	void namedDomainChecksAreGroupedAndRecreated() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:domain-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE DOMAIN POSITIVE AS INTEGER DEFAULT 7 CONSTRAINT LOWER_BOUND CHECK (VALUE > 0) CONSTRAINT UPPER_BOUND CHECK (VALUE < 10)");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getDomainReader();
			reader.setSchemaName("PUBLIC");
			var domains = reader.getAll(connection);
			assertEquals(1, domains.size());
			var domain = domains.get(0);
			assertEquals(2, domain.getChecks().size());
			var schema = new com.sqlapp.data.schemas.Schema("PUBLIC");
			schema.getDomains().add(domain);
			com.sqlapp.data.schemas.Schema restored = com.sqlapp.data.schemas.SchemaUtils
					.readXml(new java.io.StringReader(schema.asXml()));
			assertEquals(2, restored.getDomains().get("POSITIVE").getChecks().size());
			var operations = dialect.createSqlFactoryRegistry().createSql(domain, SqlType.CREATE);
			statement.execute("DROP DOMAIN POSITIVE");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			assertEquals(2, reader.getAll(connection).get(0).getChecks().size());
			statement.execute("CREATE TABLE SAMPLE (VALUE POSITIVE)");
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO SAMPLE VALUES (0)"));
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO SAMPLE VALUES (10)"));
		}
	}

	@Test
	void functionBodiesPreserveLiteralsAndNullCallSemantics() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:routine-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE FUNCTION LITERAL_FN(V VARCHAR(40)) RETURNS VARCHAR(80) CALLED ON NULL INPUT RETURN COALESCE(V, 'PUBLIC.;value')");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getFunctionReader();
			reader.setSchemaName("PUBLIC");
			reader.setObjectName("LITERAL_FN");
			var function = reader.getAllFull(connection).get(0);
			assertNotNull(function.getStatement());
			var operations = dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE);
			statement.execute("DROP FUNCTION LITERAL_FN");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery("VALUES LITERAL_FN(NULL)")) {
				assertTrue(rs.next());
				assertEquals("PUBLIC.;value", rs.getString(1));
			}
		}
	}

	@Test
	void quotedTableCheckWithLiteralPreservesItsMeaning() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:check-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE \"Mixed.Table\" (\"Name\" VARCHAR(50) NOT NULL, CONSTRAINT VALID_NAME CHECK (\"Name\" <> 'PUBLIC.Mixed.Table.value'))");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("PUBLIC");
			var table = reader.getAllFull(connection).get(0);
			assertNotNull(table.getConstraints().get("VALID_NAME"));
			var operations = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
			statement.execute("DROP TABLE \"Mixed.Table\"");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			assertThrows(java.sql.SQLException.class,
					() -> statement.execute("INSERT INTO \"Mixed.Table\" VALUES ('PUBLIC.Mixed.Table.value')"));
		}
	}

	@Test
	void identityGenerationAndBoundsSurviveRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:identity-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE SAMPLE (ID INTEGER GENERATED ALWAYS AS IDENTITY (START WITH 5 INCREMENT BY 2 MINVALUE 1 MAXVALUE 99 CYCLE))");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("PUBLIC");
			reader.setObjectName("SAMPLE");
			var table = reader.getAllFull(connection).get(0);
			var operations = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
			statement.execute("DROP TABLE SAMPLE");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			var column = reader.getAllFull(connection).get(0).getColumns().get("ID");
			assertEquals(com.sqlapp.data.schemas.IdentityGenerationType.Always, column.getIdentityGenerationType());
			assertEquals(2L, column.getIdentityStep());
			assertEquals(99L, column.getIdentityMaxValue());
			assertTrue(column.isIdentityCycle());
		}
	}

	@Test
	void generatedColumnsAndDomainNullabilitySurviveRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:generated-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE DOMAIN LABEL AS VARCHAR(40)");
			statement.execute(
					"CREATE TABLE SAMPLE (ID INTEGER, NAME LABEL NOT NULL, TOTAL INTEGER GENERATED ALWAYS AS (ID * 2))");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("PUBLIC");
			reader.setObjectName("SAMPLE");
			var table = reader.getAllFull(connection).get(0);
			assertTrue(table.getColumns().get("NAME").isNotNull());
			assertNotNull(table.getColumns().get("TOTAL").getFormula());
			var operations = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
			statement.execute("DROP TABLE SAMPLE");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO SAMPLE(ID, NAME) VALUES (3, 'test')");
			try (var rs = statement.executeQuery("SELECT TOTAL FROM SAMPLE")) {
				assertTrue(rs.next());
				assertEquals(6, rs.getInt(1));
			}
		}
	}

	@Test
	void triggerReferencesAndConditionSurviveRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:trigger-regression", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE SAMPLE (ID INTEGER, NAME VARCHAR(40))");
			statement.execute(
					"CREATE TRIGGER SAMPLE_TRIGGER BEFORE INSERT ON SAMPLE REFERENCING NEW ROW AS N FOR EACH ROW WHEN (N.ID > 0) SET N.NAME = 'PUBLIC.;value'");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getTriggerReader();
			reader.setSchemaName("PUBLIC");
			var trigger = reader.getAll(connection).get(0);
			assertEquals("N", trigger.getActionReferenceNewRow());
			var operations = dialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE);
			statement.execute("DROP TRIGGER SAMPLE_TRIGGER");
			for (var operation : operations)
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO SAMPLE VALUES (1, 'before'), (0, 'before')");
			try (var rs = statement.executeQuery("SELECT NAME FROM SAMPLE ORDER BY ID DESC")) {
				assertTrue(rs.next());
				assertEquals("PUBLIC.;value", rs.getString(1));
				assertTrue(rs.next());
				assertEquals("before", rs.getString(1));
			}
		}
	}

	@Test
	void normalizationPreservesQualifiedNamesAndLiteralContents() {
		var table = new com.sqlapp.data.schemas.Table("T").setSchemaName("PUBLIC");
		String sql = "SELECT 'PUBLIC.;value' FROM PUBLIC.T";
		assertEquals(sql, HsqlUtils.normalizeStatement(table, sql));
		assertEquals("RETURN 'a;b'", HsqlUtils.formatStatement("RETURN 'a;b'"));
	}

	@Test
	void completeRoutineDefinitionsDoNotRequireDuplicateComponents() {
		var dialect = DialectResolver.getInstance().getDialect("hsql", 2, 7);
		var registry = dialect.createSqlFactoryRegistry();
		var function = new com.sqlapp.data.schemas.Function("F");
		function.setDefinition("CREATE FUNCTION F() RETURNS INTEGER RETURN 1");
		assertEquals(String.join("\n", function.getDefinition()),
				registry.createSql(function, SqlType.CREATE).get(0).getSqlText());
		var procedure = new com.sqlapp.data.schemas.Procedure("P");
		procedure.setDefinition("CREATE PROCEDURE P() BEGIN ATOMIC END");
		assertEquals(String.join("\n", procedure.getDefinition()),
				registry.createSql(procedure, SqlType.CREATE).get(0).getSqlText());
		var domain = new com.sqlapp.data.schemas.Domain("D");
		domain.setDefinition("CREATE DOMAIN D AS INTEGER CHECK (VALUE > 0)");
		assertEquals(String.join("\n", domain.getDefinition()),
				registry.createSql(domain, SqlType.CREATE).get(0).getSqlText());
	}

	@Test
	void domainMetadataUsesOneQueryForTheWholeSet() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:query-count-regression", "SA", "");
				var statement = connection.createStatement()) {
			for (int i = 0; i < 12; i++) {
				statement.execute("CREATE DOMAIN D" + i + " AS INTEGER CHECK (VALUE > 0) CHECK (VALUE < 10)");
			}
			var count = new java.util.concurrent.atomic.AtomicInteger();
			var observed = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
					java.sql.Connection.class.getClassLoader(), new Class<?>[] { java.sql.Connection.class },
					(proxy, method, args) -> {
						if (method.getName().equals("prepareStatement"))
							count.incrementAndGet();
						try {
							return method.invoke(connection, args);
						} catch (java.lang.reflect.InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader()
					.getDomainReader();
			reader.setSchemaName("PUBLIC");
			assertEquals(12, reader.getAll(observed).size());
			assertEquals(1, count.get());
		}
	}
}

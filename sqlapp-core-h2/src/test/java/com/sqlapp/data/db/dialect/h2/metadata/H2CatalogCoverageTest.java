/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import static com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class H2CatalogCoverageTest {
	@ParameterizedTest
	@ValueSource(strings = { "DECIMAL(18,4)", "VARCHAR(42)", "ENUM('x','it''s')", "INTEGER ARRAY[4]",
			"INTERVAL DAY(3) TO SECOND(4)", "ROW(X INT,Y VARCHAR(3))", "JSON", "CLOB" })
	void preservesDomainTypesAndNamedChecks(String type) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement()) {
			statement.execute("CREATE DOMAIN D AS " + type
					+ " CONSTRAINT \"not null check\" CHECK(VALUE IS NOT NULL) CONSTRAINT \"valid check\" CHECK(TRUE)");
			var domain = read(source, "PUBLIC").getDomains().get("D");
			assertEquals(2, domain.getChecks().size());
			String xml = domain.asXml();
			create(target, domain);
			assertEquals(xml, domain.asXml());
			for (var connection : new Connection[] { source, target })
				try (var command = connection.createStatement()) {
					command.execute("CREATE TABLE T(C D)");
				}
			assertEquals(nativeType(source, "PUBLIC"), nativeType(target, "PUBLIC"));
			assertEquals(domain.getChecks().stream().map(check -> check.getName()).toList(), read(target, "PUBLIC")
					.getDomains().get("D").getChecks().stream().map(check -> check.getName()).toList());
			try (var command = target.createStatement()) {
				assertThrows(SQLException.class, () -> command.execute("INSERT INTO T VALUES(NULL)"));
			}
		}
	}

	@Test
	void preservesParentDomainConstraintsRatherThanFlatteningItsType() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			statement.execute("CREATE DOMAIN Z_PARENT AS INT CHECK(VALUE>0)");
			statement.execute("CREATE DOMAIN A_CHILD AS Z_PARENT CHECK(VALUE<5)");
			var schema = read(source, "PUBLIC");
			create(target, schema.getDomains().get("Z_PARENT"));
			create(target, schema.getDomains().get("A_CHILD"));
			destination.execute("CREATE TABLE T(C A_CHILD)");
			destination.execute("INSERT INTO T VALUES(3)");
			assertThrows(SQLException.class, () -> destination.execute("INSERT INTO T VALUES(0)"));
			assertThrows(SQLException.class, () -> destination.execute("INSERT INTO T VALUES(6)"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "PUBLIC", "odd\" schema" })
	void recreatesFiltersAndDropsQualifiedSynonyms(String schema) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			for (var command : new java.sql.Statement[] { statement, destination }) {
				command.execute("CREATE TABLE PUBLIC.T(ID INT)");
				command.execute("INSERT INTO PUBLIC.T VALUES(7)");
				if (!schema.equals("PUBLIC"))
					command.execute("CREATE SCHEMA " + quote(schema));
			}
			statement.execute("CREATE SYNONYM " + quote(schema) + ".\"synonym name\" FOR PUBLIC.T");
			statement.execute("CREATE SYNONYM " + quote(schema) + ".OTHER FOR PUBLIC.T");
			var reader = DialectResolver.getInstance().getDialect(source).getCatalogReader().getSchemaReader()
					.getSynonymReader();
			reader.setSchemaName(schema);
			reader.setObjectName("synonym name");
			var filtered = reader.getAllFull(source);
			assertEquals(1, filtered.size());
			var synonym = filtered.get(0);
			assertEquals("PUBLIC", synonym.getObjectSchemaName());
			assertEquals("T", synonym.getObjectName());
			create(target, synonym);
			try (var rows = destination.executeQuery("SELECT ID FROM " + quote(schema) + ".\"synonym name\"")) {
				assertTrue(rows.next());
				assertEquals(7, rows.getInt(1));
			}
			var registry = DialectResolver.getInstance().getDialect(target).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			for (var operation : registry.createSql(synonym, SqlType.DROP))
				destination.execute(operation.getSqlText());
			assertThrows(SQLException.class,
					() -> destination.executeQuery("SELECT * FROM " + quote(schema) + ".\"synonym name\""));
		}
	}

	@Test
	void metadataQueriesStayConstantAsTheRequestedObjectSetGrows() throws Exception {
		int small = queryCount(1), large = queryCount(12);
		assertTrue(small > 0);
		assertEquals(small, large, "Metadata must use set-based queries rather than one query per object");
	}

	private int queryCount(int count) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			for (int i = 0; i < count; i++) {
				statement.execute("CREATE DOMAIN D" + i + " AS INT CHECK(VALUE>=0)");
				statement.execute("CREATE TABLE T" + i + "(ID INT PRIMARY KEY,C D" + i + ")");
				statement.execute("CREATE INDEX IX" + i + " ON T" + i + "(C)");
				statement.execute("CREATE VIEW V" + i + " AS SELECT * FROM T" + i);
				statement.execute("CREATE SYNONYM S" + i + " FOR T" + i);
			}
			var queries = new AtomicInteger();
			var proxy = (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
					new Class<?>[] { Connection.class }, (object, method, args) -> {
						if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
								&& sql.toUpperCase(java.util.Locale.ROOT).contains("INFORMATION_SCHEMA"))
							queries.incrementAndGet();
						try {
							return method.invoke(connection, args);
						} catch (java.lang.reflect.InvocationTargetException ex) {
							throw ex.getCause();
						}
					});
			var schema = read(proxy, "PUBLIC");
			assertEquals(count, schema.getTables().size());
			assertEquals(count, schema.getDomains().size());
			assertEquals(count, schema.getSynonyms().size());
			return queries.get();
		}
	}
}

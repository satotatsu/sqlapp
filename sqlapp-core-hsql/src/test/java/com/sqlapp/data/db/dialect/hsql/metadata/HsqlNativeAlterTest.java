/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;

class HsqlNativeAlterTest {
	@Test
	void unreferencedDomainRenameAndDefaultsExecuteWhileTypeAlterFailsExplicitly() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:native-rename", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			statement.execute("CREATE TYPE APP.\"Old.Type\" AS INTEGER");
			statement.execute(
					"CREATE DOMAIN APP.\"Old.Domain\" AS INTEGER DEFAULT 5 CONSTRAINT POSITIVE CHECK(VALUE > 0)");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader();
			reader.setSchemaName("APP");
			var schema = reader.getAllFull(connection).get(0);
			var type = schema.getTypes().get("Old.Type");
			var renamedType = type.clone().setName("New.Type");
			var domain = schema.getDomains().get("Old.Domain");
			var renamedDomain = domain.clone().setName("New.Domain").setDefaultValue("8");
			var registry = dialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(type.diff(renamedType), SqlType.ALTER));
			for (var operation : registry.createSql(domain.diff(renamedDomain), SqlType.ALTER))
				statement.execute(operation.getSqlText());
			assertNotNull(reader.getAllFull(connection).get(0).getTypes().get("Old.Type"));
			assertNotNull(reader.getAllFull(connection).get(0).getDomains().get("New.Domain"));
			assertEquals("8", reader.getAllFull(connection).get(0).getDomains().get("New.Domain").getDefaultValue());
			for (var operation : registry.createSql(renamedDomain.diff(domain), SqlType.ALTER))
				statement.execute(operation.getSqlText());
			statement.execute("CREATE TABLE APP.T (V APP.\"Old.Type\", D APP.\"Old.Domain\")");
			statement.execute("INSERT INTO APP.T(V) VALUES (CAST(3 AS APP.\"Old.Type\"))");
			statement.execute("INSERT INTO APP.T(V) VALUES (CAST(4 AS APP.\"Old.Type\"))");
			try (var rs = statement.executeQuery("SELECT SUM(CAST(V AS INTEGER)), SUM(D) FROM APP.T")) {
				assertTrue(rs.next());
				assertEquals(7, rs.getInt(1));
				assertEquals(10, rs.getInt(2));
			}
			assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO APP.T(D) VALUES (-1)"));
			var rejectedRename = registry.createSql(domain.diff(renamedDomain), SqlType.ALTER);
			assertThrows(java.sql.SQLException.class, () -> statement.execute(rejectedRename.get(0).getSqlText()));
			assertEquals("5", reader.getAllFull(connection).get(0).getDomains().get("Old.Domain").getDefaultValue());
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(type.diff(type.clone().setStatement("BIGINT")), SqlType.ALTER));
		}
	}

	@Test
	void synonymRetargetAndRenamePreserveBothTargets() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:native-synonym", "SA", "");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE A(V INTEGER)");
			statement.execute("CREATE TABLE B(V INTEGER)");
			statement.execute("INSERT INTO A VALUES 1");
			statement.execute("INSERT INTO B VALUES 2");
			statement.execute("CREATE SYNONYM \"Old.Alias\" FOR PUBLIC.A");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = dialect.getCatalogReader().getSchemaReader().getSynonymReader();
			reader.setSchemaName("PUBLIC");
			var original = reader.getAll(connection).get(0);
			var target = original.clone().setName("New.Alias").setObjectName("B");
			var registry = dialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target.clone().setObjectName(null)), SqlType.ALTER));
			for (var operation : registry.createSql(original.diff(target), SqlType.ALTER))
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery("SELECT V FROM \"New.Alias\"")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt(1));
			}
			for (var operation : registry.createSql(target.diff(original), SqlType.ALTER))
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery("SELECT V FROM \"Old.Alias\"")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt(1));
			}
			try (var rs = statement
					.executeQuery("SELECT (SELECT SUM(V) FROM A)+(SELECT SUM(V) FROM B) FROM (VALUES(0))")) {
				assertTrue(rs.next());
				assertEquals(3, rs.getInt(1));
			}
		}
	}
}

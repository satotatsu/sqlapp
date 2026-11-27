/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.PrivilegeState;

class HsqlPrivilegesTest {
	@Test
	void usageColumnAndRoutineFamilyGrantsRecreateAndRevoke() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:hsqldb:mem:privileges-source", "SA", "");
				var sql = source.createStatement()) {
			sql.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			sql.execute("CREATE ROLE READER");
			sql.execute("CREATE TABLE APP.\"Rights.Table\"(\"Allowed.Column\" INTEGER, \"Other.Column\" INTEGER)");
			sql.execute("CREATE SEQUENCE APP.\"Counter.Name\"");
			sql.execute("CREATE DOMAIN APP.\"Domain.Name\" AS INTEGER");
			sql.execute("CREATE TYPE APP.\"Type.Name\" AS INTEGER");
			sql.execute("CREATE FUNCTION APP.\"F.Name\"(V INTEGER) RETURNS INTEGER SPECIFIC \"Int.Specific\" RETURN V");
			sql.execute(
					"CREATE FUNCTION APP.\"F.Name\"(V VARCHAR(20)) RETURNS INTEGER SPECIFIC \"Text.Specific\" RETURN LENGTH(V)");
			sql.execute("GRANT SELECT(\"Allowed.Column\") ON APP.\"Rights.Table\" TO READER WITH GRANT OPTION");
			sql.execute("GRANT USAGE ON SEQUENCE APP.\"Counter.Name\" TO READER");
			sql.execute("GRANT USAGE ON DOMAIN APP.\"Domain.Name\" TO READER");
			sql.execute("GRANT USAGE ON TYPE APP.\"Type.Name\" TO READER");
			sql.execute("GRANT EXECUTE ON SPECIFIC ROUTINE APP.\"Int.Specific\" TO READER");
			var dialect = DialectResolver.getInstance().getDialect(source);
			var reader = dialect.getCatalogReader();
			var schemas = reader.getSchemaReader();
			schemas.setSchemaName("APP");
			var model = new Catalog("PUBLIC");
			model.getSchemas().addAll(schemas.getAllFull(source));
			var objects = reader.getObjectPrivilegeReader();
			objects.setSchemaName("APP");
			model.getObjectPrivileges()
					.addAll(objects.getAll(source).stream().filter(p -> "READER".equals(p.getGranteeName())).toList());
			var columns = reader.getColumnPrivilegeReader();
			columns.setSchemaName("APP");
			model.getColumnPrivileges()
					.addAll(columns.getAll(source).stream().filter(p -> "READER".equals(p.getGranteeName())).toList());
			model.getRoutinePrivileges().addAll(reader.getRoutinePrivilegeReader().getAll(source).stream()
					.filter(p -> "READER".equals(p.getGranteeName())).toList());
			assertEquals(3, model.getObjectPrivileges().size());
			assertEquals(1, model.getColumnPrivileges().size(), model.getColumnPrivileges().toString());
			assertEquals(2, model.getRoutinePrivileges().size(), model.getRoutinePrivileges().toString());
			objects.setObjectName("Counter.Name");
			assertEquals(1, objects.getAll(source).stream().filter(p -> "READER".equals(p.getGranteeName())).count());
			var registry = dialect.createSqlFactoryRegistry();
			var operations = registry.createSql(model, SqlType.CREATE);
			assertEquals(6, operations.stream().filter(op -> op.getSqlType() == SqlType.GRANT).count());
			var partial = model.getRoutinePrivileges().get(0).clone();
			partial.getSpecifics().put("hsql.routineGrantScope.v1", "SPECIFIC");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(partial, SqlType.GRANT));
			try (var target = DriverManager.getConnection("jdbc:hsqldb:mem:privileges-target", "SA", "");
					var statement = target.createStatement()) {
				statement.execute("CREATE ROLE READER"); // principals are provisioned separately
				for (var operation : operations)
					statement.execute(operation.getSqlText());
				for (var privilege : model.getObjectPrivileges()) {
					for (var operation : registry.createSql(privilege.clone().setState(PrivilegeState.Revoke),
							SqlType.REVOKE))
						statement.execute(operation.getSqlText());
					for (var operation : registry.createSql(privilege, SqlType.GRANT))
						statement.execute(operation.getSqlText());
				}
				for (var privilege : model.getColumnPrivileges()) {
					for (var operation : registry.createSql(privilege.clone().setState(PrivilegeState.Revoke),
							SqlType.REVOKE))
						statement.execute(operation.getSqlText());
					for (var operation : registry.createSql(privilege, SqlType.GRANT))
						statement.execute(operation.getSqlText());
				}
				for (var privilege : model.getRoutinePrivileges()) {
					for (var operation : registry.createSql(privilege.clone().setState(PrivilegeState.Revoke),
							SqlType.REVOKE))
						statement.execute(operation.getSqlText());
					for (var operation : registry.createSql(privilege, SqlType.GRANT))
						statement.execute(operation.getSqlText());
				}
				try (var rs = statement.executeQuery(
						"SELECT COLUMN_NAME, IS_GRANTABLE FROM INFORMATION_SCHEMA.COLUMN_PRIVILEGES WHERE GRANTEE='READER' AND TABLE_SCHEMA='APP'")) {
					assertTrue(rs.next());
					assertEquals("Allowed.Column", rs.getString(1));
					assertEquals("YES", rs.getString(2));
					assertFalse(rs.next());
				}
				try (var rs = statement.executeQuery(
						"SELECT SPECIFIC_NAME FROM INFORMATION_SCHEMA.ROUTINE_PRIVILEGES WHERE GRANTEE='READER' AND ROUTINE_SCHEMA='APP'")) {
					var names = new java.util.HashSet<String>();
					while (rs.next())
						names.add(rs.getString(1));
					assertEquals(java.util.Set.of("Int.Specific", "Text.Specific"), names);
				}
			}
		}
	}

	@Test
	void rowFilteredMetadataCannotBeRecreatedAsUnrestrictedGrants() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:filtered-right", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE T(V INTEGER)");
			sql.execute("CREATE ROLE READER");
			sql.execute("GRANT SELECT FILTER (WHERE V > 0) ON T TO READER");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var privileges = dialect.getCatalogReader().getColumnPrivilegeReader().getAll(connection).stream()
					.filter(p -> "READER".equals(p.getGranteeName())).toList();
			assertFalse(privileges.isEmpty());
			for (var privilege : privileges) {
				assertEquals("FILTERED", privilege.getSpecifics().get("hsql.privilegeFilterScope.v1"));
				assertThrows(IllegalArgumentException.class,
						() -> dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
			}
		}
	}

	@Test
	void explicitTriggerGrantIsNotRetainedByBundledEngine() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:trigger-right", "SA", "");
				var sql = connection.createStatement()) {
			sql.execute("CREATE TABLE T(V INTEGER)");
			sql.execute("CREATE ROLE READER");
			sql.execute("GRANT DELETE, TRIGGER ON T TO READER");
			try (var rs = sql.executeQuery(
					"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_PRIVILEGES WHERE GRANTEE='READER' AND PRIVILEGE_TYPE='TRIGGER'")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
			var privilege = new com.sqlapp.data.schemas.ObjectPrivilege();
			privilege.setSchemaName("PUBLIC");
			privilege.setObjectName("T");
			privilege.setGranteeName("READER");
			privilege.setPrivilege("TRIGGER");
			assertThrows(IllegalArgumentException.class, () -> DialectResolver.getInstance().getDialect(connection)
					.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT));
		}
	}

	@Test
	void legacyPrivilegeQueryRemainsSeparateFromModernCatalogs() {
		class Reader extends HsqlObjectPrivilegeReader {
			Reader() {
				super(DialectResolver.getInstance().getDialect("hsql", 2, 0));
			}

			String query(int major) {
				return getSqlNode(new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(major)).toString();
			}
		}
		var reader = new Reader();
		assertTrue(reader.query(1).contains("role_table_grants"));
		assertTrue(reader.query(2).contains("table_privileges"));
		assertFalse(reader.query(2).contains("rug.udt_name"));
	}
}

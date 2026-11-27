/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.db2;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/**
 * Specific method grants are checked separately from structured-type
 * reconstruction.
 */
final class Db2MethodPrivilegeAssertions {
	private Db2MethodPrivilegeAssertions() {
	}

	static void verify(Connection connection) throws Exception {
		String schema = "SQLAPP_METHOD_AUDIT", role = "SQLAPP_METHOD_AUDIT_ROLE";
		var dialect = DialectResolver.getInstance().getDialect(connection);
		Deque<String> cleanup = new ArrayDeque<>();
		Throwable failure = null;
		try (var statement = connection.createStatement()) {
			try {
				statement.execute("CREATE SCHEMA " + schema);
				cleanup.push("DROP SCHEMA " + schema + " RESTRICT");
				statement.execute("CREATE TYPE " + schema + ".MT AS (ID INTEGER) NOT FINAL MODE DB2SQL");
				cleanup.push("DROP TYPE " + schema + ".MT");
				statement.execute("CREATE ROLE " + role);
				cleanup.push("DROP ROLE " + role);
				String specific;
				try (var rows = statement
						.executeQuery("SELECT SPECIFICNAME FROM SYSCAT.ROUTINES WHERE SUBJECT_TYPESCHEMA='" + schema
								+ "' AND SUBJECT_TYPENAME='MT' AND ROUTINETYPE='M' FETCH FIRST 1 ROW ONLY")) {
					assertTrue(rows.next());
					specific = rows.getString(1).stripTrailing();
				}
				var grant = new RoutinePrivilege().setSchemaName(schema).setObjectName("METHOD")
						.setSpecificName(specific).setPrivilege("EXECUTE").setGranteeName(role);
				grant.getSpecifics().putAll(Map.of("GRANTEE_TYPE", "R", "ROUTINE_TYPE", "M"));
				var registry = dialect.createSqlFactoryRegistry();
				SqlFactory<RoutinePrivilege> factory = registry.getSqlFactory(grant, SqlType.GRANT);
				for (var operation : factory.createSql(grant))
					statement.execute(operation.getSqlText());
				var grants = dialect.getCatalogReader().getRoutinePrivilegeReader().getAllFull(connection).stream()
						.filter(privilege -> role.equals(privilege.getGranteeName())
								&& specific.equals(privilege.getSpecificName()))
						.toList();
				assertEquals(1, grants.size());
				assertEquals("M", grants.getFirst().getSpecifics().get("ROUTINE_TYPE"));
				RoutinePrivilege restored = SchemaUtils.readXml(new java.io.StringReader(grants.getFirst().asXml()));
				SqlFactory<RoutinePrivilege> revoke = registry.getSqlFactory(restored, SqlType.REVOKE);
				for (var operation : revoke.createSql(restored))
					statement.execute(operation.getSqlText());
				assertTrue(dialect.getCatalogReader().getRoutinePrivilegeReader().getAllFull(connection).stream()
						.noneMatch(privilege -> role.equals(privilege.getGranteeName())));
			} catch (Exception | AssertionError exception) {
				failure = exception;
				throw exception;
			} finally {
				SQLException cleanupFailure = null;
				for (String sql : cleanup)
					try {
						statement.execute(sql);
					} catch (SQLException exception) {
						if (cleanupFailure == null)
							cleanupFailure = exception;
						else
							cleanupFailure.addSuppressed(exception);
					}
				if (cleanupFailure != null) {
					if (failure == null)
						throw cleanupFailure;
					failure.addSuppressed(cleanupFailure);
				}
			}
		}
	}
}

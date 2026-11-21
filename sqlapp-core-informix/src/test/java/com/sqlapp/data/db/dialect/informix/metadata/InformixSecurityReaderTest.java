/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixSecurityReaderTest {
	private static class Rows {
		final List<Map<String, Object>> rows = new ArrayList<>();
		int queries;

		JdbcQueryHandler feed(SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
			assertNotNull(node);
			assertFalse(node.eval(context).getSql().isBlank());
			queries++;
			try {
				for (var row : rows)
					handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return null;
		}
	}

	@Test
	void catalogInstallsAllSecurityReaders() {
		final var catalog = DialectHolder.defaultDialect.getCatalogReader();
		assertInstanceOf(InformixUserReader.class, catalog.getUserReader());
		assertInstanceOf(InformixRoleReader.class, catalog.getRoleReader());
		assertInstanceOf(InformixRoleMemberReader.class, catalog.getRoleMemberReader());
		assertInstanceOf(InformixRoutinePrivilegeReader.class, catalog.getRoutinePrivilegeReader());
	}

	@Test
	void readsDatabaseUsersAndRolesWithoutPasswordsInOneQueryEach() {
		final var data = new Rows();
		for (String kind : List.of("C", "D", "R", "U"))
			data.rows.add(
					new HashMap<>(Map.of("user_name", "user_" + kind, "user_type", kind, "default_role", "reader")));
		final var users = new InformixUserReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(n, p, h);
			}
		};
		final var result = users.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, data.queries);
		assertEquals(4, result.size());
		assertEquals("D", result.get(1).getSpecifics().get("INFORMIX_USER_TYPE"));
		assertEquals("reader", result.get(1).getSpecifics().get("INFORMIX_DEFAULT_ROLE"));
		assertNull(result.get(1).getPassword());
		data.rows.get(0).put("user_type", "?");
		assertThrows(IllegalStateException.class, () -> users.doGetAll(null, new ParametersContext(), null));
		data.rows.clear();
		data.queries = 0;
		data.rows.add(Map.of("role_name", "reader"));
		final var roles = new InformixRoleReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(n, p, h);
			}
		};
		assertEquals("reader", roles.doGetAll(null, new ParametersContext(), null).get(0).getName());
		assertEquals(1, data.queries);
	}

	@Test
	void readsMembershipGrantAndDefaultFlagsInOneQuery() {
		final var data = new Rows();
		for (int i = 0; i < 20; i++)
			data.rows.add(new HashMap<>(Map.of("role_name", "reader", "grantee", "user" + i, "is_grantable",
					i % 2 == 0 ? "Y" : "N", "default_role", i % 2 == 0 ? "reader" : "other")));
		final var reader = new InformixRoleMemberReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				return data.feed(n, p, h);
			}
		};
		final var members = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, data.queries);
		assertEquals(20, members.size());
		assertTrue(members.get(0).isAdmin());
		assertTrue(members.get(0).isDefault());
		assertFalse(members.get(1).isAdmin());
		assertFalse(members.get(1).isDefault());
		data.rows.get(0).put("is_grantable", "?");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void readsOverloadedRoutineGrantsWithoutPerRoutineQueries() {
		final var data = new Rows();
		for (int i = 0; i < 20; i++) {
			final Map<String, Object> row = new HashMap<>();
			row.put("catalog_name", "db");
			row.put("schema_name", "owner");
			row.put("routine_name", "routine");
			row.put("specific_name", "overload" + i);
			row.put("argument_count", 1);
			row.put("routine_id", i + 1);
			row.put("routine_parameter_types", "2");
			row.put("routine_kind", "FUNCTION");
			row.put("routine_auth", i % 2 == 0 ? "E" : "e");
			row.put("grantor", "owner");
			row.put("grantee", "reader");
			data.rows.add(row);
		}
		final var reader = new InformixRoutinePrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				if (n.eval(p).getSql().contains("sysproccolumns")) {
					data.queries++;
					return null;
				}
				return data.feed(n, p, h);
			}
		};
		final var grants = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, data.queries);
		assertEquals(20, grants.size());
		assertEquals("overload0", grants.get(0).getSpecificName());
		assertEquals("SPECIFIC_NAME", grants.get(0).getSpecifics().get("INFORMIX_ROUTINE_IDENTITY"));
		assertEquals(
				"GRANT EXECUTE ON SPECIFIC FUNCTION \"owner\".\"overload0\" TO \"reader\" WITH GRANT OPTION AS \"owner\"",
				DialectHolder.defaultDialect.createSqlFactoryRegistry()
						.createSql(grants.get(0), com.sqlapp.data.db.sql.SqlType.GRANT).get(0).getSqlText());
		assertEquals("EXECUTE", grants.get(0).getPrivilege());
		assertTrue(grants.get(0).isGrantable());
		assertFalse(grants.get(1).isGrantable());
		data.rows.get(0).put("specific_name", null);
		final var unnamed = reader.doGetAll(null, new ParametersContext(), null).get(0);
		assertEquals("PARAMETER_TYPE_IDS", unnamed.getSpecifics().get("INFORMIX_ROUTINE_IDENTITY"));
		assertThrows(IllegalArgumentException.class, () -> DialectHolder.defaultDialect.createSqlFactoryRegistry()
				.createSql(unnamed, com.sqlapp.data.db.sql.SqlType.GRANT));
		data.rows.get(0).put("routine_auth", "?");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void resolvesUnnamedSignaturesInOneBatchAndKeepsUnsupportedTypesUnresolved() {
		var privileges = new Rows();
		for (int id : java.util.List.of(101, 102, 103, 104, 105, 106)) {
			var row = new HashMap<String, Object>(Map.of("catalog_name", "db", "schema_name", "owner", "routine_name",
					"routine", "routine_id", id, "argument_count", 2, "routine_parameter_types", "2 7", "routine_kind",
					"PROCEDURE", "routine_auth", "e", "grantor", "owner", "grantee", "reader"));
			if (id == 102)
				row.put("grantee", "second_reader");
			if (id == 102)
				row.put("routine_id", 101);
			if (id == 103)
				row.put("routine_parameter_types", "2 10");
			if (id >= 105)
				row.put("routine_parameter_types", id == 105 ? "13 45" : "13 7");
			privileges.rows.add(row);
		}
		var parameters = new Rows();
		for (int id : java.util.List.of(101, 103, 104)) {
			parameters.rows.add(Map.of("routine_id", id, "parameter_id", 2, "parameter_type", id == 101 ? 7 : 10,
					"extended_type_id", 0, "parameter_mode", 1));
			parameters.rows.add(Map.of("routine_id", id, "parameter_id", 1, "parameter_type", 2, "extended_type_id",
					id == 104 ? 250 : 0, "parameter_mode", 1, "type_owner", "types_owner", "type_name", "code_t",
					"type_kind", "D"));
			if (id == 104)
				parameters.rows.set(parameters.rows.size() - 2, Map.of("routine_id", id, "parameter_id", 2,
						"parameter_type", 7, "extended_type_id", 0, "parameter_mode", 1));
		}
		parameters.rows.add(Map.of("routine_id", 105, "parameter_id", 1, "parameter_type", 13, "extended_type_id", 0,
				"parameter_mode", 1, "parameter_length", 42));
		parameters.rows.add(Map.of("routine_id", 105, "parameter_id", 2, "parameter_type", 45, "extended_type_id", 0,
				"parameter_mode", 1));
		parameters.rows.add(Map.of("routine_id", 106, "parameter_id", 1, "parameter_type", 13, "extended_type_id", 0,
				"parameter_mode", 1, "parameter_length", 298));
		parameters.rows.add(Map.of("routine_id", 106, "parameter_id", 2, "parameter_type", 7, "extended_type_id", 0,
				"parameter_mode", 1));
		var reader = new InformixRoutinePrivilegeReader(DialectHolder.defaultDialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				String query = n.eval(p).getSql();
				if (query.contains("sysproccolumns"))
					return parameters.feed(n, p, h);
				assertTrue(query.contains("p.owner IN"), query);
				return privileges.feed(n, p, h);
			}
		};
		var context = new ParametersContext();
		context.put("schemaName", "owner");
		var grants = reader.doGetAll(null, context, null);
		assertEquals(1, privileges.queries);
		assertEquals(1, parameters.queries);
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var grant = com.sqlapp.data.db.sql.SqlType.GRANT;
		assertTrue(registry.createSql(grants.get(0), grant).getFirst().getSqlText()
				.contains("\"routine\"(INTEGER, DATE)"));
		assertTrue(registry.createSql(grants.get(1), grant).getFirst().getSqlText()
				.contains("\"routine\"(INTEGER, DATE)"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grants.get(2), grant));
		assertTrue(registry.createSql(grants.get(3), grant).getFirst().getSqlText()
				.contains("\"routine\"(\"types_owner\".\"code_t\", DATE)"));
		grants.get(3).getSpecifics().put(com.sqlapp.data.db.dialect.informix.InformixRoutineSignature.TYPES,
				"types_owner.code_t()");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grants.get(3), grant));
		assertTrue(registry.createSql(grants.get(4), grant).getFirst().getSqlText().contains("(VARCHAR(42), BOOLEAN)"));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(grants.get(5), grant));
		parameters.rows.add(parameters.rows.getFirst());
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, context, null));
		parameters.rows.removeLast();
		privileges.rows.getFirst().remove("argument_count");
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, context, null));
	}
}

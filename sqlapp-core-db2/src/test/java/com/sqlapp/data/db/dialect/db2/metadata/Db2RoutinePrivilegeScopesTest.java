/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2RoutinePrivilegeScopesTest {
	private Map<String, String> row(String kind) {
		return new HashMap<>(Map.of("schema_name", "A.Schema", "object_name", "*", "ROUTINETYPE", kind, "EXECUTEAUTH",
				"G", "GRANTOR", "OWNER", "GRANTEE", "TEAM", "GRANTEETYPE", "R"));
	}

	private RoutinePrivilege read(Map<String, String> row) throws SQLException {
		return new Db2RoutinePrivilegeReader(DialectHolder.Db2_1155Dialect)
				.createPrivilege(Db2MetadataParityTest.row(row));
	}

	private String sql(RoutinePrivilege privilege, SqlType type) {
		SqlFactory<RoutinePrivilege> factory = DialectHolder.Db2_1155Dialect.createSqlFactoryRegistry()
				.getSqlFactory(privilege, type);
		return factory.createSql(privilege).getFirst().getSqlText();
	}

	@Test
	void specificMethodsRetainQuotedIdentityAndGrantOptions() throws Exception {
		var row = row("M");
		row.putAll(Map.of("object_name", "M", "SPECIFICNAME", "M\".S"));
		var privilege = read(row);
		assertEquals("GRANT EXECUTE ON SPECIFIC METHOD \"A.Schema\".\"M\"\".S\" TO ROLE TEAM WITH GRANT OPTION",
				sql(privilege, SqlType.GRANT));
		assertEquals("REVOKE EXECUTE ON SPECIFIC METHOD \"A.Schema\".\"M\"\".S\" FROM ROLE TEAM RESTRICT",
				sql(privilege, SqlType.REVOKE));
		assertNull(privilege.getSpecifics().get("DB2_ROUTINE_SCOPE"));
	}

	@Test
	void futureFunctionProcedureAndMethodScopesRecreateAfterCatalogXml() throws Exception {
		Catalog catalog = new Catalog();
		for (String kind : List.of("F", "P", "M"))
			catalog.getRoutinePrivileges().add(read(row(kind)));
		var typeRow = row("M");
		typeRow.putAll(Map.of("TYPESCHEMA", "A.Schema", "TYPENAME", "T\".Q", "object_name", "T\".Q"));
		catalog.getRoutinePrivileges().add(read(typeRow));
		Catalog restored = SchemaUtils.readXml(new StringReader(catalog.asXml()));
		assertEquals(catalog, restored);
		assertEquals(catalog, catalog.clone());
		var privileges = restored.getRoutinePrivileges();
		assertEquals(4, privileges.size());
		assertTrue(sql(privileges.get(0), SqlType.GRANT).contains("ON FUNCTION \"A.Schema\".*"));
		assertTrue(sql(privileges.get(1), SqlType.GRANT).contains("ON PROCEDURE \"A.Schema\".*"));
		assertTrue(sql(privileges.get(2), SqlType.GRANT).contains("ON METHOD * FOR \"A.Schema\".*"));
		assertTrue(sql(privileges.get(3), SqlType.GRANT).contains("ON METHOD * FOR \"A.Schema\".\"T\"\".Q\""));
		assertTrue(sql(privileges.get(3), SqlType.REVOKE).endsWith("FROM ROLE TEAM RESTRICT"));
		SqlFactory<Catalog> factory = DialectHolder.Db2_1155Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				SqlType.CREATE);
		assertEquals(4, factory.createSql(restored).stream()
				.filter(operation -> operation.getSqlType() == SqlType.GRANT).count());
	}

	@Test
	void invalidFlagsAmbiguousTypesAndImplicitWildcardScopesFail() throws Exception {
		var row = row("M");
		row.put("EXECUTEAUTH", "?");
		assertThrows(SQLException.class, () -> read(row));
		row.put("EXECUTEAUTH", "G");
		row.put("TYPENAME", "T");
		assertThrows(SQLException.class, () -> read(row));
		row.remove("TYPENAME");
		var privilege = read(row);
		privilege.getSpecifics().remove("DB2_ROUTINE_SCOPE");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
		privilege.getSpecifics().put("DB2_ROUTINE_SCOPE", "SCHEMA");
		privilege.setSpecificName("S");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
		privilege.setSpecificName(null);
		privilege.getSpecifics().put("DB2_ROUTINE_SCOPE", "UNKNOWN");
		assertThrows(IllegalArgumentException.class, () -> sql(privilege, SqlType.GRANT));
	}

	@Test
	void allRoutineScopesUseOneBulkQueryWithoutExpandingToExistingObjects() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2RoutinePrivilegeReader(DialectHolder.Db2_1155Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("LEFT JOIN"));
				for (int i = 0; i < 20; i++)
					for (String kind : List.of("F", "P", "M"))
						try {
							var row = row(kind);
							row.put("schema_name", "S" + i);
							handler.handleResultSetNext(Db2MetadataParityTest.row(row));
						} catch (SQLException exception) {
							throw new IllegalStateException(exception);
						}
				return null;
			}
		};
		assertEquals(60, reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(1, queries.get());
	}

	@Test
	void historicalReadersKeepTheirCatalogQueryAndRejectNewWildcardGeneration() throws Exception {
		var reader = new Db2RoutinePrivilegeReader(DialectHolder.Db2_1050Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				assertFalse(node.toString().contains("LEFT JOIN"));
				assertFalse(node.toString().contains("a.TYPENAME"));
				return null;
			}
		};
		assertTrue(reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).isEmpty());
		var privilege = read(row("F"));
		SqlFactory<RoutinePrivilege> factory = DialectHolder.Db2_1050Dialect.createSqlFactoryRegistry()
				.getSqlFactory(privilege, SqlType.GRANT);
		assertThrows(IllegalArgumentException.class, () -> factory.createSql(privilege));
	}
}

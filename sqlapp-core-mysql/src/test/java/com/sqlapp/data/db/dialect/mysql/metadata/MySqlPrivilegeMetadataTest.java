/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.SqlParameterCollection;
import com.sqlapp.jdbc.sql.node.SqlNode;

class MySqlPrivilegeMetadataTest {
	private final Dialect dialect = dialect(8, 4, 0);

	@ParameterizedTest
	@CsvSource({ "5,5,3,false", "5,7,9,false", "8,0,0,true", "8,0,1,true", "8,4,0,true", "9,0,0,true" })
	void resolverKeepsModernRoleCatalogsOffOlderServers(int major, int minor, int revision, boolean roles) {
		var catalog = dialect(major, minor, revision).getCatalogReader();
		assertEquals(roles, catalog.getRoleReader() instanceof MySqlRole800Reader);
		assertEquals(roles, catalog.getRoleMemberReader() instanceof MySqlRoleMember800Reader);
		assertInstanceOf(MySqlRoutinePrivilegeReader.class, catalog.getRoutinePrivilegeReader());
	}

	@Test
	void accountIdentityRoundTripsQuotesBackslashesAndHosts() {
		for (String user : new String[] { "reader", "O'Reilly", "back\\slash", "a`b@c", "", "日本語" }) {
			for (String host : new String[] { "localhost", "%", "10.0.%", "a'b\\c", "" }) {
				String name = MySqlAccount.name(user, host);
				assertEquals(name, MySqlAccount.normalize(name));
				assertEquals(name, MySqlAccount.fromGrantee("'" + user + "'@'" + host + "'"));
				assertEquals(user, MySqlAccount.user(name));
				assertEquals(host, MySqlAccount.host(name));
				assertEquals("`" + user.replace("`", "``") + "`@`" + host.replace("`", "``") + "`", MySqlAccount.sql(name));
			}
		}
		assertEquals("'O''Reilly'@'%'", MySqlAccount.normalize("'O\\'Reilly'@'%'"));
		for (String invalid : new String[] { "reader", "reader@localhost", "'reader'", "'r'@'h'; DROP USER x", "'r'@'h" })
			assertThrows(IllegalArgumentException.class, () -> MySqlAccount.sql(invalid));
		assertThrows(IllegalArgumentException.class, () -> MySqlAccount.fromGrantee("'a'@'b'@'host'"));
	}

	@Test
	void usersAndExistingGrantsShareTheCompleteAccountIdentity() throws Exception {
		Catalog catalog = new Catalog();
		for (String host : new String[] { "localhost", "%" }) {
			try (var rows = rows(List.of(values("User", "reader", "Host", host)))) {
				rows.next();
				var user = new MySqlUserReader(dialect).createUser(new ExResultSet(rows));
				catalog.getUsers().add(user);
			}
			try (var rows = rows(List.of(values("GRANTEE", MySqlAccount.name("reader", host), "TABLE_CATALOG", "def",
					"TABLE_SCHEMA", "app", "TABLE_NAME", "orders", "PRIVILEGE_TYPE", "SELECT", "IS_GRANTABLE", "YES")))) {
				rows.next();
				var privilege = new MySqlObjectPrivilegeReader(dialect).createPrivilege(new ExResultSet(rows));
				catalog.getObjectPrivileges().add(privilege);
			}
		}
		assertEquals(2, catalog.getUsers().size());
		assertEquals(2, catalog.getObjectPrivileges().size());
		for (var privilege : catalog.getObjectPrivileges())
			assertSame(catalog.getUsers().get(privilege.getGranteeName()), privilege.getGrantee());
	}

	@Test
	void routineKindsAndGrantOptionRemainDistinctAndRecreatable() throws Exception {
		Catalog catalog = new Catalog();
		for (String kind : new String[] { "FUNCTION", "PROCEDURE" }) {
			try (var rows = rows(List.of(routine(kind, "Execute,Alter Routine,Grant")))) {
				rows.next();
				var privileges = new MySqlRoutinePrivilegeReader(dialect).createPrivileges(new ExResultSet(rows));
				assertEquals(2, privileges.size());
				for (var privilege : privileges) {
					assertTrue(privilege.isGrantable());
					assertEquals(kind, privilege.getSpecifics().get("ROUTINE_KIND"));
					catalog.getRoutinePrivileges().add(privilege);
				}
			}
		}
		assertEquals(4, catalog.getRoutinePrivileges().size());
		try (var rows = rows(List.of(routine("FUNCTION", "Grant")))) {
			rows.next();
			var grantOnly = new MySqlRoutinePrivilegeReader(dialect).createPrivileges(new ExResultSet(rows));
			assertEquals("GRANT OPTION", grantOnly.get(0).getPrivilege());
			assertFalse(grantOnly.get(0).isGrantable());
		}
		try (var rows = rows(List.of(routine("FUNCTION", "unknown")))) {
			rows.next();
			assertThrows(IllegalArgumentException.class,
					() -> new MySqlRoutinePrivilegeReader(dialect).createPrivileges(new ExResultSet(rows)));
		}
	}

	@Test
	void routineRetrievalRemainsOneBatchAndFailuresPropagate() {
		List<Map<String, Object>> metadata = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			var row = routine("FUNCTION", "Execute");
			row.put("Routine_name", "f" + i);
			metadata.add(row);
		}
		int[] queries = { 0 };
		var reader = new MySqlRoutinePrivilegeReader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				queries[0]++;
				assertTrue(node.toString().contains("mysql.procs_priv"));
				assertFalse(node.toString().contains("SELECT *"));
				feed(handler, metadata);
				return null;
			}
		};
		assertEquals(12, reader.doGetAll(null, new ParametersContext(), version()).size());
		assertEquals(1, queries[0]);
		assertTrue(reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()
				.setMajorVersion(5).setMinorVersion(0).setRevision(2)).isEmpty());
		assertEquals(1, queries[0]);
		var denied = new MySqlRoutinePrivilegeReader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				throw new IllegalStateException("permission denied");
			}
		};
		assertThrows(IllegalStateException.class, () -> denied.doGetAll(null, new ParametersContext(), version()));
	}

	@Test
	void knownRolesAndExplicitAccountFiltersUseOneBatch() {
		assertEquals("'READER'@'host'", new MySqlRole800Reader(dialect).nativeCaseString(null, "'READER'@'host'"));
		assertEquals("READER", new MySqlUserReader(dialect).nativeCaseString(null, "READER"));
		assertEquals("'READER'@'host'", new MySqlRoleMember800Reader(dialect).nativeCaseString(null, "'READER'@'host'"));
		int[] queries = { 0 };
		var reader = new MySqlRole800Reader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				assertEquals("a'role", p.get("roleName"));
				assertEquals("%", p.get("accountHost"));
				assertTrue(n.toString().contains("mysql.default_roles"));
				assertFalse(n.toString().contains("account_locked"));
				feed(h, List.of(values("role_user", "a'role", "role_host", "%")));
				return null;
			}
		};
		ParametersContext context = new ParametersContext();
		context.put("roleName", "'a''role'@'%'");
		assertEquals("'a''role'@'%'", reader.doGetAll(null, context, version()).get(0).getName());
		assertEquals(1, queries[0]);
		assertEquals("'a''role'@'%'", context.get("roleName"));
		var users = new MySqlUserReader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				assertEquals("a'role", p.get("userName"));
				assertEquals("%", p.get("accountHost"));
				SqlParameterCollection sql = new SqlParameterCollection(); n.eval(p, sql);
				assertTrue(sql.getSql().contains("Host ="), sql.getSql());
				return null;
			}
		};
		context = new ParametersContext(); context.put("userName", "'a''role'@'%'");
		assertTrue(users.doGetAll(null, context, version()).isEmpty());
		context = new ParametersContext(); context.put("userName", ""); context.put("accountHost", "");
		SqlParameterCollection anonymous = new SqlParameterCollection();
		users.getSqlSqlNode(version()).eval(context, anonymous);
		assertTrue(anonymous.getSql().contains("User IN"), anonymous.getSql());
		assertTrue(anonymous.getSql().contains("Host ="), anonymous.getSql());
	}

	@Test
	void existingGlobalPrivilegePathRetainsDynamicNamesAndHostIdentity() throws Exception {
		try (var rows = rows(List.of(values("GRANTEE", "'reader'@'%'", "TABLE_CATALOG", "def",
				"PRIVILEGE_TYPE", "BINLOG_ADMIN", "IS_GRANTABLE", "YES")))) {
			rows.next();
			var privilege = new MySqlUserPrivilegeReader(dialect).createPrivilege(new ExResultSet(rows));
			assertEquals("BINLOG_ADMIN", privilege.getPrivilege());
			assertEquals("'reader'@'%'", privilege.getGranteeName());
			assertTrue(privilege.isGrantable());
		}
	}

	@Test
	void roleEdgesRetainDirectionHostsAdminAndDefaultsInOneBatch() {
		int[] queries = { 0 };
		var reader = new MySqlRoleMember800Reader(dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				queries[0]++;
				assertTrue(n.toString().contains("d.USER=e.TO_USER"));
				assertEquals("reader", p.get("granteeUser"));
				assertEquals("localhost", p.get("granteeHost"));
				SqlParameterCollection sql = new SqlParameterCollection();
				n.eval(p, sql);
				assertTrue(sql.getSql().contains("e.TO_USER ="), sql.getSql());
				feed(h, List.of(
						values("FROM_USER", "app_role", "FROM_HOST", "%", "TO_USER", "reader", "TO_HOST", "localhost", "WITH_ADMIN_OPTION", "Y", "IS_DEFAULT", "Y"),
						values("FROM_USER", "app_role", "FROM_HOST", "localhost", "TO_USER", "reader", "TO_HOST", "%", "WITH_ADMIN_OPTION", "N", "IS_DEFAULT", "N")));
				return null;
			}
		};
		ParametersContext context = new ParametersContext();
		context.put("grantee", "'reader'@'localhost'");
		var members = reader.doGetAll(null, context, version());
		assertEquals(1, queries[0]);
		assertEquals(2, members.size());
		assertEquals("'app_role'@'%'", members.get(0).getMemberRoleName());
		assertEquals("'reader'@'localhost'", members.get(0).getGranteeName());
		assertTrue(members.get(0).isAdmin());
		assertTrue(members.get(0).isDefault());
		assertFalse(members.get(1).isAdmin());
		assertFalse(members.get(1).isDefault());
		assertNull(context.get("granteeUser"));
	}

	private static Map<String, Object> routine(String kind, String privileges) {
		return values("Db", "app", "Routine_name", "same_name", "Routine_type", kind, "User", "reader", "Host", "%", "Proc_priv", privileges);
	}
	private static Dialect dialect(int major, int minor, int revision) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, revision);
	}
	private static ProductVersionInfo version() { return new ProductVersionInfo().setMajorVersion(8).setMinorVersion(4).setRevision(0); }
	private static Map<String, Object> values(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
		return map;
	}
	private static void feed(ResultSetNextHandler handler, List<Map<String, Object>> data) {
		try (var rows = rows(data)) {
			ExResultSet rs = new ExResultSet(rows);
			while (rows.next()) handler.handleResultSetNext(rs);
		} catch (SQLException e) { throw new IllegalStateException(e); }
	}
	private static CachedRowSet rows(List<Map<String, Object>> data) throws SQLException {
		var first = data.get(0);
		RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
		metadata.setColumnCount(first.size());
		int index = 0;
		for (String name : first.keySet()) {
			metadata.setColumnName(++index, name);
			metadata.setColumnLabel(index, name);
			metadata.setColumnType(index, Types.VARCHAR);
		}
		CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
		rows.setMetaData(metadata);
		for (int i = data.size() - 1; i >= 0; i--) {
			rows.moveToInsertRow(); index = 0;
			for (Object value : data.get(i).values()) rows.updateObject(++index, value);
			rows.insertRow(); rows.moveToCurrentRow();
		}
		rows.beforeFirst(); return rows;
	}
}

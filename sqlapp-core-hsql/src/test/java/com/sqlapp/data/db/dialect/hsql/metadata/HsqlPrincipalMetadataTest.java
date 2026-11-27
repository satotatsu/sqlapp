/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;

class HsqlPrincipalMetadataTest {
	private static final String LEAF = "Role.\"Reader";

	private static String quoted(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}

	private Connection open(String name) throws Exception {
		return DriverManager.getConnection("jdbc:hsqldb:mem:principals-" + name, "SA", "");
	}

	@Test
	void readsDirectEdgesWithoutAdministratorExpansionOrImplicitPublicMembership() throws Exception {
		try (var c = open("direct"); var s = c.createStatement()) {
			s.execute("CREATE ROLE " + quoted(LEAF));
			s.execute("CREATE ROLE PARENT");
			s.execute("CREATE ROLE UNUSED");
			s.execute("CREATE USER \"User.Name\" PASSWORD ''");
			s.execute("CREATE USER A PASSWORD '' ADMIN");
			s.execute("GRANT " + quoted(LEAF) + " TO PARENT");
			s.execute("GRANT PARENT TO \"User.Name\"");
			s.execute("GRANT PARENT TO A");
			var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader();
			var edges = reader.getRoleMemberReader().getAll(c);
			assertTrue(edges.stream()
					.anyMatch(m -> LEAF.equals(m.getMemberRoleName()) && "PARENT".equals(m.getGranteeName())));
			assertTrue(edges.stream()
					.anyMatch(m -> "PARENT".equals(m.getMemberRoleName()) && "User.Name".equals(m.getGranteeName())));
			assertFalse(edges.stream()
					.anyMatch(m -> LEAF.equals(m.getMemberRoleName()) && "User.Name".equals(m.getGranteeName())));
			assertFalse(edges.stream()
					.anyMatch(m -> "UNUSED".equals(m.getMemberRoleName()) || "PUBLIC".equals(m.getMemberRoleName())));
			assertFalse(edges.stream().anyMatch(RoleMember::isAdmin));
			assertTrue(edges.stream().anyMatch(
					m -> "A".equals(m.getGranteeName()) && "YES".equals(m.getSpecifics().get("IS_GRANTABLE"))));
			var filtered = reader.getRoleMemberReader();
			filtered.setGrantee("User.Name");
			assertEquals(1, filtered.getAll(c).size());
			try (var user = DriverManager.getConnection("jdbc:hsqldb:mem:principals-direct", "User.Name", "")) {
				var roles = DialectResolver.getInstance().getDialect(user).getCatalogReader().getRoleReader()
						.getAll(user);
				assertEquals(roles.size(), roles.stream().map(Role::getName).distinct().count());
				assertTrue(roles.stream().anyMatch(r -> LEAF.equals(r.getName())));
				assertFalse(roles.stream().anyMatch(r -> "UNUSED".equals(r.getName())));
			}
		}
	}

	@Test
	void catalogXmlRecreationAndRevokePreserveNestedRoleAccess() throws Exception {
		try (var source = open("source");
				var target = open("target");
				var s = source.createStatement();
				var t = target.createStatement()) {
			s.execute("CREATE ROLE " + quoted(LEAF));
			s.execute("CREATE ROLE PARENT");
			s.execute("CREATE USER \"User.Name\" PASSWORD ''");
			s.execute("CREATE TABLE T(N INTEGER)");
			s.execute("GRANT SELECT ON T TO " + quoted(LEAF));
			s.execute("GRANT " + quoted(LEAF) + " TO PARENT");
			s.execute("GRANT PARENT TO \"User.Name\"");
			t.execute("CREATE USER \"User.Name\" PASSWORD ''");
			t.execute("CREATE TABLE T(N INTEGER)");
			t.execute("INSERT INTO T VALUES(7)");
			var reader = DialectResolver.getInstance().getDialect(source).getCatalogReader();
			var catalog = new Catalog("PUBLIC");
			catalog.getRoles().addAll(reader.getRoleReader().getAll(source));
			catalog.getRoleMembers().addAll(reader.getRoleMemberReader().getAll(source));
			for (var p : reader.getObjectPrivilegeReader().getAll(source))
				if (LEAF.equals(p.getGranteeName()))
					catalog.getObjectPrivileges().add(p);
			for (var p : reader.getColumnPrivilegeReader().getAll(source))
				if (LEAF.equals(p.getGranteeName()))
					catalog.getColumnPrivileges().add(p);
			Catalog restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
			var registry = DialectResolver.getInstance().getDialect(target).createSqlFactoryRegistry();
			var operations = registry.createSql(restored, SqlType.CREATE);
			assertTrue(operations.stream().anyMatch(o -> o.getSqlText().startsWith("CREATE ROLE")));
			for (var op : operations)
				t.execute(op.getSqlText());
			try (var user = DriverManager.getConnection("jdbc:hsqldb:mem:principals-target", "User.Name", "");
					var u = user.createStatement();
					var rs = u.executeQuery("SELECT N FROM T")) {
				assertTrue(rs.next());
				assertEquals(7, rs.getInt(1));
			}
			var membership = new RoleMember().setMemberRoleName("PARENT").setGranteeName("User.Name");
			for (var op : registry.createSql(membership, SqlType.REVOKE))
				t.execute(op.getSqlText());
			try (var user = DriverManager.getConnection("jdbc:hsqldb:mem:principals-target", "User.Name", "");
					var u = user.createStatement()) {
				assertThrows(SQLException.class, () -> u.executeQuery("SELECT N FROM T"));
			}
		}
	}

	@Test
	void unsupportedRoleOptionsFailExplicitlyAndNativeEngineRejectsAdminOption() throws Exception {
		try (var c = open("options"); var s = c.createStatement()) {
			s.execute("CREATE ROLE R");
			s.execute("CREATE USER U PASSWORD ''");
			assertThrows(SQLException.class, () -> s.execute("GRANT R TO U WITH ADMIN OPTION"));
			var registry = DialectResolver.getInstance().getDialect(c).createSqlFactoryRegistry();
			var member = new RoleMember().setMemberRoleName("R").setGranteeName("U");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(member.clone().setAdmin(true), SqlType.GRANT));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(member.clone().setDefault(true), SqlType.GRANT));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(member.clone().setMemberRoleName("PUBLIC"), SqlType.GRANT));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(member.clone().setGranteeName("_SYSTEM"), SqlType.GRANT));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(member.clone().setGranteeName(null), SqlType.GRANT));
		}
	}

	@Test
	void rolesAndMembershipsUseOneQueryRegardlessOfRoleCount() throws Exception {
		try (var c = open("query-count"); var s = c.createStatement()) {
			s.execute("CREATE USER U PASSWORD ''");
			for (int i = 0; i < 12; i++) {
				s.execute("CREATE ROLE R" + i);
				s.execute("GRANT R" + i + " TO U");
			}
			var count = new AtomicInteger();
			var proxy = counted(c, count);
			var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader();
			reader.getRoleReader().getAll(proxy);
			assertEquals(1, count.getAndSet(0));
			var memberships = reader.getRoleMemberReader();
			memberships.setGrantee("U");
			assertEquals(12, memberships.getAll(proxy).size());
			assertEquals(1, count.get());
		}
	}

	@Test
	void propertiesAreReadAndFilteredWithoutChangingSettings() throws Exception {
		try (var c = open("settings"); var s = c.createStatement()) {
			s.execute("SET DATABASE SQL NAMES TRUE");
			var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader().getSettingReader();
			reader.setObjectName("sql.enforce_names");
			var count = new AtomicInteger();
			var values = reader.getAll(counted(c, count));
			assertEquals(1, count.get());
			assertEquals(1, values.size());
			assertEquals("true", values.getFirst().getValue());
			assertEquals("SQL", values.getFirst().getSpecifics().get("PROPERTY_SCOPE"));
			var catalog = new Catalog("PUBLIC");
			catalog.getSettings().addAll(values);
			Catalog restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
			assertEquals(values.getFirst(), restored.getSettings().get("sql.enforce_names"));
			try (var rs = s.executeQuery(
					"SELECT PROPERTY_VALUE FROM INFORMATION_SCHEMA.SYSTEM_PROPERTIES WHERE PROPERTY_NAME='sql.enforce_names'")) {
				assertTrue(rs.next());
				assertEquals("true", rs.getString(1));
			}
			s.execute("CREATE USER U PASSWORD ''");
			try (var user = DriverManager.getConnection("jdbc:hsqldb:mem:principals-settings", "U", "")) {
				var visible = DialectResolver.getInstance().getDialect(user).getCatalogReader().getSettingReader()
						.getAll(user);
				assertFalse(visible.isEmpty());
				assertTrue(visible.stream().allMatch(v -> "SQL".equals(v.getSpecifics().get("PROPERTY_SCOPE"))));
			}
		}
	}

	@Test
	void commonCatalogEntryPointLoadsRolesMembershipsAndPropertiesTogether() throws Exception {
		try (var c = open("catalog"); var s = c.createStatement()) {
			s.execute("CREATE SCHEMA APP AUTHORIZATION DBA");
			s.execute("CREATE TABLE APP.T(N INTEGER)");
			s.execute("CREATE ROLE R");
			s.execute("CREATE USER U PASSWORD ''");
			s.execute("GRANT R TO U");
			var reader = DialectResolver.getInstance().getDialect(c).getCatalogReader();
			reader.getSchemaReader().setSchemaName("APP");
			var catalog = reader.getAllFull(c).getFirst();
			assertNotNull(catalog.getRoles().get("R"));
			assertNotNull(catalog.getUsers().get("U"));
			assertNotNull(catalog.getSettings().get("sql.enforce_names"));
			assertTrue(catalog.getRoleMembers().stream()
					.anyMatch(m -> "R".equals(m.getMemberRoleName()) && "U".equals(m.getGranteeName())));
			Catalog restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(catalog.asXml()));
			assertEquals(catalog.getRoleMembers(), restored.getRoleMembers());
		}
	}

	@Test
	void modernCatalogsAreNotQueriedForLegacyVersions() {
		var dialect = DialectResolver.getInstance().getDialect("hsql", 2, 0);
		var version = new ProductVersionInfo().setMajorVersion(1);
		assertTrue(new HsqlRoleMemberReader(dialect).doGetAll(null, new ParametersContext(), version).isEmpty());
		assertTrue(new HsqlSettingReader(dialect).doGetAll(null, new ParametersContext(), version).isEmpty());
		class Reader extends HsqlRoleReader {
			Reader() {
				super(dialect);
			}

			String query(int major) {
				return getSqlNode(new ProductVersionInfo().setMajorVersion(major)).toString();
			}
		}
		assertTrue(new Reader().query(1).contains("applicable_roles"));
		assertTrue(new Reader().query(2).contains("AUTHORIZATIONS"));
	}

	private Connection counted(Connection c, AtomicInteger count) {
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
				new Class<?>[] { Connection.class }, (proxy, method, args) -> {
					if (method.getName().equals("prepareStatement"))
						count.incrementAndGet();
					try {
						return method.invoke(c, args);
					} catch (InvocationTargetException e) {
						throw e.getCause();
					}
				});
	}
}

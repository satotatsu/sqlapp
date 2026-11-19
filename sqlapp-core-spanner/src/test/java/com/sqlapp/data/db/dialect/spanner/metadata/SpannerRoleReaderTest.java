/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.exceptions.SqlappException;

class SpannerRoleReaderTest {
	@Test
	void recreatesReadTableAndColumnGrants() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var grant = com.sqlapp.data.db.sql.SqlType.GRANT;
		final var readerGrant = SpannerObjectPrivilegeReader.toPrivilege("", "READ_Audit", "EXECUTE", "Reader",
				"TABLE FUNCTION");
		assertEquals("GRANT EXECUTE ON TABLE FUNCTION `READ_Audit` TO ROLE `Reader`",
				registry.createSql(readerGrant, grant).getFirst().getSqlText());
		assertEquals("REVOKE EXECUTE ON TABLE FUNCTION `READ_Audit` FROM ROLE `Reader`",
				registry.createSql(readerGrant, com.sqlapp.data.db.sql.SqlType.REVOKE).getFirst().getSqlText());
		assertEquals("GRANT SELECT ON CHANGE STREAM `Audit` TO ROLE `Reader`",
				registry.createSql(SpannerObjectPrivilegeReader.toPrivilege("", "Audit", "SELECT", "Reader",
						"CHANGE STREAM"), grant).getFirst().getSqlText());
		assertThrows(IllegalArgumentException.class,
				() -> SpannerObjectPrivilegeReader.toPrivilege("", "Audit", "UPDATE", "Reader", "CHANGE STREAM"));
		assertEquals("GRANT SELECT ON TABLE `Sales`.`Orders` TO ROLE `Reader`",
				registry.createSql(SpannerObjectPrivilegeReader.toPrivilege("Sales", "Orders", "SELECT", "Reader"),
						grant).getFirst().getSqlText());
		assertEquals("GRANT UPDATE( `Label`) ON TABLE `Orders` TO ROLE `Reader`",
				registry.createSql(SpannerColumnPrivilegeReader.toPrivilege("", "Orders", "Label", "UPDATE", "Reader"),
						grant).getFirst().getSqlText());
		assertThrows(IllegalArgumentException.class,
				() -> SpannerColumnPrivilegeReader.toPrivilege("", "Orders", "Label", "DELETE", "Reader"));
	}

	@Test
	void preservesMembershipDirectionAndDistinguishesAbsentCatalogs() {
		final var member = SpannerRoleMemberReader.membership("Reader", "App");
		assertEquals("Reader", member.getMemberRoleName());
		assertEquals("App", member.getGranteeName());
		assertThrows(IllegalArgumentException.class, () -> SpannerRoleMemberReader.membership("Reader", null));
		final var absent = new SQLException("Table not found: INFORMATION_SCHEMA.ROLE_GRANTEES [at 1:1]");
		assertTrue(SpannerRoleReader.isMissingCatalogTable(absent, "ROLE_GRANTEES"));
		assertFalse(SpannerRoleReader.isMissingRoleCatalog(absent));
		assertFalse(SpannerRoleReader.isMissingRoleCatalog(
				new SQLException("Table not found: INFORMATION_SCHEMA.ROLES_OTHER")));
	}

	private static final String QUERY = "SELECT ROLE_NAME FROM INFORMATION_SCHEMA.ROLES "
			+ "WHERE IS_SYSTEM = FALSE ORDER BY ROLE_NAME";
	private final SpannerRoleReader reader = new SpannerRoleReader(DialectHolder.defaultDialect);

	@Test
	void readsUserRolesInOneQueryAndPreservesCase() throws Exception {
		final var index = new AtomicInteger(-1);
		final var queries = new AtomicInteger();
		final var closes = new AtomicInteger();
		final var names = List.of("AppReader", "OrderWriter");
		final var rows = proxy(ResultSet.class, (self, method, args) -> switch (method.getName()) {
			case "next" -> index.incrementAndGet() < names.size();
			case "getString" -> names.get(index.get());
			case "close" -> closes.incrementAndGet();
			default -> throw new AssertionError(method);
		});
		final var connection = connection((self, method, args) -> {
			if ("close".equals(method.getName())) return closes.incrementAndGet();
			assertEquals("executeQuery", method.getName());
			assertEquals(QUERY, args[0]);
			queries.incrementAndGet();
			return rows;
		});
		assertEquals(List.of("AppReader", "OrderWriter"), reader.doGetAll(connection, null, null)
				.stream().map(Role::getName).toList());
		assertEquals(1, queries.get());
		assertEquals(2, closes.get());
	}

	@Test
	void toleratesAnAbsentRoleCatalogButPropagatesOtherErrors() throws Exception {
		final var calls = new AtomicInteger();
		final var connection = connection((self, method, args) -> {
			if ("close".equals(method.getName())) return null;
			throw new SQLException(calls.getAndIncrement() == 0
					? "Table not found: INFORMATION_SCHEMA.ROLES" : "Permission denied");
		});
		assertTrue(reader.doGetAll(connection, null, null).isEmpty());
		assertThrows(SqlappException.class, () -> reader.doGetAll(connection, null, null));
	}

	private static Connection connection(final java.lang.reflect.InvocationHandler handler) {
		return proxy(Connection.class, (self, method, args) -> {
			assertEquals("createStatement", method.getName());
			return proxy(Statement.class, handler);
		});
	}

	private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
	}
}

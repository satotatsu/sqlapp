/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;

class MySqlRoleSqlTest {
	@ParameterizedTest
	@CsvSource({ "8,0", "8,4", "9,0" })
	void nativeRoleLifecycleAndAdditiveMembership(int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect("MySql", major, minor, 0).createSqlFactoryRegistry();
		Role role = new Role("'O''Reilly`role'@'%'");
		SqlFactory<Role> create = registry.getSqlFactory(role, SqlType.CREATE);
		SqlFactory<Role> drop = registry.getSqlFactory(role, SqlType.DROP);
		assertEquals("CREATE ROLE `O'Reilly``role`@`%`", create.createSql(role).get(0).getSqlText());
		assertEquals("DROP ROLE `O'Reilly``role`@`%`", drop.createSql(role).get(0).getSqlText());
		RoleMember member = member(role.getName(), "'reader'@'localhost'", true);
		member.setAdmin(true);
		SqlFactory<RoleMember> grantFactory = registry.getSqlFactory(member, SqlType.GRANT);
		SqlFactory<RoleMember> revokeFactory = registry.getSqlFactory(member, SqlType.REVOKE);
		var grant = grantFactory.createSql(member);
		assertEquals(1, grant.size());
		assertEquals("GRANT `O'Reilly``role`@`%` TO `reader`@`localhost` WITH ADMIN OPTION", grant.get(0).getSqlText());
		assertEquals("REVOKE `O'Reilly``role`@`%` FROM `reader`@`localhost`",
				revokeFactory.createSql(member).get(0).getSqlText());
		role.setName("ambiguous");
		assertThrows(IllegalArgumentException.class, () -> create.createSql(role));
	}

	@Test
	void restorationGroupsAllDefaultsAndPreservesRecipientHosts() {
		var a = member("'a'@'%'", "'reader'@'localhost'", true);
		var b = member("'b'@'%'", "'reader'@'localhost'", true);
		var c = member("'c'@'%'", "'reader'@'%'", false);
		var factory = factory(a);
		var sql = factory.createRestoreSql(List.of(b, c, a, a)).stream().map(op -> op.getSqlText()).toList();
		assertEquals(List.of("GRANT `c`@`%` TO `reader`@`%`", "GRANT `a`@`%` TO `reader`@`localhost`",
				"GRANT `b`@`%` TO `reader`@`localhost`", "SET DEFAULT ROLE NONE TO `reader`@`%`",
				"SET DEFAULT ROLE `a`@`%`, `b`@`%` TO `reader`@`localhost`"), sql);
		assertTrue(factory.createRestoreSql(List.of()).isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> factory.createRestoreSql(List.of(a, member("'a'@'%'", a.getGranteeName(), false))));
		c.setGranteeName("reader");
		assertThrows(IllegalArgumentException.class, () -> factory.createRestoreSql(List.of(a, c)));
	}

	@ParameterizedTest
	@CsvSource({ "MySql,5,7", "MariaDB,10,5" })
	void olderAndOtherProductsDoNotSelectMySqlRoleFactories(String product, int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect(product, major, minor, 0).createSqlFactoryRegistry();
		Role role = new Role("'a'@'%'");
		assertFalse(registry.getSqlFactory(role, SqlType.CREATE) instanceof MySqlCreateRoleFactory);
		assertFalse(registry.getSqlFactory(member(role.getName(), "'u'@'%'", false),
				SqlType.GRANT) instanceof MySqlGrantRoleMemberFactory);
	}

	private MySqlGrantRoleMemberFactory factory(RoleMember member) {
		return (MySqlGrantRoleMemberFactory) DialectResolver.getInstance().getDialect("MySql", 8, 4, 0)
				.createSqlFactoryRegistry().getSqlFactory(member, SqlType.GRANT);
	}

	private RoleMember member(String role, String recipient, boolean isDefault) {
		RoleMember member = new RoleMember();
		member.setMemberRoleName(role);
		member.setGranteeName(recipient);
		member.setDefault(isDefault);
		return member;
	}
}

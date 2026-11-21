/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;

class InformixRoleFactoriesTest {
	private List<String> sql(Object value, SqlType type) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(value, type).stream()
				.map(operation -> operation.getSqlText()).toList();
	}

	private RoleMember member() {
		return new RoleMember().setMemberRoleName("report role").setGranteeName("analyst");
	}

	private List<String> alter(RoleMember before, RoleMember after) {
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(before.diff(after), SqlType.ALTER)
				.stream().map(operation -> operation.getSqlText()).toList();
	}

	@Test
	void changesDefaultFlagWithoutRevokingMembership() {
		assertEquals(List.of("GRANT DEFAULT ROLE \"report role\" TO \"analyst\""),
				alter(member(), member().setDefault(true)));
		assertEquals(List.of("REVOKE DEFAULT ROLE FROM \"analyst\""), alter(member().setDefault(true), member()));
		assertTrue(alter(member(), member()).isEmpty());
	}

	@Test
	void upgradesGrantOptionAndRejectsImplicitGrantChainRevocation() {
		assertEquals(List.of("GRANT \"report role\" TO \"analyst\" WITH GRANT OPTION"),
				alter(member(), member().setAdmin(true)));
		assertThrows(IllegalArgumentException.class, () -> alter(member().setAdmin(true), member()));
		assertThrows(IllegalArgumentException.class, () -> alter(member(), member().setMemberRoleName("other")));
	}

	@Test
	void createsAndDropsQuotedRoles() {
		assertEquals(List.of("CREATE ROLE \"r\"\"ole\""), sql(new Role("r\"ole"), SqlType.CREATE));
		assertEquals(List.of("DROP ROLE \"report role\""), sql(new Role("report role"), SqlType.DROP));
	}

	@Test
	void grantsAndRevokesOrdinaryMembership() {
		assertEquals(List.of("GRANT \"report role\" TO \"analyst\""), sql(member(), SqlType.CREATE));
		assertEquals(List.of("REVOKE \"report role\" FROM \"analyst\""), sql(member(), SqlType.DROP));
	}

	@Test
	void separatesDefaultRoleFromGrantOption() {
		assertEquals(
				List.of("GRANT \"report role\" TO \"analyst\" WITH GRANT OPTION",
						"GRANT DEFAULT ROLE \"report role\" TO \"analyst\""),
				sql(member().setAdmin(true).setDefault(true), SqlType.GRANT));
		assertEquals(List.of("GRANT DEFAULT ROLE \"report role\" TO \"analyst\""),
				sql(member().setDefault(true), SqlType.GRANT));
		assertEquals(List.of("REVOKE DEFAULT ROLE FROM \"analyst\"", "REVOKE \"report role\" FROM \"analyst\""),
				sql(member().setDefault(true), SqlType.REVOKE));
	}

	@Test
	void supportsPublicWithoutTreatingItAsAnIdentifier() {
		assertEquals(List.of("GRANT DEFAULT ROLE \"report role\" TO PUBLIC"),
				sql(member().setGranteeName("public").setDefault(true), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(member().setGranteeName("PUBLIC").setAdmin(true), SqlType.GRANT));
	}

	@Test
	void rejectsUnsupportedAndMissingModelFields() {
		assertThrows(IllegalArgumentException.class, () -> sql(new Role(), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(new Role("PUBLIC"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(new Role("r").setGrantable(true), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> sql(new RoleMember(), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(member().setGrantee(new Role("other")).setDefault(true), SqlType.GRANT));
		assertThrows(IllegalArgumentException.class,
				() -> sql(member().setGrantee(new Role("other")).setAdmin(true), SqlType.GRANT));
	}
}

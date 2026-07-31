package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresLargeObjectDefaultPrivilegesBuilder.Privilege;

class PostgresLargeObjectDefaultPrivilegesBuilderTest {

	@Test
	void testGrantLargeObjectDefaultPrivileges() {
		String sql = new PostgresLargeObjectDefaultPrivilegesBuilder(
				DialectHolder.postgreSQL180)
				.targetRole("loader")
				.privilege(Privilege.SELECT)
				.privilege(Privilege.UPDATE)
				.grantee("migration_app")
				.publicGrantee()
				.grant(true)
				.build();

		assertEquals(
				"ALTER DEFAULT PRIVILEGES FOR ROLE loader GRANT SELECT, UPDATE ON LARGE OBJECTS TO migration_app, PUBLIC WITH GRANT OPTION",
				sql);
	}

	@Test
	void testRevokeLargeObjectDefaultPrivileges() {
		String sql = new PostgresLargeObjectDefaultPrivilegesBuilder(
				DialectHolder.postgreSQL180)
				.allPrivileges()
				.publicGrantee()
				.revoke(true, true)
				.build();

		assertEquals(
				"ALTER DEFAULT PRIVILEGES REVOKE GRANT OPTION FOR ALL PRIVILEGES ON LARGE OBJECTS FROM PUBLIC CASCADE",
				sql);
	}

	@Test
	void testRejectBeforePostgres18() {
		PostgresLargeObjectDefaultPrivilegesBuilder builder =
				new PostgresLargeObjectDefaultPrivilegesBuilder(
						DialectHolder.postgreSQL170)
						.privilege(Privilege.SELECT)
						.publicGrantee()
						.grant(false);

		assertThrows(IllegalArgumentException.class, builder::build);
	}

	@Test
	void testRequireActionPrivilegeAndGrantee() {
		PostgresLargeObjectDefaultPrivilegesBuilder builder =
				new PostgresLargeObjectDefaultPrivilegesBuilder(
						DialectHolder.postgreSQL180);

		assertThrows(IllegalArgumentException.class, builder::build);
		assertThrows(IllegalArgumentException.class,
				() -> builder.grant(false).build());
		assertThrows(IllegalArgumentException.class,
				() -> builder.privilege(Privilege.SELECT).build());
	}
}

package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.postgres.PostgresPredefinedRole;

class PostgresPredefinedRoleBuilderTest {

	@Test
	void testVersionedRoleDefinition() {
		assertFalse(PostgresPredefinedRole.SIGNAL_AUTOVACUUM_WORKER
				.isSupported(DialectHolder.postgreSQL170));
		assertTrue(PostgresPredefinedRole.SIGNAL_AUTOVACUUM_WORKER
				.isSupported(DialectHolder.postgreSQL180));
		assertEquals("pg_signal_autovacuum_worker",
				PostgresPredefinedRole.SIGNAL_AUTOVACUUM_WORKER.getRoleName());
	}

	@Test
	void testGrantAutovacuumSignalRole() {
		assertEquals(
				"GRANT pg_signal_autovacuum_worker TO operators WITH ADMIN OPTION",
				new PostgresPredefinedRoleBuilder(
						DialectHolder.postgreSQL180)
						.grant(PostgresPredefinedRole.SIGNAL_AUTOVACUUM_WORKER,
								"operators", true));
	}

	@Test
	void testRevokeAutovacuumSignalRole() {
		assertEquals(
				"REVOKE ADMIN OPTION FOR pg_signal_autovacuum_worker FROM operators CASCADE",
				new PostgresPredefinedRoleBuilder(
						DialectHolder.postgreSQL180)
						.revoke(PostgresPredefinedRole.SIGNAL_AUTOVACUUM_WORKER,
								"operators", true, true));
	}

	@Test
	void testRejectRoleBeforeIntroduction() {
		PostgresPredefinedRoleBuilder builder =
				new PostgresPredefinedRoleBuilder(
						DialectHolder.postgreSQL170);

		assertThrows(IllegalArgumentException.class,
				() -> builder.grant(
						PostgresPredefinedRole.SIGNAL_AUTOVACUUM_WORKER,
						"operators", false));
	}
}

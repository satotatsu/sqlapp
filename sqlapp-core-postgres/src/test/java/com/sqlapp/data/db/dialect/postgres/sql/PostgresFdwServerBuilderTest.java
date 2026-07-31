package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresFdwServerBuilderTest {

	@Test
	void testCreateServerWithScramPassthrough() {
		String sql = new PostgresFdwServerBuilder(
				DialectHolder.postgreSQL180, "reporting_server")
				.option("host", "db.example")
				.option("dbname", "reporting")
				.option("application_name", "sqlapp's fdw")
				.useScramPassthrough(true)
				.buildCreate(true);

		assertEquals(
				"CREATE SERVER IF NOT EXISTS reporting_server FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host 'db.example', dbname 'reporting', application_name 'sqlapp''s fdw', use_scram_passthrough 'true')",
				sql);
	}

	@Test
	void testAlterScramPassthrough() {
		assertEquals(
				"ALTER SERVER reporting_server OPTIONS (SET use_scram_passthrough 'false')",
				new PostgresFdwServerBuilder(
						DialectHolder.postgreSQL180, "reporting_server")
						.alterScramPassthrough(false, false));
	}

	@Test
	void testRejectScramPassthroughBeforePostgres18() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFdwServerBuilder(
						DialectHolder.postgreSQL170, "reporting_server")
						.useScramPassthrough(true)
						.buildCreate(false));
	}

	@Test
	void testRejectUserMappingSecretsAsServerOptions() {
		PostgresFdwServerBuilder builder = new PostgresFdwServerBuilder(
				DialectHolder.postgreSQL180, "reporting_server");

		assertThrows(IllegalArgumentException.class,
				() -> builder.option("user", "remote_user"));
		assertThrows(IllegalArgumentException.class,
				() -> builder.option("password", "secret"));
	}
}

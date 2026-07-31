package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresDblinkServerBuilderTest {

	@Test
	void testCreateWithScramPassthrough() {
		assertEquals(
				"CREATE SERVER IF NOT EXISTS reporting FOREIGN DATA WRAPPER dblink_fdw OPTIONS (hostaddr '127.0.0.1', dbname 'reporting''s', use_scram_passthrough 'true')",
				new PostgresDblinkServerBuilder(
						DialectHolder.postgreSQL180, "reporting")
						.option("hostaddr", "127.0.0.1")
						.option("dbname", "reporting's")
						.useScramPassthrough(true)
						.buildCreate(true));
	}

	@Test
	void testCreateWithoutScramOnOlderVersion() {
		assertEquals(
				"CREATE SERVER reporting FOREIGN DATA WRAPPER dblink_fdw OPTIONS (dbname 'reporting')",
				new PostgresDblinkServerBuilder(
						DialectHolder.postgreSQL170, "reporting")
						.option("dbname", "reporting")
						.buildCreate(false));
	}

	@Test
	void testAlterScramPassthrough() {
		assertEquals(
				"ALTER SERVER reporting OPTIONS (SET use_scram_passthrough 'false')",
				new PostgresDblinkServerBuilder(
						DialectHolder.postgreSQL180, "reporting")
						.alterScramPassthrough(false, false));
	}

	@Test
	void testVersionAndSecretValidation() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresDblinkServerBuilder(
						DialectHolder.postgreSQL170, "reporting")
						.useScramPassthrough(true)
						.buildCreate(false));
		PostgresDblinkServerBuilder builder = new PostgresDblinkServerBuilder(
				DialectHolder.postgreSQL180, "reporting");
		assertThrows(IllegalArgumentException.class,
				() -> builder.option("user", "remote"));
		assertThrows(IllegalArgumentException.class,
				() -> builder.option("password", "secret"));
		assertThrows(IllegalArgumentException.class,
				() -> builder.option("use_scram_passthrough", "true"));
	}
}

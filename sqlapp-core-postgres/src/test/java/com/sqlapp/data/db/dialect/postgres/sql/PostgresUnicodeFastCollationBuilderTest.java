package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresUnicodeFastCollationBuilderTest {

	@Test
	void testCreatePgUnicodeFastCollation() {
		assertEquals(
				"CREATE COLLATION IF NOT EXISTS app.unicode_fast (PROVIDER = builtin, LOCALE = 'PG_UNICODE_FAST')",
				new PostgresUnicodeFastCollationBuilder(
						DialectHolder.postgreSQL180)
						.create("app", "unicode_fast", true));
	}

	@Test
	void testDropPgUnicodeFastCollation() {
		assertEquals(
				"DROP COLLATION IF EXISTS app.unicode_fast CASCADE",
				new PostgresUnicodeFastCollationBuilder(
						DialectHolder.postgreSQL180)
						.drop("app", "unicode_fast", true, true));
	}

	@Test
	void testRejectBeforePostgres18() {
		PostgresUnicodeFastCollationBuilder builder =
				new PostgresUnicodeFastCollationBuilder(
						DialectHolder.postgreSQL170);

		assertThrows(IllegalArgumentException.class,
				() -> builder.create("unicode_fast"));
	}

	@Test
	void testRejectEmptyName() {
		PostgresUnicodeFastCollationBuilder builder =
				new PostgresUnicodeFastCollationBuilder(
						DialectHolder.postgreSQL180);

		assertThrows(IllegalArgumentException.class,
				() -> builder.create(""));
	}
}

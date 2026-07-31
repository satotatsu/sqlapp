package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresRegexpFunctionBuilder.Function;

class PostgresRegexpFunctionBuilderTest {

	@Test
	void testRegexpReplaceWithNamedArguments() {
		String sql = new PostgresRegexpFunctionBuilder(
				DialectHolder.postgreSQL180, Function.REPLACE)
				.argument("string", "'A PostgreSQL function'")
				.argument("pattern", "'a|e|i|o|u'")
				.argument("replacement", "'X'")
				.argument("start", "1")
				.argument("N", "3")
				.argument("flags", "'i'")
				.build();

		assertEquals(
				"regexp_replace(string => 'A PostgreSQL function', pattern => 'a|e|i|o|u', replacement => 'X', start => 1, \"N\" => 3, flags => 'i')",
				sql);
	}

	@Test
	void testRegexpInstrWithNamedArguments() {
		String sql = new PostgresRegexpFunctionBuilder(
				DialectHolder.postgreSQL180, Function.INSTR)
				.argument("string", "code")
				.argument("pattern", "'(c..)(...)'")
				.argument("subexpr", "2")
				.build();

		assertEquals(
				"regexp_instr(string => code, pattern => '(c..)(...)', subexpr => 2)",
				sql);
	}

	@Test
	void testRejectBeforePostgres18() {
		PostgresRegexpFunctionBuilder builder =
				new PostgresRegexpFunctionBuilder(
						DialectHolder.postgreSQL170, Function.LIKE)
						.argument("string", "name")
						.argument("pattern", "'^A'");

		assertThrows(IllegalArgumentException.class, builder::build);
	}

	@Test
	void testValidateArguments() {
		PostgresRegexpFunctionBuilder builder =
				new PostgresRegexpFunctionBuilder(
						DialectHolder.postgreSQL180, Function.COUNT);

		assertThrows(IllegalArgumentException.class,
				() -> builder.argument("replacement", "'X'"));
		assertThrows(IllegalArgumentException.class, builder::build);
	}
}

package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresFileFdwOptionsBuilderTest {

	@Test
	void testPostgres18ErrorHandlingOptions() {
		String sql = new PostgresFileFdwOptionsBuilder(
				DialectHolder.postgreSQL180)
				.filename("import/orders.csv")
				.format("CSV")
				.header(true)
				.onError("IGNORE")
				.rejectLimit(100)
				.logVerbosity("silent")
				.build();

		assertEquals(
				"OPTIONS (filename 'import/orders.csv', format 'csv', header 'true', on_error 'ignore', reject_limit '100', log_verbosity 'silent')",
				sql);
	}

	@Test
	void testProgramAndEscaping() {
		assertEquals(
				"OPTIONS (program 'printf ''a,b''', format 'text')",
				new PostgresFileFdwOptionsBuilder(DialectHolder.postgreSQL170)
						.program("printf 'a,b'")
						.build());
	}

	@Test
	void testRejectPostgres18OptionsBeforePostgres18() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFileFdwOptionsBuilder(
						DialectHolder.postgreSQL170)
						.filename("orders.csv")
						.onError("ignore")
						.build());
	}

	@Test
	void testValidateSourceAndErrorHandlingCombinations() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFileFdwOptionsBuilder(
						DialectHolder.postgreSQL180).build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFileFdwOptionsBuilder(
						DialectHolder.postgreSQL180)
						.filename("orders.csv")
						.program("cat orders.csv")
						.build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFileFdwOptionsBuilder(
						DialectHolder.postgreSQL180)
						.filename("orders.csv")
						.rejectLimit(1)
						.build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFileFdwOptionsBuilder(
						DialectHolder.postgreSQL180)
						.filename("orders.csv")
						.format("binary")
						.onError("ignore")
						.build());
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresFileFdwOptionsBuilder(
						DialectHolder.postgreSQL180)
						.filename("orders.csv")
						.onError("stop")
						.logVerbosity("verbose")
						.build());
	}
}

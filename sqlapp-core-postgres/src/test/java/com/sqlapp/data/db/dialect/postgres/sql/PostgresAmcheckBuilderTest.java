package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresAmcheckBuilderTest {

	@Test
	void testGinIndexCheck() {
		PostgresAmcheckBuilder builder =
				new PostgresAmcheckBuilder(DialectHolder.postgreSQL180);

		assertEquals(
				"SELECT gin_index_check('search.idx_documents'::regclass)",
				builder.ginIndexCheck("search", "idx_documents"));
		assertEquals(
				"SELECT gin_index_check('\"Search Data\".\"Document Index\"'::regclass)",
				builder.ginIndexCheck("Search Data", "Document Index"));
	}

	@Test
	void testRejectBeforePostgres18AndEmptyName() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresAmcheckBuilder(
						DialectHolder.postgreSQL170)
						.ginIndexCheck("idx_documents"));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresAmcheckBuilder(
						DialectHolder.postgreSQL180)
						.ginIndexCheck(""));
	}
}

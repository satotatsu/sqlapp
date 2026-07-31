package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresBufferCacheBuilderTest {

	@Test
	void testEvictRelationAndAll() {
		PostgresBufferCacheBuilder builder =
				new PostgresBufferCacheBuilder(DialectHolder.postgreSQL180);

		assertEquals(
				"SELECT * FROM pg_buffercache_evict_relation('archive.events'::regclass)",
				builder.evictRelation("archive", "events"));
		assertEquals(
				"SELECT * FROM pg_buffercache_evict_relation('\"Archive Data\".\"Event Log\"'::regclass)",
				builder.evictRelation("Archive Data", "Event Log"));
		assertEquals("SELECT * FROM pg_buffercache_evict_all()",
				builder.evictAll());
	}

	@Test
	void testRejectBeforePostgres18AndEmptyName() {
		PostgresBufferCacheBuilder postgres17 =
				new PostgresBufferCacheBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class,
				postgres17::evictAll);
		assertThrows(IllegalArgumentException.class,
				() -> postgres17.evictRelation("events"));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresBufferCacheBuilder(
						DialectHolder.postgreSQL180)
						.evictRelation(""));
	}
}

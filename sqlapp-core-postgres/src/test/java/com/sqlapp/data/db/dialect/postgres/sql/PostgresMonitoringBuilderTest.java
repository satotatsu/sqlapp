package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresMonitoringBuilderTest {
	private final PostgresMonitoringBuilder builder = new PostgresMonitoringBuilder(DialectHolder.postgreSQL180);

	@Test
	void selectsRecordsAndAllNewStatementCounters() {
		assertEquals("SELECT * FROM pg_catalog.pg_aios", builder.asynchronousIo());
		assertEquals("SELECT * FROM pg_catalog.pg_stat_get_backend_io(42)", builder.backendIo(42));
		assertEquals("SELECT * FROM pg_catalog.pg_stat_get_backend_wal(42)", builder.backendWal(42));
		assertEquals("SELECT pg_catalog.pg_stat_reset_backend_stats(42)", builder.resetBackendStats(42));
		String expected = "SELECT userid, dbid, toplevel, queryid, query, wal_records, wal_fpi, wal_bytes, "
				+ "wal_buffers_full, parallel_workers_to_launch, parallel_workers_launched FROM pg_stat_statements";
		assertEquals(expected, builder.statementStatistics());
		assertEquals(expected.replace("FROM pg_stat_statements", "FROM \"Extension Data\".pg_stat_statements"),
				builder.statementStatistics("Extension Data"));
		assertThrows(IllegalArgumentException.class, () -> builder.statementStatistics(" "));
	}

	@Test
	void exposesModuleAndNumaInformation() {
		assertEquals("SELECT * FROM pg_catalog.pg_get_loaded_modules()", builder.loadedModules());
		assertEquals("SELECT pg_catalog.pg_numa_available()", builder.numaAvailable());
		assertEquals("SELECT * FROM pg_catalog.pg_shmem_allocations_numa", builder.sharedMemoryNuma());
		assertEquals("SELECT * FROM pg_buffercache_numa", builder.bufferCacheNuma());
	}

	@Test
	void rejectsInvalidProcessIdsAndEarlierVersions() {
		assertThrows(IllegalArgumentException.class, () -> builder.backendIo(0));
		assertThrows(IllegalArgumentException.class, () -> builder.backendWal(-1));
		assertThrows(IllegalArgumentException.class, () -> builder.resetBackendStats(0));
		var old = new PostgresMonitoringBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class, old::asynchronousIo);
		assertThrows(IllegalArgumentException.class, () -> old.backendIo(42));
		assertThrows(IllegalArgumentException.class, () -> old.backendWal(42));
		assertThrows(IllegalArgumentException.class, () -> old.resetBackendStats(42));
		assertThrows(IllegalArgumentException.class, old::statementStatistics);
		assertThrows(IllegalArgumentException.class, old::loadedModules);
		assertThrows(IllegalArgumentException.class, old::numaAvailable);
		assertThrows(IllegalArgumentException.class, old::sharedMemoryNuma);
		assertThrows(IllegalArgumentException.class, old::bufferCacheNuma);
	}
}

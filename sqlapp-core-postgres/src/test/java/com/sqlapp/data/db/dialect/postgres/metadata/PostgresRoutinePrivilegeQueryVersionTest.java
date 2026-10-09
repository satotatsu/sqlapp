package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;

class PostgresRoutinePrivilegeQueryVersionTest {
	@Test void isolatesProcedureCatalogColumnFromLegacyReaders() {
		for (int major : new int[] {8, 10, 11, 15, 18}) {
			var dialect = DialectResolver.getInstance().getDialect("postgres", major, major == 8 ? 3 : 0, null);
			var reader = (PostgresRoutinePrivilegeReader) dialect.getCatalogReader().getRoutinePrivilegeReader();
			String query = reader.getSqlSqlNode(null).toString();
			assertEquals(major >= 11, reader instanceof Postgres110RoutinePrivilegeReader);
			assertEquals(major >= 11, query.contains("p.prokind"));
			assertTrue(query.contains("n.nspname=r.routine_schema"));
		}
	}
}

package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresLogicalInspectBuilderTest {
	@Test
	void quotesSnapshotFilenamesAndRejectsPaths() {
		var builder = new PostgresLogicalInspectBuilder(DialectHolder.postgreSQL180);
		assertEquals("SELECT * FROM pg_get_logical_snapshot_meta('0-40796E18.snap')",
				builder.snapshotMeta("0-40796E18.snap"));
		assertEquals("SELECT * FROM pg_get_logical_snapshot_info('it''s.snap')", builder.snapshotInfo("it's.snap"));
		for (String invalid : new String[] { null, "", " ", ".", "..", "../file", "dir\\file", "bad\0file" }) {
			assertThrows(IllegalArgumentException.class, () -> builder.snapshotMeta(invalid));
			assertThrows(IllegalArgumentException.class, () -> builder.snapshotInfo(invalid));
		}
		var old = new PostgresLogicalInspectBuilder(DialectHolder.postgreSQL170);
		assertThrows(IllegalArgumentException.class, () -> old.snapshotMeta("0-40796E18.snap"));
		assertThrows(IllegalArgumentException.class, () -> old.snapshotInfo("0-40796E18.snap"));
	}
}

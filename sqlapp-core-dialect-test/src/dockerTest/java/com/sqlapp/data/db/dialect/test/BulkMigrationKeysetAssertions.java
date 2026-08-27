/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Connection;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;

/** Shared real-database assertions for portable composite keyset SQL. */
public final class BulkMigrationKeysetAssertions {
	private BulkMigrationKeysetAssertions() {
	}

	public static void assertCompositeResume(final Connection connection, final Table table)
			throws Exception {
		final var source = new JdbcBulkMigrationKeysetSource(connection, table);
		final var all = source.iterator(null);
		assertEquals(Integer.valueOf(1), all.next().get("KEY1"));
		final Row cursor = all.next();
		assertEquals(Integer.valueOf(1), cursor.get("KEY1"));
		assertEquals(Integer.valueOf(2), cursor.get("KEY2"));
		if (all instanceof AutoCloseable closeable) {
			closeable.close();
		}

		final var resumed = source.iterator(source.resumeToken(cursor));
		final Row first = resumed.next();
		assertEquals(Integer.valueOf(2), first.get("KEY1"));
		assertEquals(Integer.valueOf(1), first.get("KEY2"));
		assertEquals("d", resumed.next().get("TXT"));
		assertFalse(resumed.hasNext());
	}
}

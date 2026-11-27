/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounters;
import com.sqlapp.jdbc.bulk.GeneratedKeyCounters.Action;

/**
 * Exercises {@link GeneratedKeyCounters} against a real server: a table with
 * explicitly inserted keys 5 and 42 is raised to the source next value 100, a
 * repeated run never moves it back, and a later automatic insert gets 100.
 */
public final class GeneratedKeyCounterAssertions {
	private GeneratedKeyCounterAssertions() {
	}

	/**
	 * @param insertKeys    inserts the explicit keys 5 and 42 (and nothing else)
	 * @param insertDefault inserts one row with a generated key
	 * @param selectNewKey  selects the key of that generated row
	 */
	public static void assertRaisesPastImportedKeys(final Connection connection, final Table table, final Column column,
			final boolean explicit, final String insertKeys, final String insertDefault, final String selectNewKey)
			throws SQLException {
		assertRaisesPastImportedKeys(connection, table, column, explicit, true, insertKeys, insertDefault,
				selectNewKey);
	}

	/**
	 * @param catalogTracksRestart false for databases whose catalog reports a
	 *                             restart only after the next generated value
	 *                             (Db2); a repeated run is then checked to keep the
	 *                             same restart point
	 */
	public static void assertRaisesPastImportedKeys(final Connection connection, final Table table, final Column column,
			final boolean explicit, final boolean catalogTracksRestart, final String insertKeys,
			final String insertDefault, final String selectNewKey) throws SQLException {
		execute(connection, insertKeys);
		final var restarted = GeneratedKeyCounters.synchronize(connection, table, column, 100L, explicit);
		assertEquals(Action.RESTARTED, restarted.action(), restarted.toString());
		assertEquals(BigInteger.valueOf(100), restarted.next());
		if (catalogTracksRestart) {
			// A smaller source value never moves the generator back.
			final var again = GeneratedKeyCounters.synchronize(connection, table, column, 50L, explicit);
			assertEquals(Action.ALREADY_SAFE, again.action(), again.toString());
		} else {
			final var again = GeneratedKeyCounters.synchronize(connection, table, column, 100L, explicit);
			assertEquals(BigInteger.valueOf(100), again.next(), again.toString());
		}
		execute(connection, insertDefault);
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(selectNewKey)) {
			rows.next();
			assertEquals(100L, rows.getLong(1));
		}
	}

	private static void execute(final Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}

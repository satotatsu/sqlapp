/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

/** Verifies SQLite's multi-row INSERT RETURNING contract used by tree inserts. */
class SqliteInsertReturningTest {

	@Test
	void testMultiRowInsertReturnsEveryIdentityInOrder() throws Exception {
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			connection.createStatement().execute("CREATE TABLE test_table (id INTEGER PRIMARY KEY, txt TEXT)");
			try (PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO test_table(txt) VALUES(?),(?),(?),(?),(?) RETURNING id")) {
				for (int i = 1; i <= 5; i++) {
					statement.setString(i, "row-" + i);
				}
				try (ResultSet keys = statement.executeQuery()) {
					for (long expected = 1; expected <= 5; expected++) {
						assertTrue(keys.next());
						assertEquals(expected, keys.getLong(1));
					}
					assertFalse(keys.next());
				}
			}
		}
	}
}

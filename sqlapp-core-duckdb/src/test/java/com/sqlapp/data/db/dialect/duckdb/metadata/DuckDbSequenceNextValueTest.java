/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.util.CommonUtils;

/**
 * The model's lastValue is the next value to issue, not DuckDB's last issued
 * value.
 */
class DuckDbSequenceNextValueTest {
	@Test
	void readsTheNextValueToIssue() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA s1");
			statement.execute("CREATE SEQUENCE s1.unused START WITH 10 INCREMENT BY 5");
			statement.execute("CREATE SEQUENCE s1.used START WITH 10 INCREMENT BY 5");
			statement.execute("CREATE SEQUENCE s1.down START WITH -1 INCREMENT BY -3 MINVALUE -100 MAXVALUE -1");
			try (var rows = statement
					.executeQuery("SELECT nextval('s1.used'), nextval('s1.used'), nextval('s1.down')")) {
				rows.next();
				assertEquals(15, rows.getLong(2));
			}
			var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader();
			reader.setSchemaName("s1");
			Schema schema = CommonUtils.first(reader.getAllFull(connection));
			assertNull(schema.getSequences().get("unused").getLastValue());
			assertEquals(BigInteger.valueOf(20), schema.getSequences().get("used").getLastValue());
			assertEquals(BigInteger.valueOf(-4), schema.getSequences().get("down").getLastValue());
			try (var rows = statement.executeQuery("SELECT nextval('s1.used')")) {
				rows.next();
				assertEquals(20, rows.getLong(1));
			}
		}
	}
}

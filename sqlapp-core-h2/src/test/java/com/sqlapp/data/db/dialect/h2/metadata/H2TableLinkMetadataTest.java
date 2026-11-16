/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.metadata;

import static com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.read;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

class H2TableLinkMetadataTest {
	@Test
	void doesNotReadLinkedTablesAsOrdinaryOrTemporaryTables() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID INT)");
			statement.execute("CREATE LINKED TABLE LNK('org.h2.Driver', 'jdbc:h2:mem:', '', '', '(SELECT 1 AS X)')");
			var schema = read(connection, "PUBLIC");
			assertNotNull(schema.getTables().get("T"));
			assertNull(schema.getTables().get("LNK"));
			assertNotNull(schema.getTableLinks().get("LNK"));
		}
	}
}

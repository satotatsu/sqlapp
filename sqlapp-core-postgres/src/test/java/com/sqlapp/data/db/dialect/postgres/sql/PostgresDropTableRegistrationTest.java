/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.postgres.resolver.PostgresDialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class PostgresDropTableRegistrationTest {
	@Test void usesPostgresDropSyntaxAcrossSupportedVersions() {
		var resolver = new PostgresDialectResolver();
		for (int[] version : new int[][] { {8,2}, {9,6}, {11,0}, {14,0}, {15,0}, {16,0}, {17,0}, {18,0} }) {
			var dialect = resolver.getDialect("PostgreSQL", version[0], version[1], null);
			var table = new Table("Order").setSchemaName("MixedSchema");
			com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.DROP);
			String sql = factory.createSql(table).getFirst().getSqlText();
			assertTrue(sql.startsWith("DROP TABLE"), sql);
			assertTrue(sql.contains("\"Order\""), sql);
			assertFalse(sql.contains("CONSTRAINTS"), sql);
			assertFalse(sql.contains("CASCADE"), sql);
		}
	}

	@Test void honorsDropIfExistsOption() {
		var dialect = new PostgresDialectResolver().getDialect("PostgreSQL", 16, 0, null);
		var table = new Table("target");
		com.sqlapp.data.db.sql.SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.DROP);
		factory.getOptions().setDropIfExists(true);
		assertTrue(factory.createSql(table).getFirst().getSqlText().contains("IF EXISTS"));
		factory.getOptions().setDropIfExists(false);
		assertFalse(factory.createSql(table).getFirst().getSqlText().contains("IF EXISTS"));
	}
}

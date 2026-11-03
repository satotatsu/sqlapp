/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class PhoenixTruncateTableFactoryTest {
	@Test
	void deletesAllRowsWithQualifiedQuotedIdentityOnBothVersions() {
		for (final var dialect : java.util.List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			final var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			final var table = new Table("Order Details").setSchemaName("Sales");
			final var operations = registry.createSql(table, SqlType.TRUNCATE);
			assertEquals(1, operations.size());
			assertEquals("DELETE FROM \"Sales\".\"Order Details\"", operations.get(0).getSqlText());
			assertEquals(SqlType.TRUNCATE, operations.get(0).getSqlType());
		}
	}
}

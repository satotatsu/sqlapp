/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class Db2TruncateTableFactoryTest {

	private String truncate(Dialect dialect) {
		Table table = new Table("T").setSchemaName("APP");
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.TRUNCATE);
		var sql = factory.createSql(table);
		assertEquals(1, sql.size());
		return sql.getFirst().getSqlText().replaceAll("\\s+", " ").trim();
	}

	@Test
	void truncateStatementRequiresImmediate() {
		for (Dialect dialect : new Dialect[] { DialectHolder.Db2_970Dialect, DialectHolder.Db2_1110Dialect,
				DialectHolder.Db2_1215Dialect }) {
			String sql = truncate(dialect);
			assertTrue(sql.startsWith("TRUNCATE TABLE "), sql);
			assertTrue(sql.endsWith(" IMMEDIATE"), sql);
		}
	}

	@Test
	void releasesBeforeTruncateStatementKeepNotLoggedInitially() {
		assertTrue(truncate(DialectHolder.Db2_950Dialect).endsWith("ACTIVATE NOT LOGGED INITIALLY WITH EMPTY TABLE"));
	}
}

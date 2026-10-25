/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class FirebirdDeleteAllSqlTest {
	@Test
	void regularTableIgnoresTemporaryNameOptions() {
		var table = new Table("quoted \" table");
		com.sqlapp.data.db.sql.SqlFactory<Table> factory = DialectHolder.defaultDialect25.createSqlFactoryRegistry()
				.getSqlFactory(table, SqlType.TRUNCATE);
		factory.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		factory.getTableOptions().setTemporaryTableNameSuffix(t -> "_suffix");
		assertEquals("DELETE FROM \"quoted \"\" table\"", factory.createSql(table).get(0).getSqlText());
	}

	@Test
	void temporaryOperationUsesTheSameDecoratedNameAsCreate() {
		var table = new Table("quoted table");
		com.sqlapp.data.db.sql.SqlFactory<Table> factory = DialectHolder.defaultDialect50.createSqlFactoryRegistry()
				.getSqlFactory(table, SqlType.TRUNCATE_TEMPORARY);
		factory.getTableOptions().setTemporaryTableNamePrefix(t -> "tmp_");
		factory.getTableOptions().setTemporaryTableNameSuffix(t -> "_suffix");
		assertEquals("DELETE FROM \"tmp_quoted table_suffix\"", factory.createSql(table).get(0).getSqlText());
	}
}

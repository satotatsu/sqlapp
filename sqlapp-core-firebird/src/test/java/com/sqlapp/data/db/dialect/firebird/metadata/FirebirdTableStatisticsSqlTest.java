/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.SqlParameterCollection;

/** Table statistics join one statistics group, so each table is read once. */
class FirebirdTableStatisticsSqlTest {
	@Test
	void joinsOnlyDatabaseWideTableStatistics() {
		for (var dialect : new com.sqlapp.data.db.dialect.Dialect[] { DialectHolder.defaultDialect30,
				DialectHolder.defaultDialect40, DialectHolder.defaultDialect50 }) {
			var reader = (Firebird30TableReader) dialect.getCatalogReader().getSchemaReader().getTableReader();
			var context = new ParametersContext();
			context.put("readerOptions", new ReaderOptions());
			var sql = new SqlParameterCollection();
			reader.getSqlSqlNode(new ProductVersionInfo().setMajorVersion(3).setMinorVersion(0)).eval(context, sql);
			assertTrue(sql.getSql().contains("S.MON$STAT_GROUP=0"), sql.getSql());
		}
	}
}

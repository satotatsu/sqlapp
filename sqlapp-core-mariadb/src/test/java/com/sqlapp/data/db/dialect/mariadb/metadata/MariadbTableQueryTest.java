/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;

class MariadbTableQueryTest {
	private static class Reader extends MariadbTable10_27Reader {
		Reader(int major, int minor) {
			super(DialectResolver.getInstance().getDialect("MariaDB", major, minor, 7));
		}
		String query(ParametersContext context) {
			return getSqlSqlNode(null).eval(context).getSql();
		}
	}

	@Test
	void separatesTablesFromViewsAndSequencesAndRetainsFilters() {
		for (int[] version : new int[][] { {10, 2}, {10, 3}, {11, 4}, {11, 5}, {12, 1} }) {
			var reader = new Reader(version[0], version[1]);
			String all = reader.query(new ParametersContext());
			assertTrue(all.contains("t.TABLE_TYPE IN ('BASE TABLE', 'SYSTEM VERSIONED', 'TEMPORARY')"), all);
			assertFalse(all.contains("'SEQUENCE'"), all);
			assertFalse(all.contains("'VIEW'"), all);
			var context = new ParametersContext();
			context.put("schemaName", java.util.List.of("app", "audit"));
			context.put("tableName", java.util.List.of("orders", "history"));
			String filtered = reader.query(context);
			assertTrue(filtered.contains("t.table_schema IN"), filtered);
			assertTrue(filtered.contains("t.table_name IN"), filtered);
			assertFalse(all.contains("t.table_schema IN"), all);
			assertFalse(all.contains("t.table_name IN"), all);
		}
	}
}

/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.parameter.ParametersContext;

class OracleHiddenColumnSqlTest {
	@ParameterizedTest
	@ValueSource(ints = { 11, 12, 19, 23, 26 })
	void falseIsAnExplicitVisibleColumnSelection(int version) {
		var dialect = DialectResolver.getInstance().getDialect("Oracle", version, 0, null);
		var reader = (OracleColumnReader) dialect.getCatalogReader().getSchemaReader().getTableReader()
				.getColumnReader();
		var context = new ParametersContext();
		String defaults = reader.getSqlSqlNode(null).eval(context).getSql();
		assertTrue(defaults.contains("tc.HIDDEN_COLUMN='NO'"), defaults);
		context.put("containsHiddenColumns", false);
		String sql = reader.getSqlSqlNode(null).eval(context).getSql().replaceAll("\\s+", " ");
		assertTrue(sql.contains("tc.HIDDEN_COLUMN='NO'"), sql);
		assertFalse(sql.contains("tc.HIDDEN_COLUMN='YES'"), sql);
		if (version >= 12)
			assertTrue(sql.contains("tc.USER_GENERATED='YES'"), sql);
		context.put("containsHiddenColumns", true);
		String hidden = reader.getSqlSqlNode(null).eval(context).getSql().replaceAll("\\s+", " ");
		assertTrue(hidden.contains("tc.HIDDEN_COLUMN='YES'"), hidden);
		assertFalse(hidden.contains("tc.HIDDEN_COLUMN='NO'"), hidden);
	}
}

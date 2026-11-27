/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;

class FirebirdProcedureVersionMetadataTest {
	@Test
	void isolatesParameterColumnsIntroducedIn21FromOlderCatalogs() {
		var reader = new FirebirdProcedureArgumentReader(DialectHolder.defaultDialect20);
		for (int[] version : new int[][] { { 1, 5 }, { 2, 0 }, { 2, 1 }, { 2, 5 } }) {
			var product = new ProductVersionInfo().setMajorVersion(version[0]).setMinorVersion(version[1]);
			var sql = new com.sqlapp.jdbc.sql.SqlParameterCollection();
			reader.getSqlSqlNode(product).eval(new ParametersContext(), sql);
			String text = sql.getSql();
			boolean modern = version[0] == 2 && version[1] >= 1;
			assertEquals(modern, text.contains("pp.RDB$DEFAULT_SOURCE"), text);
			assertEquals(modern, text.contains("pp.RDB$NULL_FLAG"), text);
			assertEquals(modern, text.contains("pp.RDB$COLLATION_ID"), text);
			boolean references = version[0] == 2 && version[1] >= 5;
			assertEquals(references, text.contains("pp.RDB$FIELD_NAME"), text);
			assertEquals(references, text.contains("pp.RDB$RELATION_NAME"), text);
			assertEquals(references, text.contains("pp.RDB$PARAMETER_MECHANISM"), text);
			assertTrue(text.contains("f.RDB$DEFAULT_SOURCE"), text);
			assertTrue(text.contains("CHAR_LEN"), text);
		}
	}
}

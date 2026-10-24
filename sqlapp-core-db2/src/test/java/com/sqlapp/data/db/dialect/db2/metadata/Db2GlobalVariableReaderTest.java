/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2GlobalVariableReaderTest {
	@Test
	void twentyVariablesUseOneFilteredQueryWithNoRuntimeValues() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2GlobalVariableReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				queries.incrementAndGet();
				assertTrue(node.toString().contains("VARMODULEID IS NULL"));
				assertTrue(node.toString().contains("constantName"));
				assertFalse(node.toString().contains("COORDINATETYPE"));
				for (int i = 0; i < 20; i++) {
					try {
						handler.handleResultSetNext(Db2MetadataParityTest
								.row(Map.of("VARSCHEMA", "APP", "VARNAME", "V" + i, "TYPESCHEMA", "SYSIBM", "TYPENAME",
										"INTEGER", "SCOPE", "S", "READONLY", "N", "DEFAULT_EXPRESSION", "(1 + 2)")));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				}
				return null;
			}
		};
		var variables = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(20, variables.size());
		assertEquals(1, queries.get());
		assertEquals("VARIABLE", variables.getFirst().getSpecifics().get("DB2_OBJECT_TYPE"));
		assertEquals("(1 + 2)", variables.getFirst().getStatement().getFirst());
		assertTrue(variables.getFirst().getDefinition().isEmpty());
		assertNotNull(DialectHolder.Db2_1110Dialect.getCatalogReader().getSchemaReader().getConstantReader());
		assertNull(DialectHolder.Db2_1050Dialect.getCatalogReader().getSchemaReader().getConstantReader());
		assertThrows(IllegalArgumentException.class, () -> new Db2GlobalVariableReader(DialectHolder.Db2_1050Dialect)
				.doGetAll(null, new ParametersContext(), new ProductVersionInfo()));
	}
}

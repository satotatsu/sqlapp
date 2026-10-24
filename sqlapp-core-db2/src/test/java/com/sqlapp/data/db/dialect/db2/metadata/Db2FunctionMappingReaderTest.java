/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2FunctionMappingReaderTest {
	@Test
	void twentyMappingsWithParametersUseTwoBulkQueriesAndRetainOrder() {
		AtomicInteger count = new AtomicInteger();
		var reader = new Db2FunctionMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				int query = count.incrementAndGet();
				assertTrue(node.toString().contains("m.OWNER <> 'SYSIBM'"));
				if (query == 1)
					assertTrue(node.toString().contains("ORDER BY m.CREATE_TIME"));
				for (int i = 19; i >= 0; i--)
					try {
						Map<String, String> row = query == 1
								? Map.of("FUNCTION_MAPPING", "M" + i, "FUNCSCHEMA", "APP", "FUNCNAME", "F",
										"SPECIFICNAME", "F1", "SERVERNAME", "S", "OPTION_NAME", "REMOTE_NAME",
										"SETTING", "r.f")
								: Map.of("FUNCTION_MAPPING", "M" + i, "ORDINAL", "1", "LOCATION", "L", "OPTION_NAME",
										"CUSTOM", "SETTING", "x");
						handler.handleResultSetNext(Db2MetadataParityTest.row(row));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		var result = reader.read(null, new ParametersContext());
		assertEquals(2, count.get());
		assertEquals(20, result.mappings().size());
		assertEquals("M19", result.mappings().getFirst().name());
		assertEquals("r.f", result.mappings().getFirst().options().get("REMOTE_NAME"));
		assertEquals(1, result.mappings().getFirst().parameterOptions().getFirst().ordinal());
		assertNull(result.mappings().getFirst().infix());
		assertFalse(result.orderKnown()); // Missing catalog timestamps cannot establish precedence.
		assertNotNull(((Db2CatalogReader) DialectHolder.Db2_1215Dialect.getCatalogReader()).functionMappings());
	}

	@Test
	void functionTemplatesAndSourcedFunctionsAreNotLostForBlankLanguages() throws Exception {
		var reader = new Db2FunctionReader(DialectHolder.Db2_1110Dialect);
		var options = new com.sqlapp.data.db.metadata.ReaderOptions();
		options.setReadDefinition(true);
		reader.setReaderOptions(options);
		assertTrue(reader.getSqlNode(new com.sqlapp.data.schemas.ProductVersionInfo()).toString()
				.contains("r.ORIGIN IN ('M', 'U')"));
		String ddl = "CREATE FUNCTION APP.F(INT) RETURNS INT AS TEMPLATE";
		var function = reader.createFunction(Db2MetadataParityTest.row(Map.of("schema_name", "APP", "routine_name", "F",
				"specific_name", "F1", "LANGUAGE", " ", "FUNCTIONTYPE", "S", "ROUTINE_DEFINITION", ddl)));
		assertEquals(List.of(ddl), function.getDefinition());
	}

	@Test
	void legacyReadersRejectAndCatalogFailuresDoNotClearStoredMappings() {
		assertThrows(IllegalArgumentException.class,
				() -> new Db2FunctionMappingReader(DialectHolder.Db2_1050Dialect).read(null, new ParametersContext()));
		var catalog = new com.sqlapp.data.schemas.Catalog();
		catalog.getSpecifics().put("other", "retained");
		var reader = new Db2FunctionMappingReader(DialectHolder.Db2_1110Dialect) {
			@Override
			public Db2FunctionMappings.Snapshot read(Connection connection) {
				throw new IllegalStateException("catalog denied");
			}
		};
		assertThrows(IllegalStateException.class, () -> reader.loadFull(null, catalog));
		assertEquals("retained", catalog.getSpecifics().get("other"));
	}
}

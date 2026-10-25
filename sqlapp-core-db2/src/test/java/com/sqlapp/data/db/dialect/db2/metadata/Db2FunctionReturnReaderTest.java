/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2FunctionReturnReaderTest {
	@Test
	void scalarRetainsQualifiedTypeAndStringUnitsWithoutChangingLegacyQuery() throws Exception {
		var reader = new Db2FunctionReader(DialectHolder.Db2_1110Dialect);
		reader.setReaderOptions(new ReaderOptions());
		var fields = new HashMap<String, String>(Map.of("routine_name", "F", "schema_name", "APP", "specific_name",
				"specific.dot", "FUNCTIONTYPE", "S", "RETURN_TYPE_NAME", "VARCHAR"));
		fields.putAll(Map.of("RETURN_TYPESCHEMA", "TYPE.SCHEMA", "RETURN_TYPEMODULENAME", "M", "RETURN_LENGTH", "20",
				"RETURN_SCALE", "0", "RETURN_CODEPAGE", "1208", "RETURN_TYPESTRINGUNITS", "CODEUNITS32",
				"RETURN_STRINGUNITSLENGTH", "5", "RETURN_LOCATOR", "N"));
		var function = reader.createFunction(Db2MetadataParityTest.row(fields));
		assertEquals("TYPE.SCHEMA", function.getReturning().getSpecifics().get("TYPESCHEMA"));
		assertEquals("M", function.getReturning().getSpecifics().get("TYPEMODULENAME"));
		assertEquals("5", function.getReturning().getSpecifics().get("STRINGUNITSLENGTH"));
		assertEquals("VARCHAR", function.getReturning().getSpecifics().get("TYPENAME"));
		assertTrue(reader.getSqlNode(new ProductVersionInfo()).toString()
				.contains("p.TYPESTRINGUNITS AS RETURN_TYPESTRINGUNITS"));
		assertFalse(new Db2FunctionReader(DialectHolder.Db2_1050Dialect).getSqlNode(new ProductVersionInfo()).toString()
				.contains("p.TYPESTRINGUNITS"));
	}

	@Test
	void twentyOverloadsAndFortyReturnColumnsUseTwoQueriesWithoutParsingDdl() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2FunctionReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode sql, ParametersContext p,
					ResultSetNextHandler handler) {
				int query = queries.incrementAndGet();
				try {
					if (query == 1) {
						for (int i = 0; i < 20; i++)
							handler.handleResultSetNext(Db2MetadataParityTest.row(Map.of("routine_name", "F",
									"schema_name", i % 2 == 0 ? "APP" : "OTHER", "specific_name", "F." + i / 2,
									"FUNCTIONTYPE", "T", "ROUTINE_DEFINITION", "unparseable return signature")));
					} else {
						assertTrue(sql.toString().contains("p.ORDINAL>0"));
						assertTrue(sql.toString().contains("r.ROUTINEMODULEID IS NULL"));
						for (int i = 0; i < 20; i++)
							for (int j = 0; j < 2; j++) {
								var fields = new HashMap<String, String>(Map.of("schema_name",
										i % 2 == 0 ? "APP" : "OTHER", "specific_name", "F." + i / 2, "PARMNAME",
										"C" + j, "TYPENAME", "VARCHAR", "TYPESCHEMA", "SYSIBM", "LENGTH", "20"));
								fields.putAll(Map.of("CODEPAGE", "1208", "TYPESTRINGUNITS", "CODEUNITS32",
										"STRINGUNITSLENGTH", "5", "LOCATOR", "N"));
								handler.handleResultSetNext(Db2MetadataParityTest.row(fields));
							}
					}
				} catch (SQLException e) {
					throw new IllegalStateException(e);
				}
				return null;
			}
		};
		reader.setReaderOptions(new ReaderOptions());
		var functions = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(2, queries.get());
		assertEquals(20, functions.size());
		for (int i = 0; i < 20; i++) {
			assertEquals("F." + i / 2, functions.get(i).getSpecificName());
			assertEquals(i % 2 == 0 ? "APP" : "OTHER", functions.get(i).getSchemaName());
			var columns = functions.get(i).getReturning().getTable().getColumns();
			assertEquals(2, columns.size());
			assertEquals("C0", columns.get(0).getName());
			assertEquals("C1", columns.get(1).getName());
			assertEquals("5", columns.get(0).getSpecifics().get("STRINGUNITSLENGTH"));
		}
	}
}

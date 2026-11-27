/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.db.dialect.oracle.sql.OracleJsonDualityViewUtils;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.View;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Oracle26aiDualityDetailsTest {
	private ExResultSet row(Map<String, Object> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String column) {
				Object value = values.get(column);
				return value == null ? null : value.toString();
			}

			@Override
			public Object getObject(String column) {
				return values.get(column);
			}

			@Override
			public boolean getBoolean(String column) {
				return Boolean.TRUE.equals(values.get(column));
			}
		};
	}

	@Test
	void hierarchyAndColumnFlagsSurviveThreeBulkQueriesAndXml() throws Exception {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Oracle26aiViewReader(DialectHolder.oracle26aiDialect) {
			@Override
			public ColumnReader getColumnReader() {
				return null;
			}

			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p, ResultSetNextHandler h) {
				int query = queries.incrementAndGet();
				try {
					for (String owner : List.of("App", "OTHER", "UNREQUESTED")) {
						if (query == 1) {
							h.handleResultSetNext(row(
									Map.of("VIEW_OWNER", owner, "VIEW_NAME", "Dv", "LOGICAL_REPLICATION", "DISABLED")));
						} else if (query == 2) {
							h.handleResultSetNext(row(Map.of("VIEW_OWNER", owner, "VIEW_NAME", "Dv", "TABLE_OWNER",
									owner, "TABLE_NAME", "PARENT", "TABLE_NUMBER", 1, "ROOT_TABLE", true)));
							h.handleResultSetNext(row(Map.of("VIEW_OWNER", owner, "VIEW_NAME", "Dv", "TABLE_OWNER",
									owner, "TABLE_NAME", "CHILD", "TABLE_NUMBER", 2, "PARENT_TABLE_NUMBER", 1,
									"RELATIONSHIP", "nested")));
						} else {
							h.handleResultSetNext(row(Map.of("VIEW_OWNER", owner, "VIEW_NAME", "Dv", "TABLE_OWNER",
									owner, "TABLE_NAME", "CHILD", "TABLE_NUMBER", 2, "COLUMN_NAME", "AMOUNT",
									"JSON_KEY_NAME", "total", "IS_HIDDEN", true, "IS_GENERATED", true, "ETAG_POS", 1)));
						}
					}
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
				return null;
			}
		};
		View first = new View("Dv");
		first.setSchemaName("App");
		View second = new View("Dv");
		second.setSchemaName("OTHER");
		reader.setMetadataDetail(null, new ParametersContext(), List.<Table>of(first, second));
		assertEquals(3, queries.get());
		for (View view : List.of(first, second)) {
			View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			var tables = OracleJsonDualityViewUtils.getTableDetails(restored);
			assertEquals(2, tables.size());
			assertEquals(view.getSchemaName(), tables.get(0).get("TABLE_OWNER"));
			assertEquals(1, tables.get(1).get("PARENT_TABLE_NUMBER"));
			assertEquals("nested", tables.get(1).get("RELATIONSHIP"));
			var columns = OracleJsonDualityViewUtils.getColumnDetails(restored);
			assertEquals(1, columns.size());
			assertEquals(true, columns.get(0).get("IS_HIDDEN"));
			assertEquals(true, columns.get(0).get("IS_GENERATED"));
			assertEquals("total", columns.get(0).get("JSON_KEY_NAME"));
		}
	}

	@Test
	void optionalCatalogErrorsAreDistinguishedFromBrokenSql() {
		View view = new View("DV");
		view.setSchemaName("APP");
		OracleJsonDualityViewUtils.setJsonRelationalDualityView(view, true);
		for (int code : List.of(942, 904)) {
			var reader = new Oracle26aiViewReader(DialectHolder.oracle26aiDialect) {
				@Override
				protected JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p,
						ResultSetNextHandler h) {
					throw new RuntimeException(new SQLException("catalog error", "42000", code));
				}
			};
			Runnable read = () -> reader.loadDualityDetails(null, List.of(view), "jsonDualityTables26ai.sql",
					OracleJsonDualityViewUtils.TABLE_DETAILS, List.of("TABLE_NAME"));
			if (code == 942)
				assertDoesNotThrow(read::run);
			else
				assertThrows(RuntimeException.class, read::run);
		}
	}
}

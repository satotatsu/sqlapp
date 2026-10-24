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

class Db2ExpressionIndexReaderTest {
	@Test
	void twentyIndexesRetainExpressionsRandomOrderAndOptionsInOneQuery() {
		AtomicInteger queries = new AtomicInteger();
		var reader = new Db2IndexReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context,
					ResultSetNextHandler handler) {
				assertTrue(node.toString().contains("ic.TEXT AS key_expression"));
				queries.incrementAndGet();
				for (int i = 0; i < 20; i++)
					for (String order : new String[] { "D", "R" }) {
						var row = new java.util.HashMap<>(Map.of("schema_name", "IDX", "table_schema", "APP",
								"table_name", "T" + i, "index_name", "IX" + i, "UNIQUERULE", "D", "INDEXTYPE", "REG",
								"column_name", order.equals("D") ? "SYS_EXPR" : "ID", "COLORDER", order));
						row.putAll(Map.of("COMPRESSION", "Y", "REVERSE_SCANS", "N", "PCTFREE", "10", "MINPCTUSED", "20",
								"NULLKEYS", "N", "FUNC_PATH", "\"SYSIBM\",\"APP\""));
						if (order.equals("D")) {
							row.put("key_expression", " UPPER(\"NAME\") ");
							row.put("key_virtual", "Y");
						}
						try {
							handler.handleResultSetNext(Db2MetadataParityTest.row(row));
						} catch (SQLException e) {
							throw new IllegalStateException(e);
						}
					}
				return null;
			}
		};
		var indexes = reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo());
		assertEquals(1, queries.get());
		assertEquals(20, indexes.size());
		assertEquals(" UPPER(\"NAME\") ", indexes.getFirst().getColumns().getFirst().getStatement().getFirst());
		assertEquals("R", indexes.getFirst().getColumns().get(1).getSpecifics().get("DB2_COLORDER"));
		assertEquals("N", indexes.getFirst().getSpecifics().get("NULLKEYS"));
		assertFalse(new Db2IndexReader(DialectHolder.Db2_1050Dialect).getSqlNode(new ProductVersionInfo()).toString()
				.contains("key_expression"));
	}
}

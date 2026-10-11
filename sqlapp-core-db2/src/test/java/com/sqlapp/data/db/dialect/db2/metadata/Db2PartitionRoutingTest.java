/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.data.schemas.SchemaUtils;

class Db2PartitionRoutingTest {
	@Test
	void skipsSchemaTableCrossProductRowsAndEmptySelections() throws Exception {
		var count = new AtomicInteger();
		var reader = new Db2TableReader(DialectHolder.defaultDialect) {
			@Override protected ParametersContext defaultParametersContext(Connection connection) { return new ParametersContext(); }
			@Override protected JdbcQueryHandler execute(Connection connection, SqlNode node, ParametersContext context, ResultSetNextHandler handler) {
				count.incrementAndGet();
				try {
					handler.handleResultSetNext(new ExResultSet(null) {
						@Override public String getString(String name) { return name.equals("schema_name") ? "A" : "T2"; }
					});
				} catch (SQLException e) { throw new RuntimeException(e); }
				return null;
			}
		};
		var tables = SchemaUtils.toDoubleKeyMap(List.of(new Table("T1").setSchemaName("A"), new Table("T2").setSchemaName("B")));
		reader.setPartitionExpression(null, tables);
		reader.setPartitioning(null, tables);
		assertEquals(2, count.get());
		reader.setMetadataDetail(null, new ParametersContext(), List.of());
		assertEquals(2, count.get());
	}
}

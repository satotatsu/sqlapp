/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class InformixCheckConstraintReaderTest {
	private static class Reader extends InformixCheckConstraintReader {
		final List<Map<String, Object>> rows = new ArrayList<>();
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected com.sqlapp.jdbc.sql.JdbcQueryHandler execute(Connection c, SqlNode n, ParametersContext p,
				ResultSetNextHandler handler) {
			queries++;
			try {
				for (var row : rows)
					handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
			return null;
		}

		void add(int id, String owner, String text) {
			final Map<String, Object> row = new HashMap<>();
			row.put("constraint_id", id);
			row.put("catalog_name", "db");
			row.put("schema_name", owner);
			row.put("table_name", "items");
			row.put("constraint_name", "same_check");
			row.put("constraint_owner", "check_owner");
			row.put("definition", text);
			rows.add(row);
		}
	}

	@Test
	void preservesChunkWhitespaceAndOwnerIdentityInOneQueryThroughXml() throws Exception {
		final Reader reader = new Reader();
		final String expression = "value <> '" + "a".repeat(21) + " b''s trailing '";
		for (int id = 1; id <= 20; id++) {
			for (int offset = 0; offset < expression.length(); offset += 32)
				reader.add(id, "owner" + id, expression.substring(offset, Math.min(offset + 32, expression.length())));
		}
		final var constraints = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertEquals(20, constraints.size());
		for (var constraint : constraints) {
			assertEquals(expression, constraint.getExpression());
			assertEquals("check_owner",
					constraint.getSpecifics().get(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER));
			final var table = new Table("items").setSchemaName(constraint.getSchemaName());
			table.getColumns().add("value",
					c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR).setLength(100));
			table.getConstraints().add(constraint);
			final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
			assertTrue(DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE)
					.get(0).getSqlText().contains(expression));
		}
	}

	@Test
	void rejectsMissingOrBlankExpressions() {
		for (String expression : new String[] { null, "   " }) {
			final Reader reader = new Reader();
			reader.add(1, "owner", expression);
			assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
			final var table = new Table("items");
			table.getColumns().add("value", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
			table.getConstraints().add(new CheckConstraint("ck", expression));
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		}
	}
}

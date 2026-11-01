/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

class PhoenixPrimaryKeyReaderTest {
	private Map<String, Object> row(String tenant, String table, String name, int sequence, String direction) {
		final var values = new HashMap<String, Object>();
		values.put("TABLE_CAT", tenant);
		values.put("TABLE_SCHEM", "app");
		values.put("TABLE_NAME", table);
		values.put("PK_NAME", "pk");
		values.put("COLUMN_NAME", name);
		values.put("KEY_SEQ", sequence);
		values.put("ASC_OR_DESC", direction);
		return values;
	}

	@Test
	void preservesCompositeKeySequenceAndDirectionInOneReadAndXml() throws Exception {
		final var rows = new ArrayList<Map<String, Object>>();
		for (String tenant : List.of("a", "b")) {
			for (int i = 0; i < 20; i++) {
				rows.add(row(tenant, "table" + i, "a_later", 2, "D"));
				rows.add(row(tenant, "table" + i, "z_first", 1, "A"));
			}
		}
		final var jdbc = new PhoenixColumnReaderTest.Jdbc(rows, "getPrimaryKeys");
		final var keys = new PhoenixPrimaryKeyReader(DialectHolder.defaultDialect).doGetAll(jdbc.connection,
				new ParametersContext(), null);
		assertEquals(1, jdbc.queries);
		assertTrue(jdbc.closed);
		assertEquals(40, keys.size());
		for (var key : keys) {
			final var table = new Table(key.getTableName()).setCatalogName(key.getCatalogName())
					.setSchemaName(key.getSchemaName());
			table.getColumns().add(new Column("z_first").setDataType(DataType.INT));
			table.getColumns().add(new Column("a_later").setDataType(DataType.INT));
			table.getConstraints().add(key);
			final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			final var restoredKey = restored.getConstraints().getPrimaryKeyConstraint();
			assertEquals("z_first", restoredKey.getColumns().get(0).getName());
			assertEquals(Order.Desc, restoredKey.getColumns().get(1).getOrder());
			final String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry()
					.createSql(restored, SqlType.CREATE).get(0).getSqlText();
			assertTrue(sql.contains("DESC"), sql);
		}
	}

	@Test
	void toleratesMissingOptionalDirectionAndRejectsInvalidSequences() {
		final var reader = new PhoenixPrimaryKeyReader(DialectHolder.defaultDialect);
		final var plain = row("a", "t", "id", 1, null);
		plain.remove("ASC_OR_DESC");
		final var jdbc = new PhoenixColumnReaderTest.Jdbc(List.of(plain), "getPrimaryKeys");
		assertEquals(1, reader.doGetAll(jdbc.connection, new ParametersContext(), null).size());
		for (var rows : List.of(List.of(row("a", "t", "id", 0, "A")), List.of(row("a", "t", "id", 2, "A")),
				List.of(row("a", "t", "id", 1, "invalid")),
				List.of(row("a", "t", "id", 1, "A"), row("a", "t", "other", 1, "D")))) {
			final var invalid = new PhoenixColumnReaderTest.Jdbc(rows, "getPrimaryKeys");
			assertThrows(IllegalStateException.class,
					() -> reader.doGetAll(invalid.connection, new ParametersContext(), null));
			assertTrue(invalid.closed);
		}
	}

	@Test
	void isWiredIntoBothTableReaders() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1))
			assertInstanceOf(PhoenixPrimaryKeyReader.class,
					dialect.getCatalogReader().getSchemaReader().getTableReader().getUniqueConstraintReader());
	}
}

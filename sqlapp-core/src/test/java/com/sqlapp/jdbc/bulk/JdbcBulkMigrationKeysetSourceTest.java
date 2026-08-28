/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;

class JdbcBulkMigrationKeysetSourceTest extends AbstractDbTest {
	@Test
	void readsAndResumesACompositeKeyThroughSelectByRowFactory() throws Exception {
		testDb(connection -> {
			execute(connection, "CREATE TABLE SQLAPP_KEYSET_FACTORY_TEST ("
					+ "KEY1 INTEGER NOT NULL, KEY2 INTEGER NOT NULL, VALUE VARCHAR(32), "
					+ "PRIMARY KEY (KEY1, KEY2))",
					"INSERT INTO SQLAPP_KEYSET_FACTORY_TEST VALUES "
							+ "(2, 1, 'third'), (1, 2, 'second'), (1, 1, 'first')");
			final Table table = table();
			final var source = new JdbcBulkMigrationKeysetSource(connection, table);

			assertEquals(List.of("first", "second", "third"), values(source.iterator(null)));

			final Row checkpointRow = table.newRow();
			checkpointRow.put("KEY1", 1);
			checkpointRow.put("KEY2", 1);
			assertEquals(List.of("second", "third"),
					values(source.iterator(source.resumeToken(checkpointRow))));
		});
	}

	@Test
	void acceptsOnlyCompleteNonNullUniqueKeys() throws Exception {
		testDb(connection -> {
			final Table table = new Table("KEYSET_VALIDATION");
			final Column key1 = new Column("KEY1").setDataType(DataType.INT).setNotNull(true);
			final Column key2 = new Column("KEY2").setDataType(DataType.INT).setNotNull(true);
			final Column value = new Column("VALUE").setDataType(DataType.VARCHAR);
			table.getColumns().add(key1);
			table.getColumns().add(key2);
			table.getColumns().add(value);
			table.getConstraints().addUniqueConstraint("UK_KEYSET", key1, key2);

			new JdbcBulkMigrationKeysetSource(connection, table, List.of("KEY1", "KEY2"));
			assertThrows(IllegalArgumentException.class,
					() -> new JdbcBulkMigrationKeysetSource(connection, table, List.of("KEY1")));
			assertThrows(IllegalArgumentException.class,
					() -> new JdbcBulkMigrationKeysetSource(connection, table, List.of("VALUE")));

			final Table indexed = new Table("KEYSET_INDEX_VALIDATION");
			final Column indexKey1 = new Column("KEY1").setDataType(DataType.INT)
					.setNotNull(true);
			final Column indexKey2 = new Column("KEY2").setDataType(DataType.INT)
					.setNotNull(true);
			indexed.getColumns().add(indexKey1);
			indexed.getColumns().add(indexKey2);
			indexed.getIndexes().add("UIX_KEYSET", indexKey1, indexKey2).setUnique(true);
			new JdbcBulkMigrationKeysetSource(connection, indexed, List.of("KEY2", "KEY1"));

			final Table caseInsensitive = new Table("KEYSET_CASE_VALIDATION")
					.setCaseSensitive(false);
			final Column caseKey = new Column("KEY_ID").setDataType(DataType.INT)
					.setNotNull(true);
			caseInsensitive.getColumns().add(caseKey);
			caseInsensitive.getConstraints().addUniqueConstraint("UK_CASE_KEY",
					new ReferenceColumn("key_id"));
			new JdbcBulkMigrationKeysetSource(connection, caseInsensitive, List.of("KEY_ID"));
		});
	}

	private static List<String> values(final java.util.Iterator<Row> rows) {
		final List<String> values = new ArrayList<>();
		while (rows.hasNext()) {
			values.add((String) rows.next().get("VALUE"));
		}
		return values;
	}

	private static Table table() {
		final Table table = new Table("SQLAPP_KEYSET_FACTORY_TEST");
		final Column key1 = new Column("KEY1").setDataType(DataType.INT).setNotNull(true);
		final Column key2 = new Column("KEY2").setDataType(DataType.INT).setNotNull(true);
		table.getColumns().add(key1);
		table.getColumns().add(key2);
		table.getColumns().add(new Column("VALUE").setDataType(DataType.VARCHAR).setLength(32));
		table.setPrimaryKey((String) null, key1, key2);
		return table;
	}
}

package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresJsonTableBuilderTest {
	@Test
	void testJsonTableWithNestedColumns() {
		String sql = new PostgresJsonTableBuilder(DialectHolder.postgreSQL170)
				.contextItem("payload")
				.path("$.orders[*]")
				.pathName("orders_path")
				.ordinalityColumn("row_no")
				.column("order_id", "BIGINT", "$.id")
				.existsColumn("has_items", "BOOLEAN", "$.items")
				.nested("$.items[*]", nested -> nested
						.column("product_id", "BIGINT", "$.productId")
						.column("quantity", "INTEGER", "$.quantity"))
				.errorOnError(true)
				.build();
		assertEquals("""
				JSON_TABLE(payload, '$.orders[*]' AS orders_path COLUMNS (row_no FOR ORDINALITY, order_id BIGINT PATH '$.id', has_items BOOLEAN EXISTS PATH '$.items', NESTED PATH '$.items[*]' COLUMNS (product_id BIGINT PATH '$.productId', quantity INTEGER PATH '$.quantity')) ERROR ON ERROR)""",
				sql);
	}

	@Test
	void testRejectBeforePostgres17() {
		PostgresJsonTableBuilder builder = new PostgresJsonTableBuilder(DialectHolder.postgreSQL160)
				.contextItem("payload").path("$[*]").column("id", "INTEGER", "$.id");
		assertThrows(IllegalArgumentException.class, builder::build);
	}

	@Test
	void testRejectMissingColumns() {
		PostgresJsonTableBuilder builder = new PostgresJsonTableBuilder(DialectHolder.postgreSQL170)
				.contextItem("payload").path("$[*]");
		assertThrows(IllegalArgumentException.class, builder::build);
	}
}

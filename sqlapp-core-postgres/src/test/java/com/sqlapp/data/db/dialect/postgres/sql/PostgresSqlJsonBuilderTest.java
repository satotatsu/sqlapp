package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;

class PostgresSqlJsonBuilderTest {
	private final PostgresSqlJsonBuilder builder =
			new PostgresSqlJsonBuilder(DialectHolder.postgreSQL170);

	@Test
	void testJsonValueWithPassingAndBehaviors() {
		assertEquals(
				"JSON_VALUE(payload, 'strict $.orders[$index].date' PASSING order_index AS index RETURNING DATE DEFAULT CURRENT_DATE ON EMPTY ERROR ON ERROR)",
				builder.jsonValue("payload", "strict $.orders[$index].date")
						.passing("order_index", "index")
						.returning("DATE")
						.onEmpty("DEFAULT CURRENT_DATE")
						.onError("ERROR")
						.build());
	}

	@Test
	void testJsonQuery() {
		assertEquals(
				"JSON_QUERY(payload, '$.items[*]' RETURNING JSONB WITH CONDITIONAL WRAPPER OMIT QUOTES ON SCALAR STRING EMPTY ARRAY ON EMPTY NULL ON ERROR)",
				builder.jsonQuery("payload", "$.items[*]")
						.returning("JSONB")
						.wrapper("WITH CONDITIONAL WRAPPER")
						.quotes("OMIT QUOTES ON SCALAR STRING")
						.onEmpty("EMPTY ARRAY")
						.onError("NULL")
						.build());
	}

	@Test
	void testJsonExistsAndSerialize() {
		assertEquals("JSON_EXISTS(payload, '$.active' TRUE ON ERROR)",
				builder.jsonExists("payload", "$.active").onError("TRUE").build());
		assertEquals("JSON_SERIALIZE(payload RETURNING TEXT)",
				builder.jsonSerialize("payload", "TEXT"));
	}

	@Test
	void testRejectInvalidOptionsAndOldVersion() {
		assertThrows(IllegalArgumentException.class,
				() -> builder.jsonExists("payload", "$.id").returning("BOOLEAN").build());
		PostgresSqlJsonBuilder postgres16 =
				new PostgresSqlJsonBuilder(DialectHolder.postgreSQL160);
		assertThrows(IllegalArgumentException.class,
				() -> postgres16.jsonValue("payload", "$.id").build());
	}
}

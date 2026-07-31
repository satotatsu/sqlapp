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

	@Test
	void testJsonConstructors() {
		assertEquals("JSON(payload FORMAT JSON WITH UNIQUE KEYS)",
				builder.json("payload", true, true));
		assertEquals("JSON_SCALAR(CURRENT_TIMESTAMP)",
				builder.jsonScalar("CURRENT_TIMESTAMP"));
		assertEquals(
				"JSON_OBJECT('id' VALUE id, 'payload' VALUE payload FORMAT JSON ABSENT ON NULL WITH UNIQUE KEYS RETURNING JSONB)",
				builder.jsonObject()
						.entry("'id'", "id")
						.entry("'payload'", "payload", true)
						.absentOnNull(true)
						.uniqueKeys(true)
						.returning("JSONB")
						.build());
		assertEquals(
				"JSON_ARRAY(id, payload FORMAT JSON ABSENT ON NULL RETURNING JSONB)",
				builder.jsonArray()
						.value("id")
						.value("payload", true)
						.absentOnNull(true)
						.returning("JSONB")
						.build());
		assertEquals("JSON_ARRAY(SELECT id FROM orders RETURNING JSONB)",
				builder.jsonArray().query("SELECT id FROM orders")
						.returning("JSONB").build());
	}

	@Test
	void testRejectMixedJsonArraySources() {
		assertThrows(IllegalArgumentException.class,
				() -> builder.jsonArray().value("id").query("SELECT id FROM orders"));
	}

	@Test
	void testJsonAggregates() {
		assertEquals(
				"JSON_ARRAYAGG(payload ORDER BY created_at DESC ABSENT ON NULL RETURNING JSONB)",
				builder.jsonArrayAgg("payload")
						.orderBy("created_at DESC")
						.absentOnNull(true)
						.returning("JSONB")
						.build());
		assertEquals(
				"JSON_OBJECTAGG(code VALUE payload ORDER BY code ABSENT ON NULL WITH UNIQUE KEYS RETURNING JSONB)",
				builder.jsonObjectAgg("code", "payload")
						.orderBy("code")
						.absentOnNull(true)
						.uniqueKeys(true)
						.returning("JSONB")
						.build());
	}

	@Test
	void testIsJsonPredicate() {
		assertEquals("payload IS JSON OBJECT WITH UNIQUE KEYS",
				builder.isJson("payload").type("OBJECT").uniqueKeys(true).build());
		assertEquals("payload IS NOT JSON ARRAY WITHOUT UNIQUE KEYS",
				builder.isJson("payload").not(true).type("ARRAY")
						.uniqueKeys(false).build());
	}

	@Test
	void testPostgres16ConstructorBoundary() {
		PostgresSqlJsonBuilder postgres16 =
				new PostgresSqlJsonBuilder(DialectHolder.postgreSQL160);
		assertEquals("JSON_ARRAY(id)", postgres16.jsonArray().value("id").build());
		assertEquals("JSON_ARRAYAGG(id)", postgres16.jsonArrayAgg("id").build());
		assertEquals("payload IS JSON", postgres16.isJson("payload").build());

		PostgresSqlJsonBuilder postgres15 =
				new PostgresSqlJsonBuilder(DialectHolder.postgreSQL150);
		assertThrows(IllegalArgumentException.class,
				() -> postgres15.jsonArray().value("id").build());
		assertThrows(IllegalArgumentException.class,
				() -> postgres15.jsonArrayAgg("id").build());
		assertThrows(IllegalArgumentException.class,
				() -> postgres15.isJson("payload").build());
	}
}

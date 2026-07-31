package com.sqlapp.data.db.dialect.postgres.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.db.dialect.postgres.sql.PostgresSubscriptionBuilder.Streaming;

class PostgresSubscriptionBuilderTest {

	@Test
	void testCreateSubscription() {
		String sql = new PostgresSubscriptionBuilder(
				DialectHolder.postgreSQL180, "sales_sub")
				.connection("host=publisher dbname=sales user=replicator")
				.publication("sales_pub")
				.publication("product_pub")
				.streaming(Streaming.PARALLEL)
				.twoPhase(true)
				.copyData(false)
				.buildCreate();

		assertEquals(
				"CREATE SUBSCRIPTION sales_sub CONNECTION 'host=publisher dbname=sales user=replicator' PUBLICATION sales_pub, product_pub WITH (streaming = parallel, two_phase = true, copy_data = false)",
				sql);
	}

	@Test
	void testPostgres18DefaultStreamingIsParallel() {
		assertEquals(Streaming.PARALLEL,
				new PostgresSubscriptionBuilder(
						DialectHolder.postgreSQL180, "sub")
						.getEffectiveDefaultStreaming());
		assertEquals(Streaming.OFF,
				new PostgresSubscriptionBuilder(
						DialectHolder.postgreSQL170, "sub")
						.getEffectiveDefaultStreaming());
	}

	@Test
	void testAlterTwoPhaseOnPostgres18() {
		assertEquals(
				"ALTER SUBSCRIPTION sales_sub SET (two_phase = false)",
				new PostgresSubscriptionBuilder(
						DialectHolder.postgreSQL180, "sales_sub")
						.alterTwoPhase(false));
	}

	@Test
	void testVersionBoundaries() {
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresSubscriptionBuilder(
						DialectHolder.postgreSQL170, "sub")
						.alterTwoPhase(true));
		assertThrows(IllegalArgumentException.class,
				() -> new PostgresSubscriptionBuilder(
						DialectHolder.postgreSQL150, "sub")
						.connection("host=publisher")
						.publication("pub")
						.streaming(Streaming.PARALLEL)
						.buildCreate());
	}

	@Test
	void testRequireConnectionAndPublication() {
		PostgresSubscriptionBuilder builder =
				new PostgresSubscriptionBuilder(
						DialectHolder.postgreSQL180, "sub");

		assertThrows(IllegalArgumentException.class, builder::buildCreate);
		assertThrows(IllegalArgumentException.class,
				() -> builder.connection("host=publisher").buildCreate());
	}
}

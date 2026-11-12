/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.spanner.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Sequence;

class SpannerAlterSequenceFactoryTest {
	@Test
	void restartsOnlyFromExplicitCounterRequestsInTheOriginalStyle() throws Exception {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (String style : List.of("CLAUSES", "OPTIONS")) {
			final var original = new Sequence("OrderSeq").setSchemaName("Sales");
			original.getSpecifics().put(SpannerAlterSequenceFactory.SEQUENCE_DDL_STYLE, style);
			final var target = original.clone();
			target.getSpecifics().put(SpannerAlterSequenceFactory.RESTART_COUNTER_WITH, "12345");
			final Sequence restored = SchemaUtils.readXml(new StringReader(target.asXml()));
			assertEquals("ALTER SEQUENCE `Sales`.`OrderSeq` " + ("CLAUSES".equals(style)
					? "RESTART COUNTER WITH 12345" : "SET OPTIONS (start_with_counter = 12345)"),
					registry.createSql(original.diff(restored), SqlType.ALTER).getFirst().getSqlText());
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(restored.diff(original), SqlType.ALTER));
			for (String invalid : List.of("0", "-1", "9223372036854775808", "not-a-counter")) {
				target.getSpecifics().put(SpannerAlterSequenceFactory.RESTART_COUNTER_WITH, invalid);
				assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(original.diff(target), SqlType.ALTER));
			}
		}
	}

	@Test
	void addsAndClearsRangesInTheOriginalStyleThroughXml() throws Exception {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		for (final String style : List.of("CLAUSES", "OPTIONS")) {
			final var original = new Sequence("OrderSeq").setSchemaName("Sales");
			final var target = original.clone();
			target.getSpecifics().put(SpannerAlterSequenceFactory.SEQUENCE_DDL_STYLE, style);
			target.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MIN, 10L);
			target.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MAX, 20L);
			final Sequence restored = SchemaUtils.readXml(new StringReader(target.asXml()));
			final var add = registry.createSql(original.diff(restored));
			assertEquals(1, add.size());
			final String prefix = "ALTER SEQUENCE `Sales`.`OrderSeq` ";
			assertEquals(prefix + ("CLAUSES".equals(style) ? "SKIP RANGE 10, 20"
					: "SET OPTIONS (skip_range_min = 10, skip_range_max = 20)"), add.getFirst().getSqlText());
			assertEquals(prefix + ("CLAUSES".equals(style) ? "NO SKIP RANGE"
					: "SET OPTIONS (skip_range_min = NULL, skip_range_max = NULL)"),
					registry.createSql(restored.diff(original), SqlType.ALTER).getFirst().getSqlText());
		}
	}

	@Test
	void rejectsAmbiguousOrInvalidRangesAndCounterChanges() {
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		final var original = new Sequence("seq");
		final var target = original.clone();
		target.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MIN, 20L);
		target.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MAX, 30L);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		target.getSpecifics().put(SpannerAlterSequenceFactory.SEQUENCE_DDL_STYLE, "CLAUSES");
		target.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MAX, 10L);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		target.getSpecifics().remove(SpannerCreateSequenceFactory.SKIP_RANGE_MAX);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(original.diff(target), SqlType.ALTER));
		final var changedCounter = original.clone().setStartValue(java.math.BigInteger.valueOf(100L));
		assertThrows(UnsupportedOperationException.class,
				() -> registry.createSql(original.diff(changedCounter), SqlType.ALTER));
		assertTrue(registry.createSql(original.diff(original), SqlType.ALTER).isEmpty());
	}
}

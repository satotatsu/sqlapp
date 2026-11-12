/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class SpannerCreateSequenceFactoryTest extends SpannerSqlFactoryTest {
	@Test
	void dropsNamedSequencesWithOptionalIfExists() {
		final var sequence = new Sequence("OrdersSeq").setSchemaName("Sales");
		sqlFactoryRegistry.getOptions().setDropIfExists(true);
		assertEquals("DROP SEQUENCE IF EXISTS `Sales`.`OrdersSeq`",
				sqlFactoryRegistry.createSql(sequence, SqlType.DROP).getFirst().getSqlText());
		sqlFactoryRegistry.getOptions().setDropIfExists(false);
		assertEquals("DROP SEQUENCE `Sales`.`OrdersSeq`",
				sqlFactoryRegistry.createSql(sequence, SqlType.DROP).getFirst().getSqlText());
		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(new Sequence(" "), SqlType.DROP));
	}
	@Test
	void validatesStartCounterInt64Boundary() {
		final var maximum = BigInteger.valueOf(Long.MAX_VALUE);
		final var sequence = new Sequence("sequence_boundary").setStartValue(maximum);
		assertTrue(sqlFactoryRegistry.createSql(sequence, SqlType.CREATE).getFirst().getSqlText()
				.contains("START COUNTER WITH " + maximum));
		sequence.setStartValue(maximum.add(BigInteger.ONE));
		assertThrows(IllegalArgumentException.class, () -> sqlFactoryRegistry.createSql(sequence, SqlType.CREATE));
	}

	@Test
	void testCreateBitReversedSequence() {
		final Sequence sequence = new Sequence("SINGER_ID_SEQUENCE").setStartValue(1000);
		sequence.setDialect(dialect);
		sequence.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MIN, 100L);
		sequence.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MAX, 199L);

		final String sql = sqlFactoryRegistry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+",
				" ");
		assertTrue(sql.contains("CREATE SEQUENCE IF NOT EXISTS SINGER_ID_SEQUENCE"), sql);
		assertTrue(sql.contains("START COUNTER WITH 1000"), sql);
		assertTrue(sql.contains("BIT_REVERSED_POSITIVE"), sql);
		assertTrue(sql.contains("SKIP RANGE 100"), sql);
		assertTrue(sql.contains("199"), sql);
	}

	@Test
	void testIgnoreUnsupportedGenericOptions() {
		final Sequence sequence = new Sequence("SINGER_ID_SEQUENCE").setIncrementBy(1).setMinValue(1).setMaxValue(10000)
				.setCacheSize(20).setCycle(true);
		sequence.setDialect(dialect);

		final String sql = sqlFactoryRegistry.createSql(sequence, SqlType.CREATE).get(0).getSqlText().replaceAll("\\s+",
				" ");
		assertTrue(sql.contains("BIT_REVERSED_POSITIVE"), sql);
		assertTrue(!sql.contains("INCREMENT"), sql);
		assertTrue(!sql.contains("MINVALUE"), sql);
		assertTrue(!sql.contains("MAXVALUE"), sql);
		assertTrue(!sql.contains("CACHE"), sql);
		assertTrue(!sql.contains("CYCLE"), sql);
	}

	@Test
	void testRejectIncompleteSkipRange() {
		final Sequence sequence = new Sequence("SINGER_ID_SEQUENCE");
		sequence.setDialect(dialect);
		sequence.getSpecifics().put(SpannerCreateSequenceFactory.SKIP_RANGE_MIN, 100L);

		assertThrows(IllegalArgumentException.class, () -> sqlFactoryRegistry.createSql(sequence, SqlType.CREATE));
	}

	@Test
	void testNextValues() {
		final Sequence sequence = new Sequence("SINGER_ID_SEQUENCE");
		sequence.setDialect(dialect);

		final String sql = sqlFactoryRegistry.createSql(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(sql.contains("GET_NEXT_SEQUENCE_VALUE(SEQUENCE SINGER_ID_SEQUENCE)"), sql);
		assertTrue(sql.contains("UNNEST(GENERATE_ARRAY("), sql);
	}
}

/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

class SpannerCreateSequenceFactoryTest extends SpannerSqlFactoryTest {

	@Test
	void testCreateBitReversedSequence() {
		final Sequence sequence = new Sequence("SINGER_ID_SEQUENCE")
				.setStartValue(1000);
		sequence.setDialect(dialect);
		sequence.getSpecifics().put(
				SpannerCreateSequenceFactory.SKIP_RANGE_MIN, 100L);
		sequence.getSpecifics().put(
				SpannerCreateSequenceFactory.SKIP_RANGE_MAX, 199L);

		final String sql = sqlFactoryRegistry.createSql(sequence,
				SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"CREATE SEQUENCE IF NOT EXISTS SINGER_ID_SEQUENCE"), sql);
		assertTrue(sql.contains("START COUNTER WITH 1000"), sql);
		assertTrue(sql.contains("BIT_REVERSED_POSITIVE"), sql);
		assertTrue(sql.contains("SKIP RANGE 100"), sql);
		assertTrue(sql.contains("199"), sql);
	}

	@Test
	void testIgnoreUnsupportedGenericOptions() {
		final Sequence sequence = new Sequence("SINGER_ID_SEQUENCE")
				.setIncrementBy(1)
				.setMinValue(1)
				.setMaxValue(10000)
				.setCacheSize(20)
				.setCycle(true);
		sequence.setDialect(dialect);

		final String sql = sqlFactoryRegistry.createSql(sequence,
				SqlType.CREATE).get(0).getSqlText()
				.replaceAll("\\s+", " ");
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
		sequence.getSpecifics().put(
				SpannerCreateSequenceFactory.SKIP_RANGE_MIN, 100L);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(sequence,
						SqlType.CREATE));
	}

	@Test
	void testNextValues() {
		final Sequence sequence = new Sequence("SINGER_ID_SEQUENCE");
		sequence.setDialect(dialect);

		final String sql = sqlFactoryRegistry.createSql(sequence,
				SqlType.SEQUENCE_NEXT_VALUES).get(0).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"GET_NEXT_SEQUENCE_VALUE(SEQUENCE SINGER_ID_SEQUENCE)"),
				sql);
		assertTrue(sql.contains("UNNEST(GENERATE_ARRAY("), sql);
	}
}

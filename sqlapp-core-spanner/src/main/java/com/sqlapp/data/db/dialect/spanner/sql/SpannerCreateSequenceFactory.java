/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * GoogleSQL Cloud Spanner CREATE SEQUENCE.
 */
public class SpannerCreateSequenceFactory
		extends AbstractCreateSequenceFactory<SpannerSqlBuilder> {

	public static final String SKIP_RANGE_MIN = "SKIP_RANGE_MIN";
	public static final String SKIP_RANGE_MAX = "SKIP_RANGE_MAX";

	@Override
	protected void addIfNotExists(final Sequence sequence,
			final SpannerSqlBuilder builder) {
		builder.ifNotExists(getOptions().isCreateIfNotExists());
	}

	@Override
	protected void addDataType(final Sequence sequence,
			final SpannerSqlBuilder builder) {
	}

	@Override
	protected void addStartWith(final Sequence sequence,
			final SpannerSqlBuilder builder) {
		if (sequence.getStartValue().signum() <= 0) {
			throw new IllegalArgumentException(
					"Cloud Spanner sequence start counter must be positive: "
							+ sequence.getName());
		}
		builder.start().space()._add("COUNTER WITH").space()
				._add(sequence.getStartValue());
	}

	@Override
	protected void addIncrementBy(final Sequence sequence,
			final SpannerSqlBuilder builder) {
	}

	@Override
	protected void addMaxValue(final Sequence sequence,
			final SpannerSqlBuilder builder) {
	}

	@Override
	protected void addMinValue(final Sequence sequence,
			final SpannerSqlBuilder builder) {
	}

	@Override
	protected void addCycle(final Sequence sequence,
			final SpannerSqlBuilder builder) {
	}

	@Override
	protected void addCache(final Sequence sequence,
			final SpannerSqlBuilder builder) {
	}

	@Override
	protected void addOptions(final Sequence sequence,
			final SpannerSqlBuilder builder) {
		final Long skipMin = sequence.getSpecifics().get(
				SKIP_RANGE_MIN, Long.class);
		final Long skipMax = sequence.getSpecifics().get(
				SKIP_RANGE_MAX, Long.class);
		if ((skipMin == null) != (skipMax == null)) {
			throw new IllegalArgumentException(
					"Cloud Spanner sequence skip range requires both "
							+ "minimum and maximum values: "
							+ sequence.getName());
		}
		if (skipMin != null && skipMin.longValue() > skipMax.longValue()) {
			throw new IllegalArgumentException(
					"Cloud Spanner sequence skip range minimum must not "
							+ "exceed maximum: " + sequence.getName());
		}
		builder.space()._add("BIT_REVERSED_POSITIVE");
		if (skipMin != null) {
			builder.space()._add("SKIP RANGE").space()._add(skipMin)
					._add(",").space()._add(skipMax);
		}
	}

}

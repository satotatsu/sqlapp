/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.Sequence;

/** Uses the native Phoenix sequence clauses and shared sequence model. */
public class PhoenixCreateSequenceFactory extends AbstractCreateSequenceFactory<PhoenixSqlBuilder> {
	@Override
	protected void addDataType(Sequence sequence, PhoenixSqlBuilder builder) {
		// Phoenix sequences have a fixed BIGINT representation and no AS clause.
	}

	@Override
	protected void addIfNotExists(Sequence sequence, PhoenixSqlBuilder builder) {
		builder.ifNotExists(getOptions().isCreateIfNotExists());
	}

	@Override
	protected void addCreateObject(Sequence sequence, PhoenixSqlBuilder builder) {
		for (var value : new java.math.BigInteger[] { sequence.getStartValue(), sequence.getIncrementBy(),
				sequence.getMinValue(), sequence.getMaxValue() }) {
			if (value != null)
				value.longValueExact();
		}
		if (java.math.BigInteger.ZERO.equals(sequence.getIncrementBy()))
			throw new IllegalArgumentException("Phoenix sequence increment must not be zero: " + sequence.getName());
		if (sequence.getCacheSize() != null && sequence.getCacheSize() < 0)
			throw new IllegalArgumentException("Phoenix sequence cache must not be negative: " + sequence.getName());
		if (sequence.getMinValue() != null && sequence.getMaxValue() != null
				&& sequence.getMinValue().compareTo(sequence.getMaxValue()) > 0)
			throw new IllegalArgumentException("Phoenix sequence minimum exceeds maximum: " + sequence.getName());
		if (sequence.getStartValue() != null && ((sequence.getMinValue() != null
				&& sequence.getStartValue().compareTo(sequence.getMinValue()) < 0)
				|| (sequence.getMaxValue() != null && sequence.getStartValue().compareTo(sequence.getMaxValue()) > 0)))
			throw new IllegalArgumentException("Phoenix sequence start is outside its bounds: " + sequence.getName());
		addCreateSequence(sequence, builder);
		addIfNotExists(sequence, builder);
		builder.name(sequence, getOptions().isDecorateSchemaName());
		if (sequence.getStartValue() != null)
			addStartWith(sequence, builder);
		if (sequence.getIncrementBy() != null)
			addIncrementBy(sequence, builder);
		// Phoenix's grammar requires MINVALUE before MAXVALUE.
		if (sequence.getMinValue() != null)
			addMinValue(sequence, builder);
		if (sequence.getMaxValue() != null)
			addMaxValue(sequence, builder);
		addCycle(sequence, builder);
		if (sequence.getCacheSize() != null)
			addCache(sequence, builder);
	}
}

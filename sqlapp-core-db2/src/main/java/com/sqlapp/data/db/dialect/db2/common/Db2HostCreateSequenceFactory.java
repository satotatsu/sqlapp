/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import com.sqlapp.data.db.dialect.db2.sql.Db2CreateSequenceFactory;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Sequence;

/**
 * Reuses Db2 sequence options with non-replacing CREATE and explicit type
 * validation.
 */
public class Db2HostCreateSequenceFactory extends Db2CreateSequenceFactory {
	@Override
	protected void addCreateSequence(Sequence sequence, Db2SqlBuilder builder) {
		if (sequence.getDataType() == null)
			throw new IllegalArgumentException(
					"Sequence requires an explicitly resolved type; z/OS internal DATATYPEID is not a portable type: "
							+ sequence.getName());
		if (!java.util.Set.of(DataType.SMALLINT, DataType.INT, DataType.BIGINT, DataType.DECIMAL, DataType.NUMERIC)
				.contains(sequence.getDataType()))
			throw new IllegalArgumentException("Unsupported host sequence type: " + sequence.getDataType());
		if (sequence.getIncrementBy() == null || sequence.getIncrementBy().signum() == 0
				|| sequence.getStartValue() == null || sequence.getMinValue() == null || sequence.getMaxValue() == null
				|| sequence.getMinValue().compareTo(sequence.getMaxValue()) > 0
				|| sequence.getStartValue().compareTo(sequence.getMinValue()) < 0
				|| sequence.getStartValue().compareTo(sequence.getMaxValue()) > 0)
			throw new IllegalArgumentException("Invalid or incomplete host sequence bounds: " + sequence.getName());
		if ((sequence.getDataType() == DataType.DECIMAL || sequence.getDataType() == DataType.NUMERIC)
				&& (sequence.getPrecision() == null || sequence.getPrecision() < 1
						|| sequence.getPrecision() > ((AbstractDb2HostDialect) getDialect()).maxDecimalPrecision()))
			throw new IllegalArgumentException("Invalid host sequence precision: " + sequence.getName());
		java.math.BigInteger limit = switch (sequence.getDataType()) {
		case SMALLINT -> java.math.BigInteger.ONE.shiftLeft(15);
		case INT -> java.math.BigInteger.ONE.shiftLeft(31);
		case BIGINT -> java.math.BigInteger.ONE.shiftLeft(63);
		default -> java.math.BigInteger.TEN.pow(sequence.getPrecision());
		};
		java.math.BigInteger minimum = limit.negate();
		if (sequence.getDataType() == DataType.DECIMAL || sequence.getDataType() == DataType.NUMERIC)
			minimum = minimum.add(java.math.BigInteger.ONE);
		if (sequence.getMinValue().compareTo(minimum) < 0
				|| sequence.getMaxValue().compareTo(limit.subtract(java.math.BigInteger.ONE)) > 0)
			throw new IllegalArgumentException("Sequence bounds exceed its type: " + sequence.getName());
		if (sequence.getCacheSize() != null && (sequence.getCacheSize() < 0 || sequence.getCacheSize() == 1))
			throw new IllegalArgumentException("Host sequence cache must be zero or at least two");
		builder.create().sequence();
	}

	@Override
	protected void addOptions(Sequence sequence, Db2SqlBuilder builder) {
		super.addOptions(sequence, builder);
		if (!sequence.isOrder())
			builder.noOrder();
		if (!sequence.isCycle())
			builder.no().cycle();
	}
}
